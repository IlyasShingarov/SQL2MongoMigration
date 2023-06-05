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
        table.getImportedRelationships()
            .addAll(importedRelationships);
        table.getExportedRelationships()
            .addAll(exportedRelationships);
        table.setTableType(table.getForeignKeyMetadata().size() == 0 ? 
            TableType.STRONG : TableType.WEAK);
    } catch (SQLException se) {
        log.error("Error while accessing metadata for table {}", name, se);
    } finally {
        DataSourceUtils.releaseConnection(connection, jdbcTemplate.getDataSource());
    }

    return table;
}