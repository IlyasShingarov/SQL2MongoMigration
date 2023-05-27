package com.ishingarov.migrationtool.repository;

import com.ishingarov.migrationtool.repository.domain.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@Repository
@Slf4j
@RequiredArgsConstructor
public class MetadataRepository {
    private final JdbcTemplate jdbcTemplate;

    public TableMetaData getTableMetadata(String schema, String name) {
        var table = new TableMetaData(name);
        Connection connection = null;
        try {
            connection = jdbcTemplate.getDataSource().getConnection();
            var metadata = connection.getMetaData();

            var pkeys = getPrimaryKeyMetadata(metadata.getPrimaryKeys(null, schema, name));
            var fkeys = getForeignKeyMetadata(metadata.getImportedKeys(null, schema, name));
            var cols = getColumnMetadata(metadata.getColumns(null, schema, name, null));
            var importedRelationships = getImportedRelationships(metadata, schema, name);
            var exportedRelationships = getExportedRelationships(metadata, schema, name);

            table.getPrimaryKeyMetadata().addAll(pkeys);
            table.getForeignKeyMetadata().addAll(fkeys);
            table.getColumnMetadata().addAll(cols);
            table.getImportedRelationships().addAll(importedRelationships);
            table.getExportedRelationships().addAll(exportedRelationships);

            table.setTableType(table.getForeignKeyMetadata().size() == 0 ? TableType.STRONG : TableType.WEAK);

        } catch (SQLException se) {
            log.error("Error while accessing metadata for table {}", name, se);
        } finally {
            DataSourceUtils.releaseConnection(connection, jdbcTemplate.getDataSource());
        }

        return table;
    }

    public List<String> getTableNames(String schema) {
        String query = """
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = ? AND table_type = 'BASE TABLE'
            """;
        return jdbcTemplate.queryForList(query, String.class, schema);
    }

    private static List<ImportedRelationshipMetadata> getImportedRelationships(
            DatabaseMetaData metaData, String schema, String tableName) throws SQLException {

        ResultSet foreignKeys = metaData.getImportedKeys(null, schema, tableName);

        List<ImportedRelationshipMetadata> importedRelationshipMetadataList = new ArrayList<>();
        log.debug("Table '{}'", tableName);
        while (foreignKeys.next()) {
            var foreignKeyColumnName = foreignKeys.getString("FKCOLUMN_NAME");
            var externalTableName = foreignKeys.getString("PKTABLE_NAME");
            var externalPrimaryKeyColumnName = foreignKeys.getString("PKCOLUMN_NAME");
            log.debug("FK : {} ---> {}", foreignKeyColumnName, externalTableName + "." + externalPrimaryKeyColumnName);
            importedRelationshipMetadataList.add(
                    new ImportedRelationshipMetadata(
                            foreignKeyColumnName,
                            externalTableName,
                            externalPrimaryKeyColumnName,
                            RelationshipType.ONE2MANY
                    )
            );
        }

        return importedRelationshipMetadataList;
    }

    private static List<ExportedRelationshipMetadata> getExportedRelationships(
            DatabaseMetaData metaData, String schema, String tableName) throws SQLException {

        ResultSet exportedKeys = metaData.getExportedKeys(null, schema, tableName);

        List<ExportedRelationshipMetadata> exportedRelationshipMetadataList = new ArrayList<>();
        log.debug("Table '{}'", tableName);
        while (exportedKeys.next()) {

            var externalTableName = exportedKeys.getString("FKTABLE_NAME");
            var externalColumnName = exportedKeys.getString("FKCOLUMN_NAME");
            var sourceTableName = exportedKeys.getString("PKTABLE_NAME");
            var sourceColumnName = exportedKeys.getString("PKCOLUMN_NAME");

            log.debug("EXP_TED: {}.{} ---> {}.{}", externalTableName, externalColumnName, sourceTableName, sourceColumnName);

            exportedRelationshipMetadataList.add(
                    new ExportedRelationshipMetadata(sourceTableName, sourceColumnName, externalTableName, externalColumnName));

        }

        return exportedRelationshipMetadataList;
    }

