package org.jqassistant.plugin.codecharta;

import java.io.File;

import com.buschmais.jqassistant.core.rule.api.model.RuleException;
import com.buschmais.jqassistant.plugin.java.test.AbstractJavaPluginIT;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CodeChartaIT extends AbstractJavaPluginIT {

    @Test
    void javaReport() throws RuleException {
        File classesDirectory = getClassesDirectory(CodeChartaIT.class);
        scanClassPathDirectory(classesDirectory);

        applyConcept("codecharta-java:Report");

        assertThat(new File("target/jqassistant/report/codecharta/codecharta-java_Report.cc.json")).exists();
    }

}
