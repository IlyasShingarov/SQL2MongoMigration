package com.ishingarov.migrationtool.repository.domain;

public record ColumnMetadata(String columnName, String datatype, String typename, int pos, int nullable) {
}
