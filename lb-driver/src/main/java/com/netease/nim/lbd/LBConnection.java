package com.netease.nim.lbd;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * logic connection
 * <p>
 * Created by caojiajun on 2025/12/3
 */
public class LBConnection implements Connection {

    private final ConnectionManager connectionManager;
    private final List<LBStatement> stmts = new ArrayList<>();

    private RealConnection realConnection;
    private boolean isAutoCommit = true;
    private boolean isTransactionBegun = false;
    private volatile boolean isClosed = false;
    private final LBDriverUrl lbDriverUrl;
    private String schemaName;

    public LBConnection(ConnectionManager connectionManager, LBDriverUrl lbDriverUrl) throws SQLException {
        this.connectionManager = connectionManager;
        this.lbDriverUrl = lbDriverUrl;
        this.schemaName = lbDriverUrl.getSchemaName();
        connectionManager.registerLogicalConnection();
    }

    public RealConnection getRealConnection() {
        return realConnection;
    }

    protected boolean isTransactionBegun() {
        return isTransactionBegun;
    }

    protected void setTransactionBegun(boolean isTransactionBegun) {
        this.isTransactionBegun = isTransactionBegun;
    }

    protected boolean isInTransaction() {
        return !isAutoCommit || isTransactionBegun;
    }

    protected void checkRecyclingConnection() {
        if (realConnection != null && stmts.isEmpty() && !isInTransaction() && !realConnection.isClosed()) {
            connectionManager.returnConnection(realConnection);
            realConnection = null;
        }
    }

    protected SQLException errorWrapper(Throwable error) throws SQLException {
        if (error instanceof SQLException) {
            SQLException sqlEx = (SQLException) error;
            if (realConnection != null) {
                if (lbDriverUrl.getExceptionSorter().isExceptionFatal(sqlEx)) {
                    if (realConnection != null) {
                        realConnection.markUnhealthy();
                    }
                    if (isTransactionBegun()) {
                        setTransactionBegun(false);
                    }
                    closeStatements();
                    if (realConnection != null) {
                        connectionManager.returnConnection(realConnection);
                        realConnection = null;
                    }
                }
            }
            return sqlEx;
        }
        return new SQLException("SQLException thrown by ", error);
    }

    public void checkClosed() throws SQLException {
        if (isClosed) {
            throw new SQLException("No operations allowed after connection closed.", "08003", 0);
        }
    }

    private void initCurrentConnection() throws SQLException {
        if (realConnection != null) {
            return;
        }

        realConnection = connectionManager.getConnection();
        if (realConnection == null) {
            throw new SQLException("getConnection null");
        }

        setInternalAutoCommit();
    }

    private void setInternalAutoCommit() throws SQLException {
        Connection conn = realConnection.getPhysicalConnection();
        if (conn.getAutoCommit() != isAutoCommit) {
            try {
                conn.setAutoCommit(isAutoCommit);
            } catch (SQLException e) {
                throw errorWrapper(e);
            }
        }
    }

    @Override
    public void setAutoCommit(boolean autoCommit) throws SQLException {
        checkClosed();
        isAutoCommit = autoCommit;
        if (realConnection != null) {
            setInternalAutoCommit();
        }
        if (autoCommit) {
            checkRecyclingConnection();
        }
    }

    private Connection getPhysicalConnection() {
        return realConnection.getPhysicalConnection();
    }

    protected void unregisterStatement(Statement stmt) {
        for (Iterator<LBStatement> it = stmts.iterator(); it.hasNext();) {
            if (stmt == it.next()) {
                it.remove();
                checkRecyclingConnection();
                return;
            }
        }
    }

