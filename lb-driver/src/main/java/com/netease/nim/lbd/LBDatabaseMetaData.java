package com.netease.nim.lbd;

import java.sql.*;


public class LBDatabaseMetaData implements DatabaseMetaData {
	
	private final LBDriverUrl lbDriverUrl;
	
    public LBDatabaseMetaData(LBDriverUrl lbDriverUrl) {
		super();
		this.lbDriverUrl = lbDriverUrl;
	}

	@Override
    public boolean allProceduresAreCallable() throws SQLException {
        return false;
    }

    @Override
    public boolean allTablesAreSelectable() throws SQLException {
        return false;
    }

    @Override
    public boolean autoCommitFailureClosesAllResultSets() throws SQLException {
        return false;
    }

    @Override
    public boolean dataDefinitionCausesTransactionCommit() throws SQLException {
        return true;
    }

    @Override
    public boolean dataDefinitionIgnoredInTransactions() throws SQLException {
        return false;
    }

    @Override
    public boolean deletesAreDetected(int type) throws SQLException {
        return false;
    }

    @Override
    public boolean doesMaxRowSizeIncludeBlobs() throws SQLException {
        return true;
    }

    @Override
    public boolean generatedKeyAlwaysReturned() throws SQLException {
        return true;
    }

    @Override
    public ResultSet getAttributes(String catalog, String schemaPattern, String typeNamePattern, String attributeNamePattern) throws SQLException {
    	unsupported();
    	return null;
    }

    @Override
    public ResultSet getBestRowIdentifier(String catalog, String schema, String table, int scope, boolean nullable) throws SQLException {
    	unsupported();
    	return null;
    }

    @Override
    public ResultSet getCatalogs() throws SQLException {
    	unsupported();
    	return null;
    }

    @Override
    public String getCatalogSeparator() throws SQLException {
    	unsupported();
    	return null;
    }

    @Override
    public String getCatalogTerm() throws SQLException {
    	unsupported();
    	return null;
    }

    @Override
    public ResultSet getColumnPrivileges(String catalog, String schema, String table, String columnNamePattern) throws SQLException {
    	unsupported();
    	return null;
    }

    @Override
    public ResultSet getColumns(String catalog, String schemaPattern, String tableNamePattern, String columnNamePattern) throws SQLException {
    	unsupported();
    	return null;
    }

    @Override
    public Connection getConnection() throws SQLException {
    	unsupported();
    	return null;
    }

    @Override
    public ResultSet getCrossReference(String parentCatalog, String parentSchema, String parentTable, String foreignCatalog, String foreignSchema, String foreignTable) throws SQLException {
    	unsupported();
    	return null;
    }

    @Override
    public int getDatabaseMajorVersion() throws SQLException {
        return 0;
    }

    @Override
    public int getDatabaseMinorVersion() throws SQLException {
        return 0;
    }

    @Override
    public String getDatabaseProductName() throws SQLException {
        return "MySQL";
    }

    @Override
    public String getDatabaseProductVersion() throws SQLException {
        return "5.7.20";
    }

    @Override
    public int getDefaultTransactionIsolation() throws SQLException {
    	return Connection.TRANSACTION_READ_COMMITTED;
    }

    @Override
    public int getDriverMajorVersion() {
        return 2;
    }

    @Override
    public int getDriverMinorVersion() {
        return 0;
    }

    @Override
    public String getDriverName() throws SQLException {
        return "MySQL-AB JDBC Driver";
    }

    @Override
    public String getDriverVersion() throws SQLException {
        unsupported();
        return null;
    }

    @Override
    public ResultSet getExportedKeys(String catalog, String schema, String table) throws SQLException {
    	unsupported();
        return null;
    }

    @Override
    public String getExtraNameCharacters() throws SQLException {
    	unsupported();
        return null;
    }

    @Override
    public String getIdentifierQuoteString() throws SQLException {
    	unsupported();
        return null;
    }

    @Override
    public ResultSet getImportedKeys(String catalog, String schema, String table) throws SQLException {
    	unsupported();
        return null;
    }

    @Override
    public ResultSet getIndexInfo(String catalog, String schema, String table, boolean unique, boolean approximate) throws SQLException {
    	unsupported();
        return null;
    }

    @Override
    public int getJDBCMajorVersion() throws SQLException {
    	return 4;
    }

    @Override
    public int getJDBCMinorVersion() throws SQLException {
    	return 0;
    }

    @Override
    public int getMaxBinaryLiteralLength() throws SQLException {
    	return 16777208;
    }

    @Override
    public int getMaxCatalogNameLength() throws SQLException {
        return 32;
    }

    @Override
    public int getMaxCharLiteralLength() throws SQLException {
    	return 16777208;
    }

    @Override
    public int getMaxColumnNameLength() throws SQLException {
    	return 64;
    }

    @Override
    public int getMaxColumnsInGroupBy() throws SQLException {
    	return 64;
    }

    @Override
    public int getMaxColumnsInIndex() throws SQLException {
        return 16;
    }

    @Override
    public int getMaxColumnsInOrderBy() throws SQLException {
        return 64;
    }

    @Override
    public int getMaxColumnsInSelect() throws SQLException {
        return 256;
    }

    @Override
    public int getMaxColumnsInTable() throws SQLException {
        return 512;
    }

    @Override
    public int getMaxConnections() throws SQLException {
        return 0;
    }

    @Override
    public int getMaxCursorNameLength() throws SQLException {
        return 64;
    }

    @Override
    public int getMaxIndexLength() throws SQLException {
        return 256;
    }

    @Override
    public int getMaxProcedureNameLength() throws SQLException {
        return 0;
    }

    @Override
    public int getMaxRowSize() throws SQLException {
    	return Integer.MAX_VALUE - 8;
    }

    @Override
    public int getMaxSchemaNameLength() throws SQLException {
        return 0;
    }

    @Override
    public int getMaxStatementLength() throws SQLException {
    	return 65531;
    }

    @Override
    public int getMaxStatements() throws SQLException {
        return 0;
    }

    @Override
    public int getMaxTableNameLength() throws SQLException {
        return 64;
    }

    @Override
    public int getMaxTablesInSelect() throws SQLException {
        return 256;
    }

    @Override
    public int getMaxUserNameLength() throws SQLException {
        return 16;
    }

    @Override
    public String getNumericFunctions() throws SQLException {
    	return "ABS,ACOS,ASIN,ATAN,ATAN2,BIT_COUNT,CEILING,COS,COT,DEGREES,EXP,FLOOR,LOG,LOG10,MAX,MIN,MOD,PI,POW,"
                + "POWER,RADIANS,RAND,ROUND,SIN,SQRT,TAN,TRUNCATE";
    }

    @Override
    public ResultSet getPrimaryKeys(String catalog, String schema, String table) throws SQLException {
        unsupported();
        return null;
    }

    @Override
    public ResultSet getProcedureColumns(String catalog, String schemaPattern, String procedureNamePattern, String columnNamePattern) throws SQLException {
    	unsupported();
        return null;
    }

    @Override
    public ResultSet getProcedures(String catalog, String schemaPattern, String procedureNamePattern) throws SQLException {
    	unsupported();
        return null;
    }

    @Override
    public String getProcedureTerm() throws SQLException {
    	return "PROCEDURE";
    }

    @Override
    public ResultSet getPseudoColumns(String catalog, String schemaPattern, String tableNamePattern, String columnNamePattern) throws SQLException {
    	unsupported();
    	return null;
    }

    @Override
    public int getResultSetHoldability() throws SQLException {
    	return ResultSet.HOLD_CURSORS_OVER_COMMIT;
    }

    @Override
    public ResultSet getSchemas(String catalog, String schemaPattern) throws SQLException {
    	unsupported();
    	return null;
    }

    @Override
    public String getSchemaTerm() throws SQLException {
    	return "";
    }

    @Override
    public String getSearchStringEscape() throws SQLException {
    	return "\\";
    }

    @Override
    public String getSQLKeywords() throws SQLException {
    	unsupported();
    	return null;
    }

    @Override
    public int getSQLStateType() throws SQLException {
    	return DatabaseMetaData.sqlStateSQL99;
    }

    @Override
    public String getStringFunctions() throws SQLException {
    	return "ASCII,BIN,BIT_LENGTH,CHAR,CHARACTER_LENGTH,CHAR_LENGTH,CONCAT,CONCAT_WS,CONV,ELT,EXPORT_SET,FIELD,FIND_IN_SET,HEX,INSERT,"
                + "INSTR,LCASE,LEFT,LENGTH,LOAD_FILE,LOCATE,LOCATE,LOWER,LPAD,LTRIM,MAKE_SET,MATCH,MID,OCT,OCTET_LENGTH,ORD,POSITION,"
                + "QUOTE,REPEAT,REPLACE,REVERSE,RIGHT,RPAD,RTRIM,SOUNDEX,SPACE,STRCMP,SUBSTRING,SUBSTRING,SUBSTRING,SUBSTRING,"
                + "SUBSTRING_INDEX,TRIM,UCASE,UPPER";
    }

    @Override
    public ResultSet getSuperTables(String catalog, String schemaPattern, String tableNamePattern) throws SQLException {
    	unsupported();
    	return null;
    }

    @Override
    public ResultSet getSuperTypes(String catalog, String schemaPattern, String typeNamePattern) throws SQLException {
    	unsupported();
    	return null;
    }

    @Override
    public String getSystemFunctions() throws SQLException {
    	return "DATABASE,USER,SYSTEM_USER,SESSION_USER,PASSWORD,ENCRYPT,LAST_INSERT_ID,VERSION";
    }

    @Override
    public ResultSet getTablePrivileges(String catalog, String schemaPattern, String tableNamePattern) throws SQLException {
    	unsupported();
    	return null;
    }

    @Override
    public ResultSet getTables(String catalog, String schemaPattern, String tableNamePattern, String[] types) throws SQLException {
    	unsupported();
    	return null;
    }

    @Override
    public ResultSet getTableTypes() throws SQLException {
    	unsupported();
    	return null;
    }

    @Override
    public String getTimeDateFunctions() throws SQLException {
    	return "DAYOFWEEK,WEEKDAY,DAYOFMONTH,DAYOFYEAR,MONTH,DAYNAME,MONTHNAME,QUARTER,WEEK,YEAR,HOUR,MINUTE,SECOND,PERIOD_ADD,"
                + "PERIOD_DIFF,TO_DAYS,FROM_DAYS,DATE_FORMAT,TIME_FORMAT,CURDATE,CURRENT_DATE,CURTIME,CURRENT_TIME,NOW,SYSDATE,"
                + "CURRENT_TIMESTAMP,UNIX_TIMESTAMP,FROM_UNIXTIME,SEC_TO_TIME,TIME_TO_SEC";
    }

    @Override
    public ResultSet getTypeInfo() throws SQLException {
    	unsupported();
    	return null;
    }

    @Override
    public ResultSet getUDTs(String catalog, String schemaPattern, String typeNamePattern, int[] types) throws SQLException {
    	unsupported();
    	return null;
    }

    @Override
    public String getURL() throws SQLException {
        return lbDriverUrl.getUrl();
    }

    @Override
    public String getUserName() throws SQLException {
    	unsupported();
    	return null;
    }

    @Override
    public ResultSet getVersionColumns(String catalog, String schema, String table) throws SQLException {
    	unsupported();
    	return null;
    }

    @Override
    public boolean insertsAreDetected(int type) throws SQLException {
        return false;
    }

    @Override
    public boolean isCatalogAtStart() throws SQLException {
        return true;
    }

    @Override
    public boolean isReadOnly() throws SQLException {
        return false;
    }

    @Override
    public boolean locatorsUpdateCopy() throws SQLException {
        return false;
    }

    @Override
    public boolean nullPlusNonNullIsNull() throws SQLException {
        return true;
    }

    @Override
    public boolean nullsAreSortedAtEnd() throws SQLException {
        return false;
    }

    @Override
    public boolean nullsAreSortedAtStart() throws SQLException {
    	return false;
    }

    @Override
    public boolean nullsAreSortedHigh() throws SQLException {
    	return false;
    }

    @Override
    public boolean nullsAreSortedLow() throws SQLException {
        return true;
    }

    @Override
    public boolean othersDeletesAreVisible(int type) throws SQLException {
    	return false;
    }

    @Override
    public boolean othersInsertsAreVisible(int type) throws SQLException {
    	return false;
    }

