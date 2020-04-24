/*
 * Copyright 2004-2020 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.mode;

import java.util.HashMap;
import java.util.StringJoiner;

import org.h2.api.ErrorCode;
import org.h2.command.Parser;
import org.h2.engine.Constants;
import org.h2.engine.Session;
import org.h2.engine.User;
import org.h2.expression.Expression;
import org.h2.expression.ValueExpression;
import org.h2.expression.function.Function;
import org.h2.expression.function.FunctionInfo;
import org.h2.index.Index;
import org.h2.message.DbException;
import org.h2.schema.SchemaObject;
import org.h2.server.pg.PgServer;
import org.h2.table.Column;
import org.h2.table.Table;
import org.h2.util.StringUtils;
import org.h2.value.TypeInfo;
import org.h2.value.Value;
import org.h2.value.ValueArray;
import org.h2.value.ValueBigint;
import org.h2.value.ValueBoolean;
import org.h2.value.ValueInteger;
import org.h2.value.ValueNull;
import org.h2.value.ValueVarchar;

/**
 * Functions for {@link org.h2.engine.Mode.ModeEnum#PostgreSQL} compatibility
 * mode.
 */
public final class FunctionsPostgreSQL extends FunctionsBase {

    private static final int CURRTID2 = 3001;

    private static final int FORMAT_TYPE = CURRTID2 + 1;

    private static final int HAS_DATABASE_PRIVILEGE = FORMAT_TYPE + 1;

    private static final int HAS_SCHEMA_PRIVILEGE = HAS_DATABASE_PRIVILEGE + 1;

    private static final int HAS_TABLE_PRIVILEGE = HAS_SCHEMA_PRIVILEGE + 1;

    private static final int VERSION = HAS_TABLE_PRIVILEGE + 1;

    private static final int OBJ_DESCRIPTION = VERSION + 1;

    private static final int PG_ENCODING_TO_CHAR = OBJ_DESCRIPTION + 1;

    private static final int PG_GET_EXPR = PG_ENCODING_TO_CHAR + 1;

    private static final int PG_GET_INDEXDEF = PG_GET_EXPR + 1;

    private static final int PG_GET_USERBYID = PG_GET_INDEXDEF + 1;

    private static final int PG_POSTMASTER_START_TIME = PG_GET_USERBYID + 1;

    private static final int PG_RELATION_SIZE = PG_POSTMASTER_START_TIME + 1;

    private static final int PG_TABLE_IS_VISIBLE = PG_RELATION_SIZE + 1;

    private static final int SET_CONFIG = PG_TABLE_IS_VISIBLE + 1;

    private static final int ARRAY_TO_STRING = SET_CONFIG + 1;

    private static final HashMap<String, FunctionInfo> FUNCTIONS = new HashMap<>();

