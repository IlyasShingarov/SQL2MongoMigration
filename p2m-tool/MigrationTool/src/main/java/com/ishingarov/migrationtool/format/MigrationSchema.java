package com.ishingarov.migrationtool.format;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Data;
import org.springframework.data.util.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class MigrationSchema {
    private String name;
    private Props primaryKey;
    private Map<String, Props> properties = new HashMap<>();
    private Map<String, EmbedProp> embeddedProperties = new HashMap<>();
    private Map<String, EmbedArrProp> embeddedArrayProperties = new HashMap<>();
    private Map<String, EmbeddedDoc> embeddedDocuments = new HashMap<>();
    private Map<String, Ref> references = new HashMap<>();


    /*
    For each node
        if node is an array
            if node is embedded
                if node has properties --> embedded document of type array
                else --> embedded property of type array
            if node is of type reference
                then --> array of references
        else
            if node is embedded
                if node has properties --> embedded document
                else --> embedded property
            if node is of type reference
                then --> array of references
     */
    public MigrationSchema(ObjectNode json) {
        this.name = json.get("__name").asText();
        var props = json.get("properties");

        var it = props.fields();
        while (it.hasNext()) {
            var curr = it.next();

            if (curr.getValue().isArray()) {
                var node = curr.getValue().get(0);
                if (node.has("type") && node.get("type").asText().equals("embedded")) {
                    if (node.has("properties")) {
                        var embeddedSchema = new MigrationSchema((ObjectNode) node);
                        var embed = new EmbeddedDoc(curr.getKey(),
                                node.get("__name").asText(),
                                PropType.ARRAY,
                                embeddedSchema);
                        embeddedDocuments.put(curr.getKey(), embed);
                    } else {
                        if (node.get("join").isArray()) {
                            List<String> joins = new ArrayList<>();
                            for (JsonNode joinTable : node.get("join")) {
                                joins.add(joinTable.asText());
                            }
                            var embed = new EmbedArrProp(curr.getKey(),
                                    node.get("datatype").asText(),
                                    Pair.of(node.get("source").asText(),
                                            node.get("name").asText()),
                                    joins
                            );
                        embeddedArrayProperties.put(curr.getKey(), embed);
                        }
                    }
                }
                if (node.has("type") && node.get("type").asText().equals("reference")) {
                    references.put(curr.getKey(), new Ref(curr.getKey(),
                            node.get("source").asText(),
                            node.get("pkname").asText(),
                            PropType.ARRAY
                    ));
                }
            } else {
                if (curr.getValue().has("pk") && curr.getValue().get("pk").asBoolean()) {
                    this.primaryKey = new Props(curr.getKey(), curr.getValue().get("datatype").asText());
                } else if (curr.getValue().get("type").asText().equals("property")) {
                    var prop = new Props(curr.getKey(), curr.getValue().get("datatype").asText());
                    properties.put(curr.getKey(), prop);
                }

                if (curr.getValue().get("type").asText().equals("embedded")) {
                    if (curr.getValue().has("properties")) {
                        var embeddedSchema = new MigrationSchema((ObjectNode) curr.getValue());
                        var embed = new EmbeddedDoc(curr.getKey(), curr.getValue().get("__name").asText(), PropType.SINGLE, embeddedSchema);
                        embeddedDocuments.put(curr.getKey(), embed);
                    } else {
                        var embed = new EmbedProp(curr.getKey(),
                                curr.getValue().get("datatype").asText(),
                                Pair.of(curr.getValue().get("source").asText(),
                                        curr.getValue().get("name").asText())
                        );
                        embeddedProperties.put(curr.getKey(), embed);
                    }
                }

                if (curr.getValue().get("type").asText().equals("reference")) {
                    var ref = new Ref(curr.getKey(),
                            curr.getValue().get("source").asText(),
                            curr.getValue().get("pkname").asText(),
                            PropType.SINGLE
                    );
                    references.put(curr.getKey(), ref);
                }
            }
        }
    }

    public int getAllReferences() {
        int referenceCount = references.size();

        int embeddedReferenceCount = 0;
        for (EmbeddedDoc embedded : embeddedDocuments.values()) {
            embeddedReferenceCount += embedded.schema().getAllReferences();
        }

        return referenceCount + embeddedReferenceCount;
    }

    public boolean hasNoReferences() {
        return getAllReferences() == 0;
    }

    public boolean hasReference(String target) {
        boolean hasRootReference = references.values().stream()
                .map(Ref::source)
                .anyMatch(name -> name.equals(target));

        boolean hasChildReference = false;
        for (EmbeddedDoc embedded : embeddedDocuments.values()) {
            hasChildReference = hasChildReference || embedded.schema().hasReference(target);
        }

        return hasRootReference || hasChildReference;
    }

}


// TODO: Migrate embedded props as arrays