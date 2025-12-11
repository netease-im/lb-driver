package com.netease.nim.lbd;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.*;
import java.util.Calendar;

public class LBPreparedStatement extends LBStatement implements PreparedStatement {

    private final PreparedStatement stmt;

    private final String sql;

    public LBPreparedStatement(PreparedStatement stmt, LBConnection connection, String sql) {
        super(stmt, connection);
        this.stmt = stmt;
        this.sql = sql;
    }

    public void addBatch() throws SQLException {
        checkOpen();

        try {
            stmt.addBatch();
        } catch (Throwable t) {
            throw connection.errorWrapper(t);
        }
    }

    public void clearParameters() throws SQLException {
        checkOpen();

        try {
            stmt.clearParameters();
        } catch (Throwable t) {
            throw connection.errorWrapper(t);
        }
    }

    public boolean execute() throws SQLException {
        checkOpen();

        try {
            boolean result = stmt.execute();
            dealTransaction(sql);
            return result;
        } catch (Throwable t) {
            throw connection.errorWrapper(t);
        }
    }

    public long executeLargeUpdate() throws SQLException {
        throw new SQLException("Operation Not Support.");
    }

    public ResultSet executeQuery() throws SQLException {
        checkOpen();

        try {
            ResultSet result = stmt.executeQuery();
            dealTransaction(sql);
            return result;
        } catch (Throwable t) {
            throw connection.errorWrapper(t);
        }
    }

    public int executeUpdate() throws SQLException {
        checkOpen();

        try {
            int result = stmt.executeUpdate();
            dealTransaction(sql);
            return result;
        } catch (Throwable t) {
            throw connection.errorWrapper(t);
        }
    }

    public ResultSetMetaData getMetaData() throws SQLException {
        checkOpen();

        try {
            return stmt.getMetaData();
        } catch (Throwable t) {
            throw connection.errorWrapper(t);
        }
    }

    public ParameterMetaData getParameterMetaData() throws SQLException {
        checkOpen();

        try {
            return stmt.getParameterMetaData();
        } catch (Throwable t) {
            throw connection.errorWrapper(t);
        }
    }

    public void setArray(int parameterIndex, Array x) throws SQLException {
        checkOpen();

        try {
            stmt.setArray(parameterIndex, x);
        } catch (Throwable t) {
            throw connection.errorWrapper(t);
        }
    }

    public void setAsciiStream(int parameterIndex, InputStream x) throws SQLException {
        checkOpen();

        try {
            stmt.setAsciiStream(parameterIndex, x);
        } catch (Throwable t) {
            throw connection.errorWrapper(t);
        }
    }

    public void setAsciiStream(int parameterIndex, InputStream x, int length) throws SQLException {
        checkOpen();

        try {
            stmt.setAsciiStream(parameterIndex, x, length);
        } catch (Throwable t) {
            throw connection.errorWrapper(t);
        }
    }

    public void setBigDecimal(int parameterIndex, BigDecimal x) throws SQLException {
        checkOpen();

        try {
            stmt.setBigDecimal(parameterIndex, x);
        } catch (Throwable t) {
            throw connection.errorWrapper(t);
        }
    }

    public void setBinaryStream(int parameterIndex, InputStream x) throws SQLException {
        checkOpen();

        try {
            stmt.setBinaryStream(parameterIndex, x);
        } catch (Throwable t) {
            throw connection.errorWrapper(t);
        }
    }

    public void setBinaryStream(int parameterIndex, InputStream x, int length) throws SQLException {
        checkOpen();

        try {
            stmt.setBinaryStream(parameterIndex, x, length);
        } catch (Throwable t) {
            throw connection.errorWrapper(t);
        }
    }

    public void setBlob(int parameterIndex, InputStream inputStream) throws SQLException {
        checkOpen();

        try {
            stmt.setBlob(parameterIndex, inputStream);
        } catch (Throwable t) {
            throw connection.errorWrapper(t);
        }
    }

    public void setBlob(int parameterIndex, InputStream inputStream, long length) throws SQLException {
        checkOpen();

        try {
            stmt.setBlob(parameterIndex, inputStream, length);
        } catch (Throwable t) {
            throw connection.errorWrapper(t);
        }
    }

    public void setBlob(int parameterIndex, Blob x) throws SQLException {
        checkOpen();

        try {
            stmt.setBlob(parameterIndex, x);
        } catch (Throwable t) {
            throw connection.errorWrapper(t);
        }
    }

    public void setBoolean(int parameterIndex, boolean x) throws SQLException {
        checkOpen();

        try {
            stmt.setBoolean(parameterIndex, x);
        } catch (Throwable t) {
            throw connection.errorWrapper(t);
        }
    }