    @Override
    public Statement createStatement() throws SQLException {
        checkClosed();

        initCurrentConnection();

        LBStatement lbStatement = null;
        try {
            lbStatement = new LBStatement(getPhysicalConnection().createStatement(), this);
            stmts.add(lbStatement);
        } catch (SQLException ex) {
            throw errorWrapper(ex);
        }

        return lbStatement;
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
        checkClosed();

        initCurrentConnection();

        LBStatement lbStatement = null;
        try {
            lbStatement = new LBStatement(getPhysicalConnection().createStatement(resultSetType,
                    resultSetConcurrency), this);
            stmts.add(lbStatement);
        } catch (SQLException ex) {
            throw errorWrapper(ex);
        }

        return lbStatement;
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability)
            throws SQLException {
        checkClosed();

        initCurrentConnection();

        LBStatement lbStatement = null;
        try {
            lbStatement = new LBStatement(getPhysicalConnection().createStatement(resultSetType,
                    resultSetConcurrency, resultSetHoldability), this);
            stmts.add(lbStatement);
        } catch (SQLException ex) {
            throw errorWrapper(ex);
        }

        return lbStatement;
    }

    @Override
    public PreparedStatement prepareStatement(String sql) throws SQLException {
        checkClosed();

        initCurrentConnection();

        LBPreparedStatement lbPreparedStatement = null;
        try {
            lbPreparedStatement = new LBPreparedStatement(getPhysicalConnection().prepareStatement(sql), this, sql);
            stmts.add(lbPreparedStatement);
        } catch (SQLException ex) {
            throw errorWrapper(ex);
        }

        return lbPreparedStatement;
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
        checkClosed();

        initCurrentConnection();

        LBPreparedStatement lbPreparedStatement = null;
        try {
            lbPreparedStatement = new LBPreparedStatement(
                    getPhysicalConnection().prepareStatement(sql, autoGeneratedKeys), this, sql);
            stmts.add(lbPreparedStatement);
        } catch (SQLException ex) {
            throw errorWrapper(ex);
        }

        return lbPreparedStatement;
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
        checkClosed();

        initCurrentConnection();

        LBPreparedStatement lbPreparedStatement = null;
        try {
            lbPreparedStatement = new LBPreparedStatement(getPhysicalConnection().prepareStatement(sql, columnIndexes),
                    this, sql);
            stmts.add(lbPreparedStatement);
        } catch (SQLException ex) {
            throw errorWrapper(ex);
        }

        return lbPreparedStatement;
    }

    @Override
    public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
        checkClosed();

        initCurrentConnection();

        LBPreparedStatement lbPreparedStatement = null;
        try {
            lbPreparedStatement = new LBPreparedStatement(getPhysicalConnection().prepareStatement(sql, columnNames),
                    this, sql);
            stmts.add(lbPreparedStatement);
        } catch (SQLException ex) {
            throw errorWrapper(ex);
        }

