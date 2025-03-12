package org.jqassistant.plugin.codecharta.impl.model;

import com.buschmais.jqassistant.plugin.common.api.model.NamedDescriptor;
import com.buschmais.xo.neo4j.api.annotation.Label;

@Label("AggregationLevel")
public interface AggregationLevelDescriptor extends CodeChartaDescriptor, NamedDescriptor {
}
