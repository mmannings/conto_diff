/*
 *
 *  * Copyright © 2014 - 2021 Leipzig University (Database Research Group)
 *  *
 *  * This program is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, version 3.
 *  *
 *  * This program is distributed in the hope that it will be useful, but
 *  * WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *  * General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.gomma.diff.utils;

import java.sql.*;
import java.util.UUID;
import org.gomma.diff.ContoDiffException;

/** Database owned by one comparison. Closing it also destroys its temporary data. */
public class DataBaseHandler implements AutoCloseable {
    private Connection connection;
    private boolean closed;
    private final String url = "jdbc:h2:mem:conto_" + UUID.randomUUID() + ";MODE=MYSQL";

    public Connection getDatabaseConnection() throws SQLException {
        ContoDiffException.checkInterrupted();
        if (closed) throw new SQLException("Comparison database is closed");
        if (connection == null) connection = DriverManager.getConnection(url);
        return connection;
    }

    public int executeDml(String query) throws SQLException {
        try (Statement statement = getDatabaseConnection().createStatement()) {
            return statement.executeUpdate(query);
        }
    }

    public int[] executeDml(String[] queries) throws SQLException {
        try (Statement statement = getDatabaseConnection().createStatement()) {
            for (String query : queries) statement.addBatch(query);
            return statement.executeBatch();
        }
    }

    public ResultSet executeSelect(String query) throws SQLException {
        Statement statement = getDatabaseConnection().createStatement();
        try {
            return statement.executeQuery(query);
        } catch (SQLException | RuntimeException e) {
            try { statement.close(); } catch (SQLException closing) { e.addSuppressed(closing); }
            throw e;
        }
    }

    public PreparedStatement prepareStatement(String query) throws SQLException {
        return getDatabaseConnection().prepareStatement(query);
    }

    public void closeStatement(Statement statement) throws SQLException {
        statement.close();
    }

    public void closeStatement(ResultSet results) throws SQLException {
        Statement statement = results.getStatement();
        try { results.close(); } finally { if (statement != null) statement.close(); }
    }

    @Override
    public void close() {
        closed = true;
        if (connection != null) {
            try { connection.close(); }
            catch (SQLException e) { throw new ContoDiffException("Cannot close comparison database", e); }
        }
    }
}
