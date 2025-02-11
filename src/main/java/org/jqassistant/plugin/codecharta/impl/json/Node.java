package org.jqassistant.plugin.codecharta.impl.json;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Builder
@Getter
@ToString
public class Node {

    private String name;
    private Type type;
    private Map<String, Number> attributes;
    private List<Node> children;

    public enum Type {
        @JsonProperty("Folder") FOLDER,
        @JsonProperty("File") FILE;
    }

}