    static {
        copyFunction(FUNCTIONS, "CURRENT_CATALOG", "CURRENT_DATABASE");
        copyFunction(FUNCTIONS, "IDENTITY", "LASTVAL");
        FUNCTIONS.put("CURRTID2", new FunctionInfo("CURRTID2", CURRTID2, 2, Value.INTEGER, true, false, true, false));
        FUNCTIONS.put("FORMAT_TYPE",
                new FunctionInfo("FORMAT_TYPE", FORMAT_TYPE, 2, Value.VARCHAR, false, true, true, false));
        FUNCTIONS.put("HAS_DATABASE_PRIVILEGE", new FunctionInfo("HAS_DATABASE_PRIVILEGE", HAS_DATABASE_PRIVILEGE,
                VAR_ARGS, Value.BOOLEAN, true, false, true, false));
        FUNCTIONS.put("HAS_SCHEMA_PRIVILEGE", new FunctionInfo("HAS_SCHEMA_PRIVILEGE", HAS_SCHEMA_PRIVILEGE,
                VAR_ARGS, Value.BOOLEAN, true, false, true, false));
        FUNCTIONS.put("HAS_TABLE_PRIVILEGE", new FunctionInfo("HAS_TABLE_PRIVILEGE", HAS_TABLE_PRIVILEGE,
                VAR_ARGS, Value.BOOLEAN, true, false, true, false));
        FUNCTIONS.put("VERSION", new FunctionInfo("VERSION", VERSION, 0, Value.VARCHAR, true, false, true, false));
        FUNCTIONS.put("OBJ_DESCRIPTION", new FunctionInfo("OBJ_DESCRIPTION", OBJ_DESCRIPTION, VAR_ARGS, Value.VARCHAR,
                true, false, true, false));
        FUNCTIONS.put("PG_ENCODING_TO_CHAR", new FunctionInfo("PG_ENCODING_TO_CHAR", PG_ENCODING_TO_CHAR, 1,
                Value.VARCHAR, true, true, true, false));
        FUNCTIONS.put("PG_GET_EXPR",
                new FunctionInfo("PG_GET_EXPR", PG_GET_EXPR, 2, Value.VARCHAR, true, true, true, false));
        FUNCTIONS.put("PG_GET_INDEXDEF", //
                new FunctionInfo("PG_GET_INDEXDEF", PG_GET_INDEXDEF, VAR_ARGS, Value.VARCHAR, //
                        true, false, true, false));
        FUNCTIONS.put("PG_GET_USERBYID",
                new FunctionInfo("PG_GET_USERBYID", PG_GET_USERBYID, 1, Value.VARCHAR, true, false, true, false));
        FUNCTIONS.put("PG_POSTMASTER_START_TIME", new FunctionInfo("PG_POSTMASTER_START_TIME", //
                PG_POSTMASTER_START_TIME, 0, Value.TIMESTAMP_TZ, true, false, true, false));
        FUNCTIONS.put("PG_RELATION_SIZE", new FunctionInfo("PG_RELATION_SIZE", //
                PG_RELATION_SIZE, VAR_ARGS, Value.BIGINT, true, false, true, false));
        FUNCTIONS.put("PG_TABLE_IS_VISIBLE", new FunctionInfo("PG_TABLE_IS_VISIBLE", //
                PG_TABLE_IS_VISIBLE, 1, Value.BOOLEAN, true, false, true, false));
        FUNCTIONS.put("SET_CONFIG", new FunctionInfo("SET_CONFIG", //
                SET_CONFIG, 3, Value.VARCHAR, true, false, true, false));
        FUNCTIONS.put("ARRAY_TO_STRING", new FunctionInfo("ARRAY_TO_STRING", //
                ARRAY_TO_STRING, VAR_ARGS, Value.VARCHAR, false, true, true, false));
    }

    /**
     * Returns mode-specific function for a given name, or {@code null}.
     *
     * @param upperName
     *            the upper-case name of a function
     * @return the function with specified name or {@code null}
     */
    public static Function getFunction(String upperName) {
        FunctionInfo info = FUNCTIONS.get(upperName);
        if (info != null) {
            if (info.type > 3000) {
                return new FunctionsPostgreSQL(info);
            }
            return new Function(info);
        }
        return null;
    }

    private FunctionsPostgreSQL(FunctionInfo info) {
        super(info);
    }

    @Override
    protected void checkParameterCount(int len) {
        int min, max;
        switch (info.type) {
        case HAS_DATABASE_PRIVILEGE:
        case HAS_SCHEMA_PRIVILEGE:
        case HAS_TABLE_PRIVILEGE:
            min = 2;
            max = 3;
            break;
        case OBJ_DESCRIPTION:
        case PG_RELATION_SIZE:
            min = 1;
            max = 2;
            break;
        case PG_GET_INDEXDEF:
            if (len != 1 && len != 3) {
                throw DbException.get(ErrorCode.INVALID_PARAMETER_COUNT_2, info.name, "1, 3");
            }
            return;
        case ARRAY_TO_STRING:
            min = 2;
            max = 3;
            break;
        default:
            throw DbException.throwInternalError("type=" + info.type);
        }
        if (len < min || len > max) {
            throw DbException.get(ErrorCode.INVALID_PARAMETER_COUNT_2, info.name, min + ".." + max);
        }
    }

    @Override
    public Expression optimize(Session session) {
        boolean allConst = optimizeArguments(session);
        type = TypeInfo.getTypeInfo(info.returnDataType);
        if (allConst) {
            return ValueExpression.get(getValue(session));
        }
        return this;
    }

