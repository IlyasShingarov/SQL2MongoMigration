package com.ishingarov.migrationtool.format;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ishingarov.migrationtool.repository.domain.ColumnMetadata;
import com.ishingarov.migrationtool.repository.domain.ExportedRelationshipMetadata;
import com.ishingarov.migrationtool.repository.domain.RelationshipType;
import com.ishingarov.migrationtool.repository.domain.TableMetaData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;

import java.util.Iterator;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class JsonSchemaFormatter {

    private final ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public JsonNode getReferenceNode(TableMetaData tableMetaData, RelationshipType relationshipType) {
        ObjectNode referenceNode = objectMapper.getNodeFactory().objectNode();
        referenceNode.put("source", tableMetaData.getTableName());
        referenceNode.put("pkname", tableMetaData.getPrimaryKeyMetadata().get(0).column());
        referenceNode.put("type", "reference");

        if (relationshipType == RelationshipType.ONE2MANY) {
            return objectMapper.getNodeFactory().arrayNode().add(referenceNode);
        }

        return referenceNode;
    }

    public Pair<ObjectNode, TableMetaData> getTableJsonSchema(TableMetaData tableMetadata) {
        log.trace(tableMetadata.toString());

        var jsonSchemaTable = objectMapper.getNodeFactory().objectNode();

        jsonSchemaTable.put("__name", tableMetadata.getTableName());

        var properties = objectMapper.getNodeFactory().objectNode();

        tableMetadata.getColumnMetadata().stream()
                .map(column -> makePropertyFromColumn(tableMetadata, column))
                .forEach(property -> properties.set(property.getFirst(), property.getSecond()));
        
        tableMetadata.getExportedRelationships().stream()
                .map(this::makeExportedRelationshipNode)
                .forEach(property -> properties.set(property.getFirst(), property.getSecond()));

        jsonSchemaTable.set("properties", properties);

        return Pair.of(jsonSchemaTable, tableMetadata);
    }

    public ObjectNode metadataToJson(TableMetaData tableMetaData) {
        var jsonSchemaTable = objectMapper.getNodeFactory().objectNode();

        jsonSchemaTable.put("__name", tableMetaData.getTableName());

        var properties = objectMapper.getNodeFactory().objectNode();

        tableMetaData.getColumnMetadata().stream()
                .map(column -> makePropertyFromColumn(tableMetaData, column))
                .forEach(property -> properties.set(property.getFirst(), property.getSecond()));

        tableMetaData.getExportedRelationships().stream()
                .map(this::makeExportedRelationshipNode)
                .forEach(property -> properties.set(property.getFirst(), property.getSecond()));

        jsonSchemaTable.set("properties", properties);

        return jsonSchemaTable;
    }

    public JsonNode jsonTableToEmbed(ObjectNode table, boolean leavePk, RelationshipType relationshipType) {
        JsonNode props = table.get("properties");
        if (props instanceof ObjectNode) {
            Iterator<Map.Entry<String, JsonNode>> iterator = props.fields();
            while(iterator.hasNext()) {
                Map.Entry<String, JsonNode> property = iterator.next();
                if (!leavePk && property.getValue().has("pk") && property.getValue().get("pk").asBoolean()) {
                    iterator.remove();
                } else if (property.getValue().has("type") && property.getValue().get("type").asText().equals("EXTERNAL")) {
                    iterator.remove();
                }
            }
            if (props.size() == 1) {
                var p  = (ObjectNode) props.elements().next();
                p.put("type", "embedded");
                p.put("source", table.get("__name").asText());
                return p;
            }
        }
        table.put("type", "embedded");
//        table.remove("__name");
//        p.put("type", "embedded");

        if (relationshipType == RelationshipType.ONE2MANY) {
            return objectMapper.getNodeFactory().arrayNode().add(table);
        }

        return table;
    }

    private Pair<String, ObjectNode> makePropertyFromColumn(TableMetaData tableMetadata, ColumnMetadata column) {
        var property = objectMapper.getNodeFactory().objectNode();
        property.put("name", column.columnName());
        property.put("datatype", column.typename());
        property.put("type", "property");

        // Check if a property is a primary key
        if (tableMetadata.getPrimaryKeyMetadata().stream()
                .anyMatch(pkm -> pkm.column().equals(column.columnName()))
        ) {
            property.put("pk", true);
        }

        // Check whether column is a foreign key
        var fkMeta = tableMetadata.getImportedRelationships()
                .stream()
                .filter(fk -> fk.sourceColumn().equals(column.columnName()))
                .findAny();

        // If a foreign key than describe a reference
        if (fkMeta.isPresent()) {
            property.put("fk", true);
            var ref = objectMapper.getNodeFactory().objectNode();
            ref.put("table", fkMeta.get().referencedTable());
            ref.put("column", fkMeta.get().referencedColumn());
            ref.put("type", fkMeta.get().type().toString());
            property.set("references", ref);
        }

        return Pair.of(column.columnName(), property);
    }

    private Pair<String, ObjectNode> makeExportedRelationshipNode(ExportedRelationshipMetadata relationship) {
        var property = objectMapper.getNodeFactory().objectNode();
        property.put("type", "EXTERNAL");
        property.put("fk", false);
        var ref = objectMapper.getNodeFactory().objectNode();
        ref.put("table", relationship.foreignTableName());
        ref.put("column", relationship.foreignTableColumn());
        ref.put("type", RelationshipType.ONE2MANY.toString());
        property.set("references", ref);
        return Pair.of(relationship.foreignTableName(), property);
    }

}
