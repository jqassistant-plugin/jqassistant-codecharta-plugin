package org.jqassistant.plugin.codecharta.impl.model;

import com.buschmais.xo.api.CompositeObject;
import com.buschmais.xo.api.annotation.Abstract;
import com.buschmais.xo.neo4j.api.annotation.Label;

@Abstract
@Label("CodeCharta")
public interface CodeChartaDescriptor extends CompositeObject {
}
