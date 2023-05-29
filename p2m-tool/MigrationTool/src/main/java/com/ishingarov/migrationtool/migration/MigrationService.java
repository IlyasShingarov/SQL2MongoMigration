package com.ishingarov.migrationtool.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.ishingarov.migrationtool.format.*;
import com.ishingarov.migrationtool.repository.domain.ForeignKeyMetadata;
import com.ishingarov.migrationtool.repository.domain.TableMetaData;
import com.ishingarov.migrationtool.storage.MetadataStorage;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.util.Pair;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class MigrationService {

    private final JdbcTemplate jdbcTemplate;
    private final MongoTemplate mongoTemplate;
    private final MetadataStorage metadataStorage;

    private static final String QUERY_TEMPLATE = "SELECT %s FROM %s.%s %s %s";
    private static final String JOIN_TEMPLATE = "JOIN %s.%s ON %s.%s=%s.%s ";
    private static final String WHERE_TEMPLATE = "WHERE %s.%s=%s";

    public void migrateSchema(MigrationSchema schema) {
        StringBuilder selectArguments = getSelectParameterBuilder(schema);
        StringBuilder joinQuery = getJoinQueryBuilder(schema);

        String completeQuery = QUERY_TEMPLATE
                .formatted(selectArguments, metadataStorage.getSchema(), schema.getName(), joinQuery, "");

        List<Map<String, Object>> topLevelSelectResult = jdbcTemplate.queryForList(completeQuery);

        // Migrate primary key
        // Migrate properties
        // Migrate embedded properties
        // Migrate embedded documents
        // Migrate references
        topLevelSelectResult.parallelStream().forEach(resultRow -> {
            Document doc = new Document();
            if (schema.getPrimaryKey() != null) {
                doc.put(schema.getPrimaryKey().name(), resultRow.get(schema.getPrimaryKey().name()));
            }
            for (String key : schema.getProperties().keySet()) {
                doc.put(key, resultRow.get(key));
            }
            for (String key : schema.getEmbeddedProperties().keySet()) {
                doc.put(key, resultRow.get(key));
            }
            for (Map.Entry<String, EmbedArrProp> arrProp : schema.getEmbeddedArrayProperties().entrySet()) {
                Object currentId = resultRow.get(getPrimaryKeyColumn(schema.getName()));
                var propArray = getPropArray(schema, arrProp, currentId);
                doc.put(arrProp.getKey(), propArray);
            }
            for (Map.Entry<String, EmbeddedDoc> embeddedDoc : schema.getEmbeddedDocuments().entrySet()) {
                // Add PropType check
                Object currentId = resultRow.get(getPrimaryKeyColumn(schema.getName()));
                var embeddedDocumnent = getDocument(embeddedDoc.getValue(), schema, currentId);
                doc.put(embeddedDoc.getKey(), embeddedDocumnent);
            }
            for (Map.Entry<String, Ref> referenceEntry : schema.getReferences().entrySet()) {
                Object currentId = resultRow.get(getPrimaryKeyColumn(schema.getName()));
                var reference = getReference(schema, referenceEntry.getValue(), currentId);
                doc.put(referenceEntry.getValue().name(), reference);
            }
            mongoTemplate.insert(doc, schema.getName());
        });
    }

    private List<Object> getPropArray(MigrationSchema schema, Map.Entry<String, EmbedArrProp> arrProp, Object currentId) {
        String selectQuery = "%s.%s".formatted(arrProp.getValue().soruce().getFirst(), arrProp.getValue().soruce().getSecond());
        var mainMeta = getMetadata(schema.getName());
        var destMeta = getMetadata(arrProp.getValue().soruce().getFirst());

        StringBuilder joinQuery = new StringBuilder();

        Deque<String> queue = new ArrayDeque<>();
        queue.add(schema.getName());
        queue.addAll(arrProp.getValue().join());
        queue.add(destMeta.getTableName());
        while (queue.size() != 1) {
            var currentMetadata = getMetadata(queue.pollFirst());
            var nextMetadata = getMetadata(queue.peekFirst());

            currentMetadata.getForeignKeyMetadata().stream()
                    .filter(fk -> fk.fkTableName().equals(nextMetadata.getTableName()))
                    .findAny()
                    .ifPresent(foreignKeyMetadata -> joinQuery.append(JOIN_TEMPLATE.formatted(
                            metadataStorage.getSchema(),
                            nextMetadata.getTableName(),
                            currentMetadata.getTableName(),
                            foreignKeyMetadata.pkColumnName(),
                            foreignKeyMetadata.fkTableName(),
                            foreignKeyMetadata.fkColumnName()
                    )));

            currentMetadata.getExportedRelationships().stream()
                    .filter(rel -> rel.foreignTableName().equals(nextMetadata.getTableName()))
                    .findAny()
                    .ifPresent(relationship -> joinQuery.append(JOIN_TEMPLATE.formatted(
                            metadataStorage.getSchema(),
                            nextMetadata.getTableName(),
                            relationship.sourceTableName(),
                            relationship.sourceColumnName(),
                            relationship.foreignTableName(),
                            relationship.foreignTableColumn()
                    )));
        }

        StringBuilder whereQuery = getWhereQueryBuilder(schema, currentId);
        String finalQuery = QUERY_TEMPLATE
                .formatted(selectQuery, metadataStorage.getSchema(), schema.getName(), joinQuery, whereQuery);

        return jdbcTemplate.queryForList(finalQuery, Object.class);
    }

    private Object getDocument(EmbeddedDoc embedded, MigrationSchema parent, Object id) {
        MigrationSchema schema = embedded.schema();
        StringBuilder selectArguments = getSelectParameterBuilder(schema);
        StringBuilder joinQuery = getJoinQueryBuilder(schema, parent);
        StringBuilder whereQuery = getWhereQueryBuilder(parent, id);

        String completeQuery = QUERY_TEMPLATE
                .formatted(selectArguments, metadataStorage.getSchema(), schema.getName(), joinQuery, whereQuery);

        List<Map<String, Object>> topLevelSelectResult = jdbcTemplate.queryForList(completeQuery);

        List<Document> result = new ArrayList<>();
        for (Map<String, Object> resultRow : topLevelSelectResult) {
            Document doc = new Document();
            if (schema.getPrimaryKey() != null) {
                doc.put(schema.getPrimaryKey().name(), resultRow.get(schema.getPrimaryKey().name()));
            }
            for (String key : schema.getProperties().keySet()) {
                doc.put(key, resultRow.get(key));
            }
            for (String key : schema.getEmbeddedProperties().keySet()) {
                doc.put(key, resultRow.get(key));
            }
            for (Map.Entry<String, EmbedArrProp> arrProp : schema.getEmbeddedArrayProperties().entrySet()) {
                Object currentId = resultRow.get(getPrimaryKeyColumn(schema.getName()));
                var propArray = getPropArray(schema, arrProp, currentId);
                doc.put(arrProp.getKey(), propArray);
            }
            for (Map.Entry<String, EmbeddedDoc> embeddedDoc : schema.getEmbeddedDocuments().entrySet()) {
                Object currentId = resultRow.get(getPrimaryKeyColumn(schema.getName()));
                var embeddedDocument = getDocument(embeddedDoc.getValue(), schema, currentId);
                doc.put(embeddedDoc.getKey(), embeddedDocument);
            }
            // Migrate references
            for (Map.Entry<String, Ref> referenceEntry : schema.getReferences().entrySet()) {
                Object currentId = resultRow.get(getPrimaryKeyColumn(schema.getName()));
                var reference = getReference(schema, referenceEntry.getValue(), currentId);
                doc.put(referenceEntry.getValue().name(), reference);
            }
            result.add(doc);
        }

        if (embedded.type() == PropType.ARRAY) {
            return result;
        } else {
            return result.size() == 1 ? result.get(0) : result;
        }

    }

    private Object getReference(MigrationSchema parent, Ref referenced, Object id) {
        var selectArguments = "%s.%s".formatted(referenced.source(), referenced.pkName());

        var meta = getMetadata(parent.getName());
        var fk = meta.getForeignKeyMetadata().stream()
                .filter(fkey -> fkey.pkColumnName().equals(referenced.name()))
                .findAny()
                .orElse(null);

        String joinQuery = JOIN_TEMPLATE.formatted(
                metadataStorage.getSchema(),
                referenced.source(),
                parent.getName(),
                fk.pkColumnName(),
                referenced.source(),
                referenced.pkName()
        );
        StringBuilder whereQuery = getWhereQueryBuilder(parent, id);

        String query = QUERY_TEMPLATE.formatted(
                selectArguments,
                metadataStorage.getSchema(),
                parent.getName(),
                joinQuery,
                whereQuery);

        if (referenced.type() == PropType.SINGLE) {
            Integer resultId = jdbcTemplate.queryForObject(query, Integer.class);
            Query queryForId = new Query();
            queryForId.addCriteria(Criteria.where(referenced.pkName()).is(resultId)).fields().include("_id");
            var result = mongoTemplate.find(queryForId, Document.class, referenced.source());
            return result.get(0).getObjectId("_id");
        }
        if (referenced.type() == PropType.ARRAY) {
            List<Integer> resultSet = jdbcTemplate.queryForList(query, Integer.class);
            for (Integer refId : resultSet) {
                Query q = new Query();
                q.addCriteria(Criteria.where(referenced.pkName()).is(refId)).fields().include("_id");
                List<Document> result = mongoTemplate.find(q, Document.class, referenced.name());
                return result.stream()
                        .map(doc -> doc.getObjectId("_key"))
                        .toList();
            }
        }
        return null;
    }

    private StringBuilder getWhereQueryBuilder(MigrationSchema parent, Object id) {

        StringBuilder whereQuery = new StringBuilder();
        if (id instanceof String) {
            whereQuery.append(WHERE_TEMPLATE.formatted(parent.getName(), parent.getPrimaryKey().name(), "'" + id + "'"));
        } else {
            whereQuery.append(WHERE_TEMPLATE.formatted(parent.getName(), getPrimaryKeyColumn(parent.getName()), id));
        }
        return whereQuery;
    }

    private StringBuilder getJoinQueryBuilder(MigrationSchema schema) {
        StringBuilder joinQuery = new StringBuilder();
        if (schema.getEmbeddedProperties().size() != 0) {
            for (Map.Entry<String, EmbedProp> prop : schema.getEmbeddedProperties().entrySet()) {
                Pair<String, String> source = prop.getValue().soruce();
                String sourceColumn = getMetadata(source.getFirst())
                        .getPrimaryKeyMetadata()
                        .get(0)
                        .column();

                joinQuery.append(JOIN_TEMPLATE.formatted(
                                metadataStorage.getSchema(),
                                source.getFirst(),
                                schema.getName(), prop.getValue().name(),
                                source.getFirst(), sourceColumn)
                );
            }
        }
        return joinQuery;
    }

    private StringBuilder getJoinQueryBuilder(MigrationSchema schema, MigrationSchema parent) {
        StringBuilder joinQuery = getJoinQueryBuilder(schema);
        TableMetaData metadata = getMetadata(schema.getName());

        // Refactor this (Use map inside metadata)
        // Try to find if there's a Foreign Key pointing to a parent
        metadata.getForeignKeyMetadata().stream()
                .filter(fk -> fk.fkTableName().equals(parent.getName()))
                .map(ForeignKeyMetadata::pkColumnName)
                .findAny().ifPresent(fkColumn ->
                        joinQuery.append(JOIN_TEMPLATE.formatted(metadataStorage.getSchema(),
                                parent.getName(),
                                schema.getName(), fkColumn,
                                parent.getName(), parent.getPrimaryKey().name())));

        metadata.getExportedRelationships().stream()
                .filter(rel -> rel.foreignTableName().equals(parent.getName()))
                .findAny().ifPresent(relationshipMetadata -> joinQuery.append(
                        JOIN_TEMPLATE.formatted(metadataStorage.getSchema(),
                                relationshipMetadata.foreignTableName(),
                                relationshipMetadata.sourceTableName(), relationshipMetadata.sourceColumnName(),
                                relationshipMetadata.foreignTableName(), relationshipMetadata.foreignTableColumn())));

        return joinQuery;
    }

    private StringBuilder getSelectParameterBuilder(MigrationSchema schema) {
        StringBuilder selectTemplate = new StringBuilder();
        if (getPrimaryKeyColumn(schema.getName()) != null) {
            selectTemplate.append("%s.%s, ".formatted(schema.getName(), getPrimaryKeyColumn(schema.getName())));
        }
        for (Props prop : schema.getProperties().values()) {
            selectTemplate.append("%s.%s, ".formatted(schema.getName(), prop.name()));
        }

        if (schema.getEmbeddedProperties().size() != 0) {
            for (Map.Entry<String, EmbedProp> prop : schema.getEmbeddedProperties().entrySet()) {
                var source = prop.getValue().soruce();
                selectTemplate.append("%s.%s as %s, ".formatted(source.getFirst(), source.getSecond(), prop.getKey()));
            }
        }

        selectTemplate.delete(selectTemplate.length() - 2, selectTemplate.length());
        return selectTemplate;
    }

    private TableMetaData getMetadata(String table) {
        return metadataStorage.getFullJsonSchema().get(table).getSecond();
    }

    private String getPrimaryKeyColumn(String table) {
        return metadataStorage.getFullJsonSchema()
                .get(table)
                .getSecond()
                .getPrimaryKeyMetadata()
                .get(0)
                .column();
    }

}
