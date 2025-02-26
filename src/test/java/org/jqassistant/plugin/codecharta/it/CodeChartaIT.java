package org.jqassistant.plugin.codecharta.it;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Map;

import com.buschmais.jqassistant.core.rule.api.model.RuleException;
import com.buschmais.jqassistant.plugin.java.test.AbstractJavaPluginIT;
import com.buschmais.jqassistant.plugin.maven3.api.model.MavenProjectDirectoryDescriptor;

import org.apache.commons.io.IOUtils;
import org.jqassistant.plugin.codecharta.it.set.TestClass;
import org.jqassistant.plugin.codecharta.it.set.TestInterface;
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

        applyConcept("codecharta-java:TypeReport");

        verify("codecharta-java_TypeReport.cc.json");
    }

    @Test
    void mavenReport() throws RuleException, IOException, ClassNotFoundException {
        store.reset();
        store.beginTransaction();
        MavenProjectDirectoryDescriptor parent = store.create(MavenProjectDirectoryDescriptor.class);
        parent.setFullQualifiedName("org.jqassistant.plugin.codecharta.test:parent:1.0.0");

        MavenProjectDirectoryDescriptor p1 = store.create(MavenProjectDirectoryDescriptor.class);
        p1.setFullQualifiedName("org.jqassistant.plugin.codecharta.test:a1:1.0.0");
        p1.getCreatesArtifacts()
            .add(getArtifactDescriptor("a1"));
        parent.getModules()
            .add(p1);
        p1.setParent(parent);

        MavenProjectDirectoryDescriptor p2 = store.create(MavenProjectDirectoryDescriptor.class);
        p2.setFullQualifiedName("org.jqassistant.plugin.codecharta.test:a2:1.0.0");
        p2.getCreatesArtifacts()
            .add(getArtifactDescriptor("a2"));
        parent.getModules()
            .add(p2);
        p2.setParent(parent);
        store.commitTransaction();

        scanClasses("a1", TestInterface.class);
        scanClasses("a2", TestClass.class, TestClass.InnerClass.class, getInnerClass(TestClass.InnerClass.class, "1"));

        applyConcept("codecharta-java:MavenReport");

        verify("codecharta-java_MavenReport.cc.json");
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
