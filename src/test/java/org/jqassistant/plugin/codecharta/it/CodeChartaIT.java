package org.jqassistant.plugin.codecharta.it;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Map;

import com.buschmais.jqassistant.core.rule.api.model.RuleException;
import com.buschmais.jqassistant.plugin.java.test.AbstractJavaPluginIT;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static uk.org.webcompere.modelassert.json.JsonAssertions.assertJson;

class CodeChartaIT extends AbstractJavaPluginIT {

    private static final String REFERENCE_DATA_DIRECTORY = "/reference-data/";
    private static final File CC_REPORT_DIRECTORY = new File("target/jqassistant/report/codecharta");

    @Override
    protected Map<String, Object> getScannerProperties() {
        // Ignore ITs to allow comparism using reference files
        return Map.of("file.exclude", "**/*IT.class");
    }

    @Test
    void javaReport() throws RuleException, IOException {
        File classesDirectory = getClassesDirectory(CodeChartaIT.class);
        scanClassPathDirectory(classesDirectory);

        applyConcept("codecharta-java:Report");

        verify("codecharta-java_Report.cc.json");
    }

    private void verify(String reportFileName) throws IOException {
        File expectedReportFile = new File(CC_REPORT_DIRECTORY, reportFileName);
        assertThat(expectedReportFile).exists();
        URL resource = CodeChartaIT.class.getResource(REFERENCE_DATA_DIRECTORY + reportFileName);
        assertThat(resource).isNotNull();
        String reference = IOUtils.toString(resource, UTF_8);
        assertJson(expectedReportFile).isEqualTo(reference);
    }

}
