package org.jqassistant.plugin.codecharta.impl.model;

import com.buschmais.xo.neo4j.api.annotation.Label;
import com.buschmais.xo.neo4j.api.annotation.Relation;
import com.buschmais.xo.neo4j.api.annotation.Relation.Incoming;
import com.buschmais.xo.neo4j.api.annotation.Relation.Outgoing;

import java.util.List;

@Label("Node")
public interface NodeMetricsDescriptor extends MetricsDescriptor {

    @Relation("FOR_AGGREGATION_LEVEL")
    AggregationLevelDescriptor getAggregationLevel();

    @Incoming
    List<EdgeMetricsDescriptor> getEdgeMetricsFromNodeMetrics();

    @Outgoing
    List<EdgeMetricsDescriptor> getEdgeMetricsToNodeMetrics();
}