    public void setByte(int parameterIndex, byte x) throws SQLException {
        checkOpen();

        try {
            stmt.setByte(parameterIndex, x);
        } catch (Throwable t) {
            throw connection.errorWrapper(t);
        }
    }

    public void setBytes(int parameterIndex, byte[] x) throws SQLException {
        checkOpen();

        try {
            stmt.setBytes(parameterIndex, x);
        } catch (Throwable t) {
            throw connection.errorWrapper(t);
        }
    }

    public void setCharacterStream(int parameterIndex, Reader reader) throws SQLException {
        checkOpen();

        try {
            stmt.setCharacterStream(parameterIndex, reader);
        } catch (Throwable t) {
            throw connection.errorWrapper(t);
        }
    }

    public void setCharacterStream(int parameterIndex, Reader reader, int length) throws SQLException {
        checkOpen();

        try {
            stmt.setCharacterStream(parameterIndex, reader, length);
        } catch (Throwable t) {
            throw connection.errorWrapper(t);
        }
    }

    public void setClob(int parameterIndex, Reader reader) throws SQLException {
        checkOpen();

        try {
            stmt.setClob(parameterIndex, reader);
        } catch (Throwable t) {
            throw connection.errorWrapper(t);
        }
    }

    public void setClob(int parameterIndex, Reader reader, long length) throws SQLException {
        checkOpen();

        try {
            stmt.setClob(parameterIndex, reader, length);
        } catch (Throwable t) {
            throw connection.errorWrapper(t);
        }
    }

    public void setClob(int parameterIndex, Clob x) throws SQLException {
        checkOpen();

        try {
            stmt.setClob(parameterIndex, x);
        } catch (Throwable t) {
            throw connection.errorWrapper(t);
        }
    }

    public void setDate(int parameterIndex, Date x) throws SQLException {
        checkOpen();

        try {
            stmt.setDate(parameterIndex, x);
        } catch (Throwable t) {
            throw connection.errorWrapper(t);
        }
    }

    public void setDate(int parameterIndex, Date x, Calendar cal) throws SQLException {
        checkOpen();

        try {
            stmt.setDate(parameterIndex, x, cal);
        } catch (Throwable t) {
            throw connection.errorWrapper(t);
        }
    }

    public void setDouble(int parameterIndex, double x) throws SQLException {
        checkOpen();

        try {
            stmt.setDouble(parameterIndex, x);
        } catch (Throwable t) {
            throw connection.errorWrapper(t);
        }
    }

    public void setFloat(int parameterIndex, float x) throws SQLException {
        checkOpen();

        try {
            stmt.setFloat(parameterIndex, x);
        } catch (Throwable t) {
            throw connection.errorWrapper(t);
        }
    }

    public void setInt(int parameterIndex, int x) throws SQLException {
        checkOpen();

        try {
            stmt.setInt(parameterIndex, x);
        } catch (Throwable t) {
            throw connection.errorWrapper(t);
        }
    }

    public void setLong(int parameterIndex, long x) throws SQLException {
        checkOpen();

        try {
            stmt.setLong(parameterIndex, x);
        } catch (Throwable t) {
            throw connection.errorWrapper(t);
        }
    }

    public void setNCharacterStream(int parameterIndex, Reader value) throws SQLException {
        checkOpen();

        try {
            stmt.setNCharacterStream(parameterIndex, value);
        } catch (Throwable t) {
            throw connection.errorWrapper(t);
        }
    }

    public void setNCharacterStream(int parameterIndex, Reader value, long length) throws SQLException {
        checkOpen();

        try {
            stmt.setNCharacterStream(parameterIndex, value, length);
        } catch (Throwable t) {
            throw connection.errorWrapper(t);
        }
    }

    public void setNClob(int parameterIndex, Reader reader) throws SQLException {
        checkOpen();

        try {
            stmt.setNClob(parameterIndex, reader);
        } catch (Throwable t) {
            throw connection.errorWrapper(t);
        }
    }

    public void setNClob(int parameterIndex, Reader reader, long length) throws SQLException {
        checkOpen();

        try {
            stmt.setNClob(parameterIndex, reader, length);
        } catch (Throwable t) {
            throw connection.errorWrapper(t);
        }
    }

    public void setNClob(int parameterIndex, NClob value) throws SQLException {
        checkOpen();

        try {
            stmt.setNClob(parameterIndex, value);
        } catch (Throwable t) {
            throw connection.errorWrapper(t);
        }
    }

    public void setNString(int parameterIndex, String value) throws SQLException {
        checkOpen();

        try {
            stmt.setNString(parameterIndex, value);
        } catch (Throwable t) {
            throw connection.errorWrapper(t);
        }
    }

    public void setNull(int parameterIndex, int sqlType) throws SQLException {
        checkOpen();

        try {
            stmt.setNull(parameterIndex, sqlType);
        } catch (Throwable t) {
            throw connection.errorWrapper(t);
        }
    }

    public void setNull(int parameterIndex, int sqlType, String typeName) throws SQLException {
        checkOpen();

        try {
            stmt.setNull(parameterIndex, sqlType, typeName);
        } catch (Throwable t) {
            throw connection.errorWrapper(t);
        }
    }

    public void setObject(int parameterIndex, Object x) throws SQLException {
        checkOpen();

        try {
            stmt.setObject(parameterIndex, x);
        } catch (Throwable t) {
            throw connection.errorWrapper(t);
        }
    }

    public void setObject(int parameterIndex, Object x, int targetSqlType) throws SQLException {
        checkOpen();

        try {
            stmt.setObject(parameterIndex, x, targetSqlType);
        } catch (Throwable t) {
            throw connection.errorWrapper(t);
        }
    }

    public void setObject(int parameterIndex, Object x, int targetSqlType, int scaleOrLength) throws SQLException {
        checkOpen();

        try {
            stmt.setObject(parameterIndex, x, targetSqlType, scaleOrLength);
        } catch (Throwable t) {
            throw connection.errorWrapper(t);
        }
    }

    public void setRef(int parameterIndex, Ref x) throws SQLException {
        checkOpen();

        try {
            stmt.setRef(parameterIndex, x);
        } catch (Throwable t) {
            throw connection.errorWrapper(t);
        }
    }

    public void setRowId(int parameterIndex, RowId x) throws SQLException {
        checkOpen();

        try {
            stmt.setRowId(parameterIndex, x);
        } catch (Throwable t) {
            throw connection.errorWrapper(t);
        }
    }

    public void setShort(int parameterIndex, short x) throws SQLException {
        checkOpen();

        try {
            stmt.setShort(parameterIndex, x);
        } catch (Throwable t) {
            throw connection.errorWrapper(t);
        }
    }

    public void setSQLXML(int parameterIndex, SQLXML xmlObject) throws SQLException {
        checkOpen();

        try {
            stmt.setSQLXML(parameterIndex, xmlObject);
        } catch (Throwable t) {
            throw connection.errorWrapper(t);
        }
    }

    public void setString(int parameterIndex, String x) throws SQLException {
        checkOpen();

        try {
            stmt.setString(parameterIndex, x);
        } catch (Throwable t) {
            throw connection.errorWrapper(t);
        }
    }

    public void setTime(int parameterIndex, Time x) throws SQLException {
        checkOpen();

        try {
            stmt.setTime(parameterIndex, x);
        } catch (Throwable t) {
            throw connection.errorWrapper(t);
        }
    }

    public void setTime(int parameterIndex, Time x, Calendar cal) throws SQLException {
        checkOpen();

        try {
            stmt.setTime(parameterIndex, x, cal);
        } catch (Throwable t) {
            throw connection.errorWrapper(t);
        }
    }

    public void setTimestamp(int parameterIndex, Timestamp x) throws SQLException {
        checkOpen();

        try {
            stmt.setTimestamp(parameterIndex, x);
        } catch (Throwable t) {
            throw connection.errorWrapper(t);
        }
    }

    public void setTimestamp(int parameterIndex, Timestamp x, Calendar cal) throws SQLException {
        checkOpen();

        try {
            stmt.setTimestamp(parameterIndex, x, cal);
        } catch (Throwable t) {
            throw connection.errorWrapper(t);
        }
    }

    @SuppressWarnings("deprecation")
	public void setUnicodeStream(int parameterIndex, InputStream x, int length) throws SQLException {
        checkOpen();

        try {
            stmt.setUnicodeStream(parameterIndex, x, length);
        } catch (Throwable t) {
            throw connection.errorWrapper(t);
        }
    }

    public void setURL(int parameterIndex, URL x) throws SQLException {
        checkOpen();

        try {
            stmt.setURL(parameterIndex, x);
        } catch (Throwable t) {
            throw connection.errorWrapper(t);
        }
    }

    public void setAsciiStream(int parameterIndex, InputStream x, long length) throws SQLException {
        checkOpen();

        try {
            stmt.setAsciiStream(parameterIndex, x, length);
        } catch (Throwable t) {
            throw connection.errorWrapper(t);
        }
    }

    public void setBinaryStream(int parameterIndex, InputStream x, long length) throws SQLException {
        checkOpen();

        try {
            stmt.setBinaryStream(parameterIndex, x, length);
        } catch (Throwable t) {
            throw connection.errorWrapper(t);
        }
    }

    public void setCharacterStream(int parameterIndex, Reader reader, long length) throws SQLException {
        checkOpen();

        try {
            stmt.setCharacterStream(parameterIndex, reader, length);
        } catch (Throwable t) {
            throw connection.errorWrapper(t);
        }
    }
}
