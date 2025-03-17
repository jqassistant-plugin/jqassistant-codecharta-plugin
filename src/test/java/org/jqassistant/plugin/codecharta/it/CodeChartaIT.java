package org.jqassistant.plugin.codecharta.it;

import com.buschmais.jqassistant.core.rule.api.model.RuleException;
import com.buschmais.jqassistant.plugin.common.api.model.DependsOnDescriptor;
import com.buschmais.jqassistant.plugin.java.api.model.JavaClassesDirectoryDescriptor;
import com.buschmais.jqassistant.plugin.java.test.AbstractJavaPluginIT;
import com.buschmais.jqassistant.plugin.maven3.api.model.MavenProjectDirectoryDescriptor;

import de.kontext_e.jqassistant.plugin.git.store.descriptor.GitAuthorDescriptor;
import de.kontext_e.jqassistant.plugin.git.store.descriptor.GitCommitDescriptor;
import de.kontext_e.jqassistant.plugin.git.store.descriptor.GitFileDescriptor;
import de.kontext_e.jqassistant.plugin.git.store.descriptor.change.GitChangeDescriptor;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Map;
import org.apache.commons.io.IOUtils;
import org.jqassistant.plugin.codecharta.it.set.TestClass;
import org.jqassistant.plugin.codecharta.it.set.TestInterface;
import org.junit.jupiter.api.BeforeEach;
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

    @BeforeEach
    void prepareGitHistory() {
        store.beginTransaction();
        GitAuthorDescriptor author1 = store.create(GitAuthorDescriptor.class);
        GitAuthorDescriptor author2 = store.create(GitAuthorDescriptor.class);
        Class<TestInterface> testInterfaceClass = TestInterface.class;
        GitFileDescriptor testInterface = createGitFile(testInterfaceClass);
        GitFileDescriptor testClass = createGitFile(TestClass.class);
        createCommit(author1, testInterface, testClass);
        createCommit(author2, testClass);
        store.commitTransaction();
    }

    @Test
    void typeReport() throws RuleException, IOException {
        File classesDirectory = getClassesDirectory(CodeChartaIT.class);
        scanClassPathDirectory(classesDirectory);

        applyConcept("codecharta-java:TypeReport");

        verify("codecharta-java_TypeReport.cc.json");
    }

    @Test
    void customReport() throws RuleException, IOException {
        File classesDirectory = getClassesDirectory(CodeChartaIT.class);
        scanClassPathDirectory(classesDirectory);

        applyConcept("codecharta-test:CustomReport");

        verify("codecharta-test_CustomReport.cc.json");
    }

    @Test
    void mavenProjectReport() throws RuleException, IOException, ClassNotFoundException {
        store.beginTransaction();
        MavenProjectDirectoryDescriptor parent = store.create(MavenProjectDirectoryDescriptor.class);
        parent.setFullQualifiedName("org.jqassistant.plugin.codecharta.test:parent:1.0.0");

        MavenProjectDirectoryDescriptor p1 = store.create(MavenProjectDirectoryDescriptor.class);
        p1.setFullQualifiedName("org.jqassistant.plugin.codecharta.test:a1:1.0.0");
        JavaClassesDirectoryDescriptor a1 = getArtifactDescriptor("a1");
        p1.getCreatesArtifacts()
            .add(a1);
        parent.getModules()
            .add(p1);

        MavenProjectDirectoryDescriptor p2 = store.create(MavenProjectDirectoryDescriptor.class);
        p2.setFullQualifiedName("org.jqassistant.plugin.codecharta.test:a2:1.0.0");
        JavaClassesDirectoryDescriptor a2 = getArtifactDescriptor("a2");
        p2.getCreatesArtifacts()
            .add(a2);
        parent.getModules()
            .add(p2);

        store.create(a2, DependsOnDescriptor.class, a1);
        store.commitTransaction();

        scanClasses("a1", TestInterface.class);
        scanClasses("a2", TestClass.class, TestClass.InnerClass.class, getInnerClass(TestClass.InnerClass.class, "1"));

        applyConcept("codecharta-java:MavenProjectReport");

        verify("codecharta-java_MavenProjectReport.cc.json");
    }

    private void verify(String reportFileName) throws IOException {
        File expectedReportFile = new File(CC_REPORT_DIRECTORY, reportFileName);
        assertThat(expectedReportFile).exists();
        URL resource = CodeChartaIT.class.getResource(REFERENCE_DATA_DIRECTORY + reportFileName);
        assertThat(resource).isNotNull();
        String reference = IOUtils.toString(resource, UTF_8);
        assertJson(expectedReportFile).isEqualTo(reference);
    }

    private void createCommit(GitAuthorDescriptor author, GitFileDescriptor... files) {
        GitCommitDescriptor commit = store.create(GitCommitDescriptor.class);
        for (GitFileDescriptor file : files) {
            commit.getChanges()
                .add(createChange(file));
        }
        author.getCommits()
            .add(commit);
    }

    private GitFileDescriptor createGitFile(Class<?> type) {
        GitFileDescriptor gitFile = store.create(GitFileDescriptor.class);
        gitFile.setRelativePath("src/main/java/" + type.getName()
            .replace('.', '/') + ".java");
        return gitFile;
    }

    private GitChangeDescriptor createChange(GitFileDescriptor testInterface) {
        GitChangeDescriptor change1 = store.create(GitChangeDescriptor.class);
        change1.setModifies(testInterface);
        return change1;
    }
}
