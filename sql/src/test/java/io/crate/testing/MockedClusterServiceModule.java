/*
 * Licensed to CRATE Technology GmbH ("Crate") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.  Crate licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial agreement.
 */

package io.crate.testing;

import io.crate.executor.transport.TransportActionProvider;
import org.elasticsearch.action.admin.indices.template.put.TransportPutIndexTemplateAction;
import org.elasticsearch.cluster.ClusterService;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.metadata.IndexTemplateMetaData;
import org.elasticsearch.cluster.metadata.MetaData;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.cluster.node.DiscoveryNodes;
import org.elasticsearch.common.collect.ImmutableOpenMap;
import org.elasticsearch.common.inject.AbstractModule;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.discovery.Discovery;
import org.elasticsearch.monitor.os.OsService;
import org.elasticsearch.monitor.os.OsStats;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MockedClusterServiceModule extends AbstractModule {
    @Override
    protected void configure() {
        ClusterService clusterService = mock(ClusterService.class);
        ClusterState state = mock(ClusterState.class);
        MetaData metaData = mock(MetaData.class);
        when(metaData.settings()).thenReturn(Settings.EMPTY);
        when(metaData.persistentSettings()).thenReturn(Settings.EMPTY);
        when(metaData.transientSettings()).thenReturn(Settings.EMPTY);
        when(metaData.concreteAllOpenIndices()).thenReturn(new String[0]);
        when(metaData.getTemplates()).thenReturn(ImmutableOpenMap.<String, IndexTemplateMetaData>of());
        when(metaData.templates()).thenReturn(ImmutableOpenMap.<String, IndexTemplateMetaData>of());
        when(state.metaData()).thenReturn(metaData);
        when(clusterService.state()).thenReturn(state);
        bind(ClusterService.class).toInstance(clusterService);
        bind(Settings.class).toInstance(Settings.EMPTY);
        OsService osService = mock(OsService.class);
        OsStats osStats = mock(OsStats.class);
        when(osService.stats()).thenReturn(osStats);
        bind(OsService.class).toInstance(osService);
        Discovery discovery = mock(Discovery.class);
        bind(Discovery.class).toInstance(discovery);

        DiscoveryNodes discoveryNodes = mock(DiscoveryNodes.class);
        when(discoveryNodes.localNodeId()).thenReturn("node-id-1");
        when(state.nodes()).thenReturn(discoveryNodes);
        when(clusterService.state()).thenReturn(state);

        ImmutableOpenMap.Builder<String, DiscoveryNode> dataNodesbuilder = ImmutableOpenMap.builder();
        dataNodesbuilder.put("dataNode1", mock(DiscoveryNode.class));
        dataNodesbuilder.put("dataNode2", mock(DiscoveryNode.class));
        dataNodesbuilder.put("dataNode3", mock(DiscoveryNode.class));
        when(discoveryNodes.dataNodes()).thenReturn(dataNodesbuilder.build());

        DiscoveryNode node = mock(DiscoveryNode.class);
        when(discovery.localNode()).thenReturn(node);
        when(node.getId()).thenReturn("node-id-1");
        when(node.id()).thenReturn("node-id-1");
        when(node.getName()).thenReturn("node 1");
        when(clusterService.localNode()).thenReturn(node);

        bind(TransportActionProvider.class).toInstance(mock(TransportActionProvider.class));

        bind(TransportPutIndexTemplateAction.class).toInstance(mock(TransportPutIndexTemplateAction.class));

        configureClusterService(clusterService);
        configureClusterState(state);
        configureMetaData(metaData);
    }

    protected void configureClusterService(ClusterService clusterService) {}

    protected void configureClusterState(ClusterState clusterState) {}

    protected void configureMetaData(MetaData metaData) {}
}
