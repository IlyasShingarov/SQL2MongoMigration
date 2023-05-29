package com.ishingarov.migrationtool.storage;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ishingarov.migrationtool.format.JsonSchemaFormatter;
import com.ishingarov.migrationtool.repository.MetadataRepository;
import com.ishingarov.migrationtool.repository.domain.TableMetaData;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Getter
@Setter
public class MetadataStorage {

    private final JsonSchemaFormatter jsonSchemaFormatter;
    private final MetadataRepository metadataRepository;

    private String schema;
    private List<String> tableNames;
    private List<String> selectedTableNames;

    private Map<String, Pair<ObjectNode, TableMetaData>> fullJsonSchema;
    private Map<String, Pair<ObjectNode, TableMetaData>> jsonSchema;
    private Map<String, ObjectNode> resultJsonSchema;

    public void updateJsonSchema() {
        fullJsonSchema = tableNames.stream()
                .map(name -> metadataRepository.getTableMetadata(schema, name))
                .map(jsonSchemaFormatter::getTableJsonSchema)
                .collect(Collectors.toMap(t -> t.getSecond().getTableName(), Function.identity()));

        jsonSchema = fullJsonSchema.entrySet().stream()
                .filter(item -> selectedTableNames.contains(item.getValue().getSecond().getTableName()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }


}