    @Override
    public boolean othersUpdatesAreVisible(int type) throws SQLException {
    	return false;
    }

    @Override
    public boolean ownDeletesAreVisible(int type) throws SQLException {
    	return false;
    }

    @Override
    public boolean ownInsertsAreVisible(int type) throws SQLException {
    	return false;
    }

    @Override
    public boolean ownUpdatesAreVisible(int type) throws SQLException {
    	return false;
    }

    @Override
    public boolean storesLowerCaseIdentifiers() throws SQLException {
    	unsupported();
    	return false;
    }

    @Override
    public boolean storesLowerCaseQuotedIdentifiers() throws SQLException {
    	unsupported();
    	return false;
    }

    @Override
    public boolean storesMixedCaseIdentifiers() throws SQLException {
    	unsupported();
    	return false;
    }

    @Override
    public boolean storesMixedCaseQuotedIdentifiers() throws SQLException {
    	unsupported();
    	return false;
    }

    @Override
    public boolean storesUpperCaseIdentifiers() throws SQLException {
    	return false;
    }

    @Override
    public boolean storesUpperCaseQuotedIdentifiers() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsAlterTableWithAddColumn() throws SQLException {
    	return true;
    }

    @Override
    public boolean supportsAlterTableWithDropColumn() throws SQLException {
    	return true;
    }

    @Override
    public boolean supportsANSI92EntryLevelSQL() throws SQLException {
    	return true;
    }

    @Override
    public boolean supportsANSI92FullSQL() throws SQLException {
    	return false;
    }

    @Override
    public boolean supportsANSI92IntermediateSQL() throws SQLException {
    	return false;
    }

    @Override
    public boolean supportsBatchUpdates() throws SQLException {
    	return true;
    }

    @Override
    public boolean supportsCatalogsInDataManipulation() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsCatalogsInIndexDefinitions() throws SQLException {
    	return true;
    }

    @Override
    public boolean supportsCatalogsInPrivilegeDefinitions() throws SQLException {
    	return true;
    }

    @Override
    public boolean supportsCatalogsInProcedureCalls() throws SQLException {
    	return true;
    }

    @Override
    public boolean supportsCatalogsInTableDefinitions() throws SQLException {
    	return true;
    }

    @Override
    public boolean supportsColumnAliasing() throws SQLException {
    	return true;
    }

    @Override
    public boolean supportsConvert() throws SQLException {
    	return false;
    }

