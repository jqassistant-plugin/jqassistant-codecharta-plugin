package org.jqassistant.plugin.codecharta;

import java.io.File;

import com.buschmais.jqassistant.core.rule.api.model.RuleException;
import com.buschmais.jqassistant.core.scanner.api.DefaultScope;
import com.buschmais.jqassistant.core.test.plugin.AbstractPluginIT;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CodeChartaIT extends AbstractPluginIT {

    @Test
    void files() throws RuleException {
        File classesDirectory = getClassesDirectory(CodeChartaIT.class);
        getScanner().scan(classesDirectory, classesDirectory.getAbsolutePath(), DefaultScope.NONE);

        applyConcept("codecharta-test:Report");

        assertThat(new File("target/jqassistant/report/codecharta/codecharta-test_Report.cc.json")).exists();
    }

}