        return lbPreparedStatement;
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency)
            throws SQLException {
        checkClosed();

        initCurrentConnection();

        LBPreparedStatement lbPreparedStatement = null;
        try {
            lbPreparedStatement = new LBPreparedStatement(getPhysicalConnection().
                    prepareStatement(sql, resultSetType, resultSetConcurrency), this, sql);
            stmts.add(lbPreparedStatement);
        } catch (SQLException ex) {
            throw errorWrapper(ex);
        }

        return lbPreparedStatement;
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency,
                                              int resultSetHoldability) throws SQLException {
        checkClosed();

        initCurrentConnection();

        LBPreparedStatement lbPreparedStatement = null;
        try {
            lbPreparedStatement = new LBPreparedStatement(getPhysicalConnection().
                    prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability), this, sql);
            stmts.add(lbPreparedStatement);
        } catch (SQLException ex) {
            throw errorWrapper(ex);
        }

        return lbPreparedStatement;
    }

    @Override
    public boolean getAutoCommit() throws SQLException {
        return isAutoCommit;
    }

    @Override
    public void commit() throws SQLException {
        checkClosed();

        if (realConnection != null) {
            try {
                getPhysicalConnection().commit();
            } catch (SQLException ex) {
                throw errorWrapper(ex);
            }
        }
        if (isTransactionBegun()) {
            setTransactionBegun(false);
        }
        checkRecyclingConnection();
    }

    @Override
    public void rollback() throws SQLException {
        checkClosed();

        if (realConnection != null) {
            try {
                getPhysicalConnection().rollback();
            } catch (SQLException ex) {
                throw errorWrapper(ex);
            }
        }
        if (isTransactionBegun()) {
            setTransactionBegun(false);
        }
        checkRecyclingConnection();
    }

    @Override
    public void rollback(Savepoint savepoint) throws SQLException {
        unsupported();
    }

    private void closeStatements() throws SQLException {
        if (!stmts.isEmpty()) {
            List<LBStatement> stmtList = new ArrayList<>(stmts);
            for (LBStatement stmt : stmtList) {
                stmt.close();
            }
            stmts.clear();
        }
    }

    @Override
    public void close() throws SQLException {
        if (isClosed) {
            return;
        }
        closeStatements();
        if (!isAutoCommit) {
            rollback();
        } else if (isTransactionBegun && realConnection != null) {
            realConnection.markUnhealthy();
        }
        setTransactionBegun(false);
        connectionManager.unregisterLogicalConnection(this);
        realConnection = null;
        isClosed = true;
    }

    @Override
    public boolean isClosed() throws SQLException {
        return isClosed;
    }

    @Override
    public void setTransactionIsolation(int level) throws SQLException {
        unsupported();
    }

    @Override
    public void abort(Executor executor) throws SQLException {
        unsupported();
    }

    @Override
    public void clearWarnings() throws SQLException {

    }

    @Override
    public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
        return null;
    }

    @Override
    public Blob createBlob() throws SQLException {
        return null;
    }

    @Override
    public Clob createClob() throws SQLException {
        return null;
    }

    @Override
    public NClob createNClob() throws SQLException {
        return null;
    }

    @Override
    public SQLXML createSQLXML() throws SQLException {
        return null;
    }

    @Override
    public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
        return null;
    }

    @Override
    public String getCatalog() throws SQLException {
        return null;
    }

    @Override
    public Properties getClientInfo() throws SQLException {
        return null;
    }

    @Override
    public String getClientInfo(String name) throws SQLException {
        return null;
    }

    @Override
    public int getHoldability() throws SQLException {
        return 0;
    }

    @Override
    public DatabaseMetaData getMetaData() throws SQLException {
        return new LBDatabaseMetaData(lbDriverUrl);
    }

    @Override
    public int getNetworkTimeout() throws SQLException {
        unsupported();
        return 0;
    }

    @Override
    public String getSchema() throws SQLException {
        return schemaName;
    }

    @Override
    public int getTransactionIsolation() throws SQLException {
        return Connection.TRANSACTION_NONE;
    }

    @Override
    public Map<String, Class<?>> getTypeMap() throws SQLException {
        return null;
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        return null;
    }

    @Override
    public boolean isReadOnly() throws SQLException {
        return false;
    }

    @Override
    public boolean isValid(int timeout) throws SQLException {
        return false;
    }

    @Override
    public String nativeSQL(String sql) throws SQLException {
        return null;
    }

    @Override
    public CallableStatement prepareCall(String sql) throws SQLException {
        return null;
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        return null;
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency,
                                         int resultSetHoldability) throws SQLException {
        return null;
    }

    @Override
    public void releaseSavepoint(Savepoint savepoint) throws SQLException {

    }

    @Override
    public void setCatalog(String catalog) throws SQLException {

    }

    @Override
    public void setClientInfo(String name, String value) throws SQLClientInfoException {

    }

    @Override
    public void setClientInfo(Properties properties) throws SQLClientInfoException {

    }

    @Override
    public void setHoldability(int holdability) throws SQLException {

    }

    @Override
    public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {

    }

    @Override
    public void setReadOnly(boolean readOnly) throws SQLException {

    }

    @Override
    public Savepoint setSavepoint() throws SQLException {
        unsupported();
        return null;
    }

    @Override
    public Savepoint setSavepoint(String name) throws SQLException {
        unsupported();
        return null;
    }

    @Override
    public void setSchema(String schema) throws SQLException {
        if (realConnection != null)
            throw new SQLException("Can not set schema while physical connection is binding");
        this.schemaName = schema;
    }

    @Override
    public void setTypeMap(Map<String, Class<?>> map) throws SQLException {

    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return false;
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        unsupported();
        return null;
    }

    private void unsupported() throws SQLException {
        if (lbDriverUrl.getUnsupportedMethodBehavior() == UnsupportedMethodBehavior.ThrowException) {
            throw new SQLException("Not supported method!");
        }
    }
}
