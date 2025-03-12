package org.jqassistant.plugin.codecharta.impl.model;

import com.buschmais.jqassistant.core.store.api.model.Descriptor;
import com.buschmais.xo.neo4j.api.annotation.Relation;
import com.buschmais.xo.neo4j.api.annotation.Relation.Incoming;
import com.buschmais.xo.neo4j.api.annotation.Relation.Outgoing;

@Relation("HAS_EDGE_METRICS")
public interface EdgeMetricsDescriptor extends Descriptor {

    @Outgoing
    NodeMetricsDescriptor getFrom();

    @Incoming
    NodeMetricsDescriptor getTo();
}