    @Override
    protected Value getValueWithArgs(Session session, Expression[] args) {
        Value[] values = getArgumentsValues(session, args);
        if (values == null) {
            return ValueNull.INSTANCE;
        }
        Value v0 = getNullOrValue(session, args, values, 0);
        Value v1 = getNullOrValue(session, args, values, 1);
        Value v2 = getNullOrValue(session, args, values, 2);
        Value result;
        switch (info.type) {
        case CURRTID2:
            // Not implemented
            result = ValueInteger.get(1);
            break;
        case FORMAT_TYPE:
            result = v0 != ValueNull.INSTANCE ? ValueVarchar.get(PgServer.formatType(v0.getInt())) //
                    : ValueNull.INSTANCE;
            break;
        case HAS_DATABASE_PRIVILEGE:
        case HAS_SCHEMA_PRIVILEGE:
        case HAS_TABLE_PRIVILEGE:
        case PG_TABLE_IS_VISIBLE:
            // Not implemented
            result = ValueBoolean.TRUE;
            break;
        case VERSION:
            result = ValueVarchar
                    .get("PostgreSQL " + Constants.PG_VERSION + " server protocol using H2 " + Constants.FULL_VERSION);
            break;
        case OBJ_DESCRIPTION:
            // Not implemented
            result = ValueNull.INSTANCE;
            break;
        case PG_ENCODING_TO_CHAR:
            result = ValueVarchar.get(encodingToChar(v0.getInt()));
            break;
        case PG_GET_EXPR:
            // Not implemented
            result = ValueNull.INSTANCE;
            break;
        case PG_GET_INDEXDEF:
            result = getIndexdef(session, v0.getInt(), v1, v2);
            break;
        case PG_GET_USERBYID:
            result = ValueVarchar.get(getUserbyid(session, v0.getInt()));
            break;
        case PG_POSTMASTER_START_TIME:
            result = session.getDatabase().getSystemSession().getSessionStart();
            break;
        case PG_RELATION_SIZE:
            // Optional second argument is ignored
            result = relationSize(session, v0);
            break;
        case SET_CONFIG:
            // Not implemented
            result = v1.convertTo(Value.VARCHAR);
            break;
        case ARRAY_TO_STRING:
            if (v0 == ValueNull.INSTANCE || v1 == ValueNull.INSTANCE) {
                result = ValueNull.INSTANCE;
                break;
            }
            StringJoiner joiner = new StringJoiner(v1.getString());
            if (v0.getValueType() != Value.ARRAY) {
                throw DbException.getInvalidValueException("ARRAY_TO_STRING array", v0);
            }
            String nullString = null;
            if (v2 != null) {
                nullString = v2.getString();
            }
            for (Value v : ((ValueArray) v0).getList()) {
                if (v != ValueNull.INSTANCE) {
                    joiner.add(v.getString());
                } else if (nullString != null) {
                    joiner.add(nullString);
                }
            }
            result = ValueVarchar.get(joiner.toString());
            break;
        default:
            throw DbException.throwInternalError("type=" + info.type);
        }
        return result;
    }

    private static String encodingToChar(int code) {
        switch (code) {
        case 0:
            return "SQL_ASCII";
        case 6:
            return "UTF8";
        case 8:
            return "LATIN1";
        default:
            // This function returns empty string for unknown encodings
            return code < 40 ? "UTF8" : "";
        }
    }

    private static Value getIndexdef(Session session, int indexId, Value ordinalPosition, Value pretty) {
        for (SchemaObject obj : session.getDatabase().getAllSchemaObjects(SchemaObject.INDEX)) {
            if (obj.getId() == indexId) {
                Index index = (Index) obj;
                if (!index.getTable().isHidden()) {
                    int ordinal;
                    if (ordinalPosition == null || (ordinal = ordinalPosition.getInt()) == 0) {
                        return ValueVarchar.get(index.getCreateSQL());
                    }
                    Column[] columns;
                    if (ordinal >= 1 && ordinal <= (columns = index.getColumns()).length) {
                        return ValueVarchar.get(columns[ordinal - 1].getName());
                    }
                }
                break;
            }
        }
        return ValueNull.INSTANCE;
    }

    private static String getUserbyid(Session session, int uid) {
        User u = session.getUser();
        String name;
        search: {
            if (u.getId() == uid) {
                name = u.getName();
                break search;
            } else {
                if (u.isAdmin()) {
                    for (User user : session.getDatabase().getAllUsers()) {
                        if (user.getId() == uid) {
                            name = user.getName();
                            break search;
                        }
                    }
                }
            }
            return "unknown (OID=" + uid + ')';
        }
        if (session.getDatabase().getSettings().databaseToLower) {
            name = StringUtils.toLowerEnglish(name);
        }
        return name;
    }

    private static Value relationSize(Session session, Value tableOidOrName) {
        Table t;
        if (tableOidOrName.getValueType() == Value.INTEGER) {
            int tid = tableOidOrName.getInt();
            for (Table table : session.getDatabase().getAllTablesAndViews(false)) {
                if (tid == table.getId()) {
                    t = table;
                    break;
                }
            }
            return ValueNull.INSTANCE;
        } else {
            t = new Parser(session).parseTableName(tableOidOrName.getString());
        }
        return ValueBigint.get(t.getDiskSpaceUsed());
    }

}