    private static List<ImportedRelationshipMetadata> getRelationships(
            DatabaseMetaData metaData, String schema, String tableName) throws SQLException {

        ResultSet primaryKeyColumns = metaData.getPrimaryKeys(null, schema, tableName);
        ResultSet foreignKeys = metaData.getImportedKeys(null, schema, tableName);
        ResultSet exportedKeys = metaData.getExportedKeys(null, schema, tableName);

        log.info("TABLE: {}", tableName);
        while (primaryKeyColumns.next()) {
            log.info("PK: {}", primaryKeyColumns.getString("COLUMN_NAME"));
        }
//
//        while (foreignKeys.next()) {
//            var foreignKeyColumnName = foreignKeys.getString("FKCOLUMN_NAME");
//            var externalTableName = foreignKeys.getString("PKTABLE_NAME")
//            var externalPrimaryKeyColumnName = foreignKeys.getString("PKCOLUMN_NAME")
//
//            log.info("FK : {} ---> {}", foreignKeyColumnName, externalTableName + "." + externalPrimaryKeyColumnName);
//        }

        while (exportedKeys.next()) {
            log.info("EXPORTED: {}.{} ---> {}.{}",
                    exportedKeys.getString("FKTABLE_NAME"),
                    exportedKeys.getString("FKCOLUMN_NAME"),
                    exportedKeys.getString("PKTABLE_NAME"),
                    exportedKeys.getString("PKCOLUMN_NAME")
            );
        }
        //        Map<String, String> primaryKeys = new HashMap<>();
//        while (primaryKeyColumns.next()) {
//            String primaryKeyColumn = primaryKeyColumns.getString("COLUMN_NAME");
//            primaryKeys.put(primaryKeyColumn, null);
//        }

        // Get foreign keys that reference the primary key columns
//        List<RelationshipMetadata> relationships = new ArrayList<>();
//        while (foreignKeys.next()) {
//            String foreignKeyColumn = foreignKeys.getString("FKCOLUMN_NAME");
//            String referencedTableName = foreignKeys.getString("PKTABLE_NAME");
//            String referencedPrimaryKeyColumn = foreignKeys.getString("PKCOLUMN_NAME");
//
//            // Check if the foreign key references all columns in the primary key
//            boolean isOneToOne = true;
//            ResultSet referencedPrimaryKeys = metaData.getPrimaryKeys(null, schema, referencedTableName);
//            while (referencedPrimaryKeys.next()) {
//                String referencedPrimaryKey = referencedPrimaryKeys.getString("COLUMN_NAME");
//                if (!primaryKeys.containsKey(referencedPrimaryKey)) {
//                    isOneToOne = false;
//                    break;
//                }
//            }
//
//            if (isOneToOne) {
//                relationships.add(new RelationshipMetadata(foreignKeyColumn, referencedTableName, referencedPrimaryKeyColumn, RelationshipType.ONE2ONE));
//            } else {
//                relationships.add(new RelationshipMetadata(foreignKeyColumn, referencedTableName, referencedPrimaryKeyColumn, RelationshipType.ONE2MANY));
//            }
//        }
        return null;
    }

    private static ArrayList<ColumnMetadata> getColumnMetadata(ResultSet rs) throws SQLException {
        var cols = new ArrayList<ColumnMetadata>();
        while (rs.next()) {
            cols.add(new ColumnMetadata(
                    rs.getString("COLUMN_NAME"),
                    rs.getString("DATA_TYPE"),
                    rs.getString("TYPE_NAME"),
                    rs.getInt("ORDINAL_POSITION"),
                    rs.getInt("NULLABLE")));
        }
        return cols;
    }

    private static ArrayList<ForeignKeyMetadata> getForeignKeyMetadata(ResultSet rs) throws SQLException {
        var fkeys = new ArrayList<ForeignKeyMetadata>();
        while (rs.next()) {
            fkeys.add(new ForeignKeyMetadata(
                    rs.getString("PKTABLE_NAME"),
                    rs.getString("PKCOLUMN_NAME"),
                    rs.getString("FKTABLE_NAME"),
                    rs.getString("FKCOLUMN_NAME"))
            );
        }
        return fkeys;
    }

    private static ArrayList<PrimaryKeyMetadata> getPrimaryKeyMetadata(ResultSet rs) throws SQLException {
        var pkeys = new ArrayList<PrimaryKeyMetadata>();
        while (rs.next()) {
            pkeys.add(new PrimaryKeyMetadata(rs.getString("COLUMN_NAME"), rs.getString("PK_NAME")));
        }
        return pkeys;
    }

    private static List<String> getTableNames(ResultSet tables) throws SQLException {
        List<String> tableNames = new ArrayList<>();
        while (tables.next()) {
            tableNames.add(tables.getString("TABLE_NAME"));
        }
        return tableNames;
    }


    @Deprecated
    public Map<String, TableMetaData> findAllTables(String schema) throws SQLException {
        var metadata = Objects.requireNonNull(jdbcTemplate.getDataSource()).getConnection().getMetaData();

        var tableNames = getTableNames(
                metadata.getTables(null, schema, null, new String[] {"TABLE"}));

        var tables = new HashMap<String, TableMetaData>();
        for (String name : tableNames) {
            var table = getTableMetadata(schema, name);
            tables.put(table.getTableName(), table);
        }

        return tables;
    }

}