/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.optus.insights.presto.plugin.teradata;

import com.facebook.presto.plugin.jdbc.BaseJdbcClient;
import com.facebook.presto.plugin.jdbc.BaseJdbcConfig;
import com.facebook.presto.plugin.jdbc.JdbcConnectorId;
import com.facebook.presto.spi.SchemaTableName;
import com.facebook.presto.spi.type.Type;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;
import com.teradata.jdbc.TeraDriver;

import javax.inject.Inject;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;

import static java.util.Locale.ENGLISH;

public class TeradataClient
        extends BaseJdbcClient
{
    @Inject
    public TeradataClient(JdbcConnectorId connectorId, BaseJdbcConfig config, TeradataConfig teradataConfig)
            throws SQLException
    {
        super(connectorId, config, "`", new TeraDriver());
        connectionProperties.setProperty("nullCatalogMeansCurrent", "false");
        if (teradataConfig.isAutoReconnect()) {
            connectionProperties.setProperty("autoReconnect", String.valueOf(teradataConfig.isAutoReconnect()));
            connectionProperties.setProperty("maxReconnects", String.valueOf(teradataConfig.getMaxReconnects()));
        }
        if (teradataConfig.getConnectionTimeout() != null) {
            connectionProperties.setProperty("connectTimeout", String.valueOf(teradataConfig.getConnectionTimeout().toMillis()));
        }
    }

    @Override
    public Set<String> getSchemaNames()
    {
        // for Teradata, we need to list catalogs instead of schemas
        try (Connection connection = driver.connect(connectionUrl, connectionProperties);
                ResultSet resultSet = connection.getMetaData().getSchemas()) {
            ImmutableSet.Builder<String> schemaNames = ImmutableSet.builder();
            while (resultSet.next()) {
                String schemaName = resultSet.getString("TABLE_SCHEM").toLowerCase(ENGLISH);
                schemaNames.add(schemaName);
            }
            return schemaNames.build();
        }
        catch (SQLException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    protected ResultSet getTables(Connection connection, String schemaName, String tableName)
            throws SQLException
    {
        // MySQL maps their "database" to SQL catalogs and does not have schemas
        return connection.getMetaData().getTables(schemaName, null, tableName, null);
    }

    @Override
    protected SchemaTableName getSchemaTableName(ResultSet resultSet)
            throws SQLException
    {
        // MySQL uses catalogs instead of schemas
        return new SchemaTableName(
                resultSet.getString("TABLE_CAT").toLowerCase(ENGLISH),
                resultSet.getString("TABLE_NAME").toLowerCase(ENGLISH));

    }

    @Override
    protected String toSqlType(Type type)
    {
        String sqlType = super.toSqlType(type);
        switch (sqlType) {
            case "varchar":
                return "mediumtext";
            case "varbinary":
                return "mediumblob";
            case "time with timezone":
                return "time";
            case "timestamp":
            case "timestamp with timezone":
                return "datetime";
        }
        return sqlType;
    }
}
