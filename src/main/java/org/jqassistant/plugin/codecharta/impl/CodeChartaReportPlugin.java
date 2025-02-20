package org.jqassistant.plugin.codecharta.impl;

import java.io.File;
import java.io.IOException;
import java.util.*;

import com.buschmais.jqassistant.core.report.api.ReportContext;
import com.buschmais.jqassistant.core.report.api.ReportException;
import com.buschmais.jqassistant.core.report.api.ReportHelper;
import com.buschmais.jqassistant.core.report.api.ReportPlugin;
import com.buschmais.jqassistant.core.report.api.model.Column;
import com.buschmais.jqassistant.core.report.api.model.Result;
import com.buschmais.jqassistant.core.report.api.model.Row;
import com.buschmais.jqassistant.core.rule.api.model.ExecutableRule;
import com.buschmais.xo.neo4j.api.model.Neo4jNode;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.jqassistant.plugin.codecharta.impl.json.CodeChartaReport;
import org.jqassistant.plugin.codecharta.impl.json.Edge;
import org.jqassistant.plugin.codecharta.impl.json.Node;
import org.jqassistant.plugin.codecharta.impl.model.MetricsDescriptor;

import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;
import static java.util.Collections.*;
import static org.jqassistant.plugin.codecharta.impl.json.Node.Type.FILE;
import static org.jqassistant.plugin.codecharta.impl.json.Node.Type.FOLDER;

@Slf4j
public class CodeChartaReportPlugin implements ReportPlugin {

    /**
     * The label of the report to be used, e.g. in generated Asciioc documents.
     */
    public static final String REPORT_LABEL = "CodeCharta";

    public static final String COLUMN_ELEMENT = "Element";
    public static final String COLUMN_NODE_METRICS = "NodeMetrics";
    public static final String COLUMN_EDGE_METRICS = "EdgeMetrics";
    public static final String COLUMN_PARENT = "Parent";
    public static final String COLUMN_ELEMENT_LABEL = "ElementLabel";

    public static final String CC_REPORT_DIRECTORY = "codecharta";
    public static final String CC_FILE_EXTENSION = ".cc.json";

    public static final String CC_API_VERSION = "1.1";
    public static final String CC_PROJECT_NAME = "jQAssistant CodeCharta Report";

    private ObjectMapper objectMapper;

    private ReportContext reportContext;

    @Override
    public void initialize() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(INDENT_OUTPUT);
    }

    @Override
    public void configure(ReportContext reportContext, Map<String, Object> properties) {
        log.info("Configuring CodeCharta Report");
        this.reportContext = reportContext;
    }

    @Override
    public void setResult(Result<? extends ExecutableRule> result) throws ReportException {
        log.info("Evaluating result");
        Map<String, SortedMap<String, Number>> nodeMetrics = getNodeMetrics(result);
        Map<String, Map<String, SortedMap<String, Number>>> edgeMetrics = getEdgeMetrics(result);

        SortedSet<String> roots = new TreeSet<>();
        Map<String, SortedSet<String>> tree = new HashMap<>();
        Map<String, String> labels = new HashMap<>();
        calculateTree(result, tree, roots, labels);
        SortedMap<String, String> paths = calculatePaths(roots, tree, labels);

        List<Node> nodes = toNodes(roots, nodeMetrics, tree, labels);
        List<Edge> edges = toEdges(edgeMetrics, paths);

        writeReport(result.getRule(), nodes, edges);
    }

    private static SortedMap<String, String> calculatePaths(SortedSet<String> roots, Map<String, SortedSet<String>> tree, Map<String, String> labels) {
        TreeMap<String, String> paths = new TreeMap<>();
        for (String nodeKey : roots) {
            calculatePath("", nodeKey, paths, tree, labels);
        }
        return paths;
    }

    private static void calculatePath(String parentPath, String nodeKey, TreeMap<String, String> paths, Map<String, SortedSet<String>> tree,
        Map<String, String> labels) {
        String nodePath = parentPath + '/' + labels.get(nodeKey);
        paths.put(nodeKey, nodePath);
        SortedSet<String> children = tree.getOrDefault(nodeKey, emptySortedSet());
        for (String childKey : children) {
            calculatePath(nodePath, childKey, paths, tree, labels);
        }
    }

    private static Map<String, SortedMap<String, Number>> getNodeMetrics(Result<? extends ExecutableRule> result) throws ReportException {
        Map<String, SortedMap<String, Number>> nodeMetrics = new HashMap<>();
        for (Row row : result.getRows()) {
            Map<String, Column<?>> columns = row.getColumns();
            Column<?> elementColumn = requireColumn(columns, COLUMN_ELEMENT);
            String elementKey = elementColumn.getLabel();
            Column<Map<String, Number>> nodeMetricsColumn = requireColumn(columns, COLUMN_NODE_METRICS);
            Object value = nodeMetricsColumn.getValue();
            if (value != null) {
                nodeMetrics.put(elementKey, getMetricsFromValue(value));
            }
        }
        return nodeMetrics;
    }

    private static Map<String, Map<String, SortedMap<String, Number>>> getEdgeMetrics(Result<? extends ExecutableRule> result) throws ReportException {
        Map<String, Map<String, SortedMap<String, Number>>> edgeMetrics = new HashMap<>();
        for (Row row : result.getRows()) {
            Map<String, Column<?>> columns = row.getColumns();
            Column<?> nodeColumn = requireColumn(columns, COLUMN_ELEMENT);
            String nodeKey = nodeColumn.getLabel();
            Column<List<Map<String, Object>>> edgeMetricsColumn = requireColumn(columns, COLUMN_EDGE_METRICS);
            List<Map<String, Object>> value = edgeMetricsColumn.getValue();
            for (Map<String, Object> edgeMetricsValue : value) {
                Object toNode = edgeMetricsValue.get("to");
                if (toNode != null) {
                    Object metrics = edgeMetricsValue.get("metrics");
                    String toNodeKey = ReportHelper.getLabel(toNode);
                    edgeMetrics.computeIfAbsent(nodeKey, key -> new TreeMap<>())
                        .put(toNodeKey, getMetricsFromValue(metrics));
                }
            }
        }
        return edgeMetrics;
    }

    private static SortedMap<String, Number> getMetricsFromValue(Object value) throws ReportException {
        SortedMap<String, Number> metric;
        if (value instanceof Map) {
            metric = getMetricsFromValue((Map<?, ?>) value);
        } else if (value instanceof MetricsDescriptor) {
            MetricsDescriptor metricsDescriptor = (MetricsDescriptor) value;
            Neo4jNode<?, ?, ?, ?> neo4jNode = metricsDescriptor.getDelegate();
            metric = getMetricsFromValue(neo4jNode.getProperties());
        } else {
            throw new ReportException("Cannot extract value from " + COLUMN_NODE_METRICS + " column:" + value);
        }
        return metric;
    }

    private static SortedMap<String, Number> getMetricsFromValue(Map<?, ?> container) {
        TreeMap<String, Number> metrics = new TreeMap<>();
        container.entrySet()
            .stream()
            .filter(entry -> entry.getKey() instanceof String && entry.getValue() instanceof Number)
            .forEach(entry -> metrics.put((String) entry.getKey(), (Number) entry.getValue()));
        return metrics;
    }

    private static void calculateTree(Result<? extends ExecutableRule> result, Map<String, SortedSet<String>> tree, SortedSet<String> roots,
        Map<String, String> labels) throws ReportException {
        for (Row row : result.getRows()) {
            Column<?> elementColumn = requireColumn(row.getColumns(), COLUMN_ELEMENT);
            Column<?> parentColumn = requireColumn(row.getColumns(), COLUMN_PARENT);
            String nodeKey = elementColumn.getLabel();
            String parent = parentColumn.getLabel();
            if (parentColumn.getValue() != null) {
                tree.computeIfAbsent(parent, key -> new TreeSet<>())
                    .add(nodeKey);
            } else {
                roots.add(nodeKey);
            }
            String label = row.getColumns()
                .get(COLUMN_ELEMENT_LABEL)
                .getLabel();
            labels.put(nodeKey, label != null ? label : nodeKey);
        }
    }

    private static <T> Column<T> requireColumn(Map<String, Column<?>> columns, String columnName) throws ReportException {
        Column<T> column = (Column<T>) columns.get(columnName);
        if (column == null) {
            throw new ReportException("Result does not contain required column " + columnName);
        }
        return column;
    }

    /**
     * Convert a collection of elements to CodeCharta {@link Node}s with metrics and children
     *
     * @param nodeKeys
     *     The elements.
     * @param nodeMetrics
     *     A {@link Map} containing elements as keys and associated metrics as values (represented by a {@link Map}).
     * @param tree
     *     A {@link Map} containing elements as keys their children as values.
     * @param labels
     *     A {@link Map} containing elements as keys and their labels as values (optional).
     * @return The CodeCharta {@link Node}s as tree structure.
     */
    private static List<Node> toNodes(Collection<String> nodeKeys, Map<String, SortedMap<String, Number>> nodeMetrics, Map<String, SortedSet<String>> tree,
        Map<String, String> labels) {
        List<Node> nodes = new ArrayList<>();
        if (nodeKeys != null) {
            for (String nodeKey : nodeKeys) {
                List<Node> children = toNodes(tree.get(nodeKey), nodeMetrics, tree, labels);
                Map<String, Number> metrics = nodeMetrics.getOrDefault(nodeKey, emptySortedMap());
                nodes.add(Node.builder()
                    .name(labels.get(nodeKey))
                    .type(tree.containsKey(nodeKey) ? FOLDER : FILE)
                    .attributes(metrics)
                    .children(children)
                    .build());
            }
        }
        return nodes;
    }

    private static List<Edge> toEdges(Map<String, Map<String, SortedMap<String, Number>>> edgeMetrics, SortedMap<String, String> paths) {
        List<Edge> edges = new ArrayList<>();
        for (Map.Entry<String, Map<String, SortedMap<String, Number>>> fromNodeEntry : edgeMetrics.entrySet()) {
            String fromNodeKey = fromNodeEntry.getKey();
            for (Map.Entry<String, SortedMap<String, Number>> toNodeEntry : fromNodeEntry.getValue()
                .entrySet()) {
                String toNodeKey = toNodeEntry.getKey();
                SortedMap<String, Number> metrics = toNodeEntry.getValue();
                Edge edge = Edge.builder()
                    .fromNodeName(paths.get(fromNodeKey))
                    .toNodeName(paths.get(toNodeKey))
                    .attributes(metrics)
                    .build();
                edges.add(edge);
            }
        }
        return edges;
    }

    /**
     * Write the CodeCharta {@link Node}s to a json.cc report file.
     *
     * @param rule
     *     The rule which has been used to create the report.
     * @param nodes
     *     The {@link Node}s.
     * @param edges
     * @throws ReportException
     *     If writing fails.
     */
    private void writeReport(ExecutableRule<?> rule, List<Node> nodes, List<Edge> edges) throws ReportException {
        Node rootNode = Node.builder()
            .name("")
            .type(FOLDER)
            .attributes(emptyMap())
            .children(nodes)
            .build();

        CodeChartaReport codeChartaReport = CodeChartaReport.builder()
            .projectName(CC_PROJECT_NAME)
            .apiVersion(CC_API_VERSION)
            .nodes(List.of(rootNode))
            .edges(edges)
            .build();

        File reportDirectory = reportContext.getReportDirectory(CC_REPORT_DIRECTORY);
        String reportFileName = ReportHelper.escapeRuleId(rule) + CC_FILE_EXTENSION;
        File reportFile = new File(reportDirectory, reportFileName);
        try {
            objectMapper.writeValue(reportFile, codeChartaReport);
            reportContext.addReport(REPORT_LABEL, rule, ReportContext.ReportType.LINK, reportFile.toURI()
                .toURL());
        } catch (IOException e) {
            throw new ReportException("Cannot create CodeCharta report file " + reportFile, e);
        }
    }

}