    @Override
    public boolean supportsConvert(int fromType, int toType) throws SQLException {
    	switch (fromType) {
        /*
         * The char/binary types can be converted to pretty much anything.
         */
            case Types.CHAR:
            case Types.VARCHAR:
            case Types.LONGVARCHAR:
            case Types.BINARY:
            case Types.VARBINARY:
            case Types.LONGVARBINARY:

                switch (toType) {
                    case Types.DECIMAL:
                    case Types.NUMERIC:
                    case Types.REAL:
                    case Types.TINYINT:
                    case Types.SMALLINT:
                    case Types.INTEGER:
                    case Types.BIGINT:
                    case Types.FLOAT:
                    case Types.DOUBLE:
                    case Types.CHAR:
                    case Types.VARCHAR:
                    case Types.LONGVARCHAR:
                    case Types.BINARY:
                    case Types.VARBINARY:
                    case Types.LONGVARBINARY:
                    case Types.OTHER:
                    case Types.DATE:
                    case Types.TIME:
                    case Types.TIMESTAMP:
                        return true;

                    default:
                        return false;
                }

                /*
                 * We don't handle the BIT type yet.
                 */
            case Types.BIT:
                return false;

                /*
                 * The numeric types. Basically they can convert among themselves, and with char/binary types.
                 */
            case Types.DECIMAL:
            case Types.NUMERIC:
            case Types.REAL:
            case Types.TINYINT:
            case Types.SMALLINT:
            case Types.INTEGER:
            case Types.BIGINT:
            case Types.FLOAT:
            case Types.DOUBLE:

                switch (toType) {
                    case Types.DECIMAL:
                    case Types.NUMERIC:
                    case Types.REAL:
                    case Types.TINYINT:
                    case Types.SMALLINT:
                    case Types.INTEGER:
                    case Types.BIGINT:
                    case Types.FLOAT:
                    case Types.DOUBLE:
                    case Types.CHAR:
                    case Types.VARCHAR:
                    case Types.LONGVARCHAR:
                    case Types.BINARY:
                    case Types.VARBINARY:
                    case Types.LONGVARBINARY:
                        return true;

                    default:
                        return false;
                }

                /* MySQL doesn't support a NULL type. */
            case Types.NULL:
                return false;

                /*
                 * With this driver, this will always be a serialized object, so the char/binary types will work.
                 */
            case Types.OTHER:

                switch (toType) {
                    case Types.CHAR:
                    case Types.VARCHAR:
                    case Types.LONGVARCHAR:
                    case Types.BINARY:
                    case Types.VARBINARY:
                    case Types.LONGVARBINARY:
                        return true;

                    default:
                        return false;
                }

                /* Dates can be converted to char/binary types. */
            case Types.DATE:

                switch (toType) {
                    case Types.CHAR:
                    case Types.VARCHAR:
                    case Types.LONGVARCHAR:
                    case Types.BINARY:
                    case Types.VARBINARY:
                    case Types.LONGVARBINARY:
                        return true;

                    default:
                        return false;
                }

                /* Time can be converted to char/binary types */
            case Types.TIME:

                switch (toType) {
                    case Types.CHAR:
                    case Types.VARCHAR:
                    case Types.LONGVARCHAR:
                    case Types.BINARY:
                    case Types.VARBINARY:
                    case Types.LONGVARBINARY:
                        return true;

                    default:
                        return false;
                }

                /*
                 * Timestamp can be converted to char/binary types and date/time types (with loss of precision).
                 */
            case Types.TIMESTAMP:

                switch (toType) {
                    case Types.CHAR:
                    case Types.VARCHAR:
                    case Types.LONGVARCHAR:
                    case Types.BINARY:
                    case Types.VARBINARY:
                    case Types.LONGVARBINARY:
                    case Types.TIME:
                    case Types.DATE:
                        return true;

                    default:
                        return false;
                }

                /* We shouldn't get here! */
            default:
                return false; // not sure
        }
    }

    @Override
    public boolean supportsCoreSQLGrammar() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsCorrelatedSubqueries() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsDataDefinitionAndDataManipulationTransactions() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsDataManipulationTransactionsOnly() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsDifferentTableCorrelationNames() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsExpressionsInOrderBy() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsExtendedSQLGrammar() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsFullOuterJoins() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsGetGeneratedKeys() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsGroupBy() throws SQLException {
    	return true;
    }

    @Override
    public boolean supportsGroupByBeyondSelect() throws SQLException {
    	return true;
    }

    @Override
    public boolean supportsGroupByUnrelated() throws SQLException {
    	return true;
    }

    @Override
    public boolean supportsIntegrityEnhancementFacility() throws SQLException {
    	return true;
    }

    @Override
    public boolean supportsLikeEscapeClause() throws SQLException {
    	return true;
    }

    @Override
    public boolean supportsLimitedOuterJoins() throws SQLException {
    	return true;
    }

    @Override
    public boolean supportsMinimumSQLGrammar() throws SQLException {
    	return true;
    }

    @Override
    public boolean supportsMixedCaseIdentifiers() throws SQLException {
    	return true;
    }

    @Override
    public boolean supportsMixedCaseQuotedIdentifiers() throws SQLException {
    	return true;
    }

    @Override
    public boolean supportsMultipleOpenResults() throws SQLException {
    	return true;
    }

    @Override
    public boolean supportsMultipleResultSets() throws SQLException {
    	return true;
    }

    @Override
    public boolean supportsMultipleTransactions() throws SQLException {
    	return false;
    }

    @Override
    public boolean supportsNamedParameters() throws SQLException {
    	return true;
    }

    @Override
    public boolean supportsNonNullableColumns() throws SQLException {
    	return false;
    }

    @Override
    public boolean supportsOpenCursorsAcrossCommit() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsOpenCursorsAcrossRollback() throws SQLException {
    	return false;
    }

    @Override
    public boolean supportsOpenStatementsAcrossCommit() throws SQLException {
    	return false;
    }

    @Override
    public boolean supportsOpenStatementsAcrossRollback() throws SQLException {
    	return false;
    }

