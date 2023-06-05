topLevelSelectResult.parallelStream().forEach(resultRow -> {
    Document doc = new Document();
    if (schema.getPrimaryKey() != null)
        doc.put(schema.getPrimaryKey().name(), resultRow.get(schema.getPrimaryKey().name()));
    for (String key : schema.getProperties().keySet())
        doc.put(key, resultRow.get(key));
    for (String key : schema.getEmbeddedProperties().keySet())
        doc.put(key, resultRow.get(key));
    for (Map.Entry<String, EmbedArrProp> arrProp : schema.getEmbeddedArrayProperties().entrySet()) {
        Object currentId = resultRow.get(getPrimaryKeyColumn(schema.getName()));
        var propArray = getPropArray(schema, arrProp, currentId);
        doc.put(arrProp.getKey(), propArray);
    }
    for (Map.Entry<String, EmbeddedDoc> embeddedDoc : schema.getEmbeddedDocuments().entrySet()) {
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