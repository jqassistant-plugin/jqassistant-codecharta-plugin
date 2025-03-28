package org.jqassistant.plugin.codecharta.impl.json;

import java.util.List;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Builder
@Getter
@ToString
public class CodeChartaReport {

    private String projectName;
    private String apiVersion;
    private List<Node> nodes;
    private List<Edge> edges;
}