    @Override
    public boolean supportsOrderByUnrelated() throws SQLException {
    	return false;
    }

    @Override
    public boolean supportsOuterJoins() throws SQLException {
    	return true;
    }

    @Override
    public boolean supportsPositionedDelete() throws SQLException {
    	return false;
    }

    @Override
    public boolean supportsPositionedUpdate() throws SQLException {
    	return false;
    }

    @Override
    public boolean supportsResultSetConcurrency(int type, int concurrency) throws SQLException {
        unsupported();
        return false;
    }

    @Override
    public boolean supportsResultSetHoldability(int holdability) throws SQLException {
    	return (holdability == ResultSet.HOLD_CURSORS_OVER_COMMIT);
    }

    @Override
    public boolean supportsResultSetType(int type) throws SQLException {
    	return (type == ResultSet.TYPE_SCROLL_INSENSITIVE);
    }

    @Override
    public boolean supportsSavepoints() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsSchemasInDataManipulation() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsSchemasInIndexDefinitions() throws SQLException {
    	return false;
    }

    @Override
    public boolean supportsSchemasInPrivilegeDefinitions() throws SQLException {
    	return false;
    }

    @Override
    public boolean supportsSchemasInProcedureCalls() throws SQLException {
    	return false;
    }

    @Override
    public boolean supportsSchemasInTableDefinitions() throws SQLException {
    	return false;
    }

    @Override
    public boolean supportsSelectForUpdate() throws SQLException {
    	return true;
    }

    @Override
    public boolean supportsStatementPooling() throws SQLException {
    	return false;
    }

    @Override
    public boolean supportsStoredFunctionsUsingCallSyntax() throws SQLException {
    	return true;
    }

    @Override
    public boolean supportsStoredProcedures() throws SQLException {
    	return true;
    }

    @Override
    public boolean supportsSubqueriesInComparisons() throws SQLException {
    	return true;
    }

    @Override
    public boolean supportsSubqueriesInExists() throws SQLException {
    	return true;
    }

    @Override
    public boolean supportsSubqueriesInIns() throws SQLException {
    	return true;
    }

    @Override
    public boolean supportsSubqueriesInQuantifieds() throws SQLException {
    	return true;
    }

    @Override
    public boolean supportsTableCorrelationNames() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsTransactionIsolationLevel(int level) throws SQLException {
    	switch (level) {
        case Connection.TRANSACTION_READ_COMMITTED:
        case Connection.TRANSACTION_READ_UNCOMMITTED:
        case Connection.TRANSACTION_REPEATABLE_READ:
        case Connection.TRANSACTION_SERIALIZABLE:
            return true;

        default:
            return false;
    }
    }

    @Override
    public boolean supportsTransactions() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsUnion() throws SQLException {
    	return true;
    }

    @Override
    public boolean supportsUnionAll() throws SQLException {
    	return true;
    }

    @Override
    public boolean updatesAreDetected(int type) throws SQLException {
        return false;
    }

    @Override
    public boolean usesLocalFilePerTable() throws SQLException {
    	return false;
    }

    @Override
    public boolean usesLocalFiles() throws SQLException {
    	return false;
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
    	unsupported();
    	return false;
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        unsupported();
        return null;
    }

    @Override
    public RowIdLifetime getRowIdLifetime() throws SQLException {
    	return RowIdLifetime.ROWID_UNSUPPORTED;
    }

    @Override
    public ResultSet getSchemas() throws SQLException {
        unsupported();
        return null;
    }

    @Override
    public ResultSet getClientInfoProperties() throws SQLException {
    	unsupported();
        return null;
    }

    @Override
    public ResultSet getFunctions(String catalog, String schemaPattern, String functionNamePattern) throws SQLException {
    	unsupported();
        return null;
    }

    @Override
    public ResultSet getFunctionColumns(String catalog, String schemaPattern, String functionNamePattern, String columnNamePattern) throws SQLException {
    	unsupported();
        return null;
    }
    
    private void unsupported() throws SQLException {
        if (lbDriverUrl.getUnsupportedMethodBehavior() == UnsupportedMethodBehavior.ThrowException) {
            throw new SQLException("Not supported method!");
        }
	}
}
