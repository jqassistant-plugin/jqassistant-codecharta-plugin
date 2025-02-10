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

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.jqassistant.plugin.codecharta.impl.model.CodeChartaReport;
import org.jqassistant.plugin.codecharta.impl.model.Node;

import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;
import static java.util.Collections.emptyMap;
import static org.jqassistant.plugin.codecharta.impl.model.Node.Type.FILE;
import static org.jqassistant.plugin.codecharta.impl.model.Node.Type.FOLDER;

@Slf4j
public class CodeChartaReportPlugin implements ReportPlugin {

    public static final String CC_REPORT_DIRECTORY = "codecharta";

    public static final String CC_FILE_EXTENSION = ".cc.json";

    public static final String COLUMN_ELEMENT = "element";
    public static final String COLUMN_METRICS = "metrics";
    public static final String COLUMN_PARENT = "parent";
    public static final String COLUMN_ELEMENT_LABEL = "elementLabel";

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
        Map<String, Map<String, Number>> metricsByElement = getMetricsByElement(result);

        SortedSet<String> roots = new TreeSet<>();
        Map<String, SortedSet<String>> tree = new HashMap<>();
        Map<String, String> labels = new HashMap<>();
        calculateTree(result, tree, roots, labels);

        List<Node> nodes = toNodes(roots, metricsByElement, tree, labels);

        writeReport(result, nodes);
    }

    private static Map<String, Map<String, Number>> getMetricsByElement(Result<? extends ExecutableRule> result) throws ReportException {
        Map<String, Map<String, Number>> metricsByElement = new HashMap<>();
        for (Row row : result.getRows()) {
            Map<String, Column<?>> columns = row.getColumns();
            Column<?> elementColumn = requireColumn(columns, COLUMN_ELEMENT);
            Column<Map<String, Number>> metricsColumn = requireColumn(columns, COLUMN_METRICS);
            metricsByElement.put(elementColumn.getLabel(), metricsColumn.getValue());
        }
        return metricsByElement;
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

    private static List<Node> toNodes(Collection<String> elements, Map<String, Map<String, Number>> metricsByFileDescriptor,
            Map<String, SortedSet<String>> tree, Map<String, String> labels) {
        List<Node> nodes = new ArrayList<>();
        if (elements != null) {
            for (String element : elements) {
                Map<String, Number> metrics = metricsByFileDescriptor.getOrDefault(element, emptyMap());
                List<Node> children = toNodes(tree.get(element), metricsByFileDescriptor, tree, labels);
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

    private void writeReport(Result<? extends ExecutableRule> result, List<Node> nodes) throws ReportException {
        CodeChartaReport codeChartaReport = CodeChartaReport.builder()
                .projectName("jQAssistant CodeCharta Report")
                .apiVersion("1.1")
                .nodes(nodes)
                .build();

        File reportDirectory = reportContext.getReportDirectory(CC_REPORT_DIRECTORY);
        String reportFileName = ReportHelper.escapeRuleId(result.getRule()) + CC_FILE_EXTENSION;
        File reportFile = new File(reportDirectory, reportFileName);
        try {
            objectMapper.writeValue(reportFile, codeChartaReport);
            reportContext.addReport("CodeCharta", result.getRule(), ReportContext.ReportType.LINK, reportFile.toURI()
                    .toURL());
        } catch (IOException e) {
            throw new ReportException("Cannot create CodeCharta report file " + reportFile, e);
        }
    }

}
