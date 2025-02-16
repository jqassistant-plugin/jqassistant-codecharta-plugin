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
import org.jqassistant.plugin.codecharta.impl.json.Node;
import org.jqassistant.plugin.codecharta.impl.model.MetricsDescriptor;

import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySortedMap;
import static org.jqassistant.plugin.codecharta.impl.json.Node.Type.FILE;
import static org.jqassistant.plugin.codecharta.impl.json.Node.Type.FOLDER;

@Slf4j
public class CodeChartaReportPlugin implements ReportPlugin {

    /**
     * The label of the report to be used, e.g. in generated Asciioc documents.
     */
    public static final String REPORT_LABEL = "CodeCharta";

    public static final String COLUMN_ELEMENT = "Element";
    public static final String COLUMN_METRICS = "Metrics";
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
        Map<String, SortedMap<String, Number>> metricsByElement = getMetricsByElement(result);

        SortedSet<String> roots = new TreeSet<>();
        Map<String, SortedSet<String>> tree = new HashMap<>();
        Map<String, String> labels = new HashMap<>();
        calculateTree(result, tree, roots, labels);

        List<Node> nodes = toNodes(roots, metricsByElement, tree, labels);

        writeReport(result.getRule(), nodes);
    }

    private static Map<String, SortedMap<String, Number>> getMetricsByElement(Result<? extends ExecutableRule> result) throws ReportException {
        Map<String, SortedMap<String, Number>> metricsByElement = new HashMap<>();
        for (Row row : result.getRows()) {
            Map<String, Column<?>> columns = row.getColumns();
            Column<?> elementColumn = requireColumn(columns, COLUMN_ELEMENT);
            Column<Map<String, Number>> metricsColumn = requireColumn(columns, COLUMN_METRICS);
            Object value = metricsColumn.getValue();
            if (value instanceof Map) {
                metricsByElement.put(elementColumn.getLabel(), getMetricsFromColumValue((Map<?, ?>) value));
            } else if (value instanceof MetricsDescriptor) {
                MetricsDescriptor metricsDescriptor = (MetricsDescriptor) value;
                Neo4jNode<?, ?, ?, ?> neo4jNode = metricsDescriptor.getDelegate();
                metricsByElement.put(elementColumn.getLabel(), getMetricsFromColumValue(neo4jNode.getProperties()));
            }
        }
        return metricsByElement;
    }

    private static SortedMap<String, Number> getMetricsFromColumValue(Map<?, ?> container) {
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
            String element = elementColumn.getLabel();
            String parent = parentColumn.getLabel();
            if (parentColumn.getValue() != null) {
                tree.computeIfAbsent(parent, key -> new TreeSet<>())
                    .add(element);
            } else {
                roots.add(element);
            }
            Column<?> labelColumn = row.getColumns()
                .get(COLUMN_ELEMENT_LABEL);
            if (labelColumn.getValue() != null) {
                labels.put(element, labelColumn.getLabel());
            }
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
     * @param elements
     *     The elements.
     * @param metricsByElement
     *     A {@link Map} containing elements as keys and associated metrics as values (represented by a {@link Map}).
     * @param tree
     *     A {@link Map} containing elements as keys their children as values.
     * @param labels
     *     A {@link Map} containing elements as keys and their labels as values (optional).
     * @return The CodeCharta {@link Node}s as tree structure.
     */
    private static List<Node> toNodes(Collection<String> elements, Map<String, SortedMap<String, Number>> metricsByElement, Map<String, SortedSet<String>> tree,
        Map<String, String> labels) {
        List<Node> nodes = new ArrayList<>();
        if (elements != null) {
            for (String element : elements) {
                Map<String, Number> metrics = metricsByElement.getOrDefault(element, emptySortedMap());
                List<Node> children = toNodes(tree.get(element), metricsByElement, tree, labels);
                nodes.add(Node.builder()
                    .name(labels.getOrDefault(element, element))
                    .type(tree.containsKey(element) ? FOLDER : FILE)
                    .attributes(metrics)
                    .children(children)
                    .build());
            }
        }
        return nodes;
    }

    /**
     * Write the CodeCharta {@link Node}s to a json.cc report file.
     *
     * @param rule
     *     The rule which has been used to create the report.
     * @param nodes
     *     The {@link Node}s.
     * @throws ReportException
     *     If writing fails.
     */
    private void writeReport(ExecutableRule<?> rule, List<Node> nodes) throws ReportException {
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
