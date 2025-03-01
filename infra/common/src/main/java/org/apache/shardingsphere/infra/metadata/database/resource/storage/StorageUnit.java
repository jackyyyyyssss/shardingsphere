/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.infra.metadata.database.resource.storage;

import lombok.Getter;
import org.apache.shardingsphere.infra.database.DatabaseTypeEngine;
import org.apache.shardingsphere.infra.database.core.connector.ConnectionProperties;
import org.apache.shardingsphere.infra.database.core.connector.ConnectionPropertiesParser;
import org.apache.shardingsphere.infra.database.core.spi.DatabaseTypedSPILoader;
import org.apache.shardingsphere.infra.database.core.type.DatabaseType;
import org.apache.shardingsphere.infra.datasource.pool.CatalogSwitchableDataSource;
import org.apache.shardingsphere.infra.datasource.pool.props.creator.DataSourcePoolPropertiesCreator;
import org.apache.shardingsphere.infra.datasource.pool.props.domain.DataSourcePoolProperties;
import org.apache.shardingsphere.infra.state.datasource.DataSourceStateManager;

import javax.sql.DataSource;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Storage unit.
 */
@Getter
public final class StorageUnit {
    
    private final DataSourcePoolProperties dataSourcePoolProperties;
    
    private final StorageUnitNodeMapper unitNodeMapper;
    
    private final DataSource dataSource;
    
    private final DatabaseType storageType;
    
    private final ConnectionProperties connectionProperties;
    
    public StorageUnit(final String databaseName, final Map<StorageNode, DataSource> storageNodeDataSources,
                       final DataSourcePoolProperties props, final StorageUnitNodeMapper unitNodeMapper) {
        this.dataSourcePoolProperties = props;
        this.unitNodeMapper = unitNodeMapper;
        dataSource = getStorageUnitDataSource(storageNodeDataSources, unitNodeMapper);
        Map<StorageNode, DataSource> enabledStorageNodeDataSources = getEnabledStorageNodeDataSources(databaseName, storageNodeDataSources);
        storageType = createStorageType(enabledStorageNodeDataSources, unitNodeMapper);
        connectionProperties = createConnectionProperties(enabledStorageNodeDataSources, unitNodeMapper, storageType).orElse(null);
    }
    
    private DataSource getStorageUnitDataSource(final Map<StorageNode, DataSource> storageNodeDataSources, final StorageUnitNodeMapper unitNodeMapper) {
        DataSource dataSource = storageNodeDataSources.get(unitNodeMapper.getStorageNode());
        return new CatalogSwitchableDataSource(dataSource, unitNodeMapper.getCatalog(), unitNodeMapper.getUrl());
    }
    
    private Map<StorageNode, DataSource> getEnabledStorageNodeDataSources(final String databaseName, final Map<StorageNode, DataSource> storageNodeDataSources) {
        Map<String, DataSource> toBeCheckedDataSources = new LinkedHashMap<>(storageNodeDataSources.size(), 1F);
        for (Entry<StorageNode, DataSource> entry : storageNodeDataSources.entrySet()) {
            toBeCheckedDataSources.put(entry.getKey().getName(), entry.getValue());
        }
        Map<String, DataSource> enabledDataSources = DataSourceStateManager.getInstance().getEnabledDataSources(databaseName, toBeCheckedDataSources);
        return storageNodeDataSources.entrySet().stream()
                .filter(entry -> enabledDataSources.containsKey(entry.getKey().getName())).collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    }
    
    private DatabaseType createStorageType(final Map<StorageNode, DataSource> enabledStorageNodeDataSources, final StorageUnitNodeMapper unitNodeMapper) {
        return DatabaseTypeEngine.getStorageType(enabledStorageNodeDataSources.containsKey(unitNodeMapper.getStorageNode())
                ? Collections.singleton(enabledStorageNodeDataSources.get(unitNodeMapper.getStorageNode()))
                : Collections.emptyList());
    }
    
    private Optional<ConnectionProperties> createConnectionProperties(final Map<StorageNode, DataSource> enabledStorageNodeDataSources,
                                                                      final StorageUnitNodeMapper unitNodeMapper, final DatabaseType storageType) {
        if (!enabledStorageNodeDataSources.containsKey(unitNodeMapper.getStorageNode())) {
            return Optional.empty();
        }
        Map<String, Object> standardProps = DataSourcePoolPropertiesCreator.create(
                enabledStorageNodeDataSources.get(unitNodeMapper.getStorageNode())).getConnectionPropertySynonyms().getStandardProperties();
        ConnectionPropertiesParser parser = DatabaseTypedSPILoader.getService(ConnectionPropertiesParser.class, storageType);
        return Optional.of(parser.parse(standardProps.get("url").toString(), standardProps.get("username").toString(), unitNodeMapper.getCatalog()));
    }
}
