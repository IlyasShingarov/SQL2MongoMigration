Deque<MigrationSchema> queue = new ArrayDeque<>();
migrationSchemaSet.stream()
    .filter(MigrationSchema::hasNoReferences)
    .forEach(queue::add);
migrationSchemaSet.removeAll(queue);
while (migrationSchemaSet.size() != 0) {
    Iterator<MigrationSchema> iterator = migrationSchemaSet.iterator();
    while (iterator.hasNext()) {
        MigrationSchema schema = iterator.next();
        var queueNameSet = queue.stream()
                .map(MigrationSchema::getName)
                .collect(Collectors.toSet());
        boolean hasQueuedReference = queueNameSet.stream()
                .anyMatch(schema::hasReference);
        boolean hasUnsafeReference = migrationSchemaSet.stream()
                .anyMatch(unsafe -> schema.hasReference(unsafe.getName()));
        if (hasQueuedReference && !hasUnsafeReference)
            queue.add(schema); iterator.remove();
    }
}