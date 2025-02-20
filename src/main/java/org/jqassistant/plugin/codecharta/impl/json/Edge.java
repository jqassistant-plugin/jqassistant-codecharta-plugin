package org.jqassistant.plugin.codecharta.impl.json;

import java.util.Map;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Builder
@Getter
@ToString
public class Edge {

    private String fromNodeName;

    private String toNodeName;

    private Map<String, Number> attributes;

}
