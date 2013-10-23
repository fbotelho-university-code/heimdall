/**
*    Copyright 2012 Big Switch Networks, Inc.
*    Originally created by David Erickson, Stanford University
*
*    Licensed under the Apache License, Version 2.0 (the "License"); you may
*    not use this file except in compliance with the License. You may obtain
*    a copy of the License at
*
*         http://www.apache.org/licenses/LICENSE-2.0
*
*    Unless required by applicable law or agreed to in writing, software
*    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
*    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
*    License for the specific language governing permissions and limitations
*    under the License.
**/

package net.floodlightcontroller.devicemanager.internal;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.Set;

import net.floodlightcontroller.devicemanager.IDeviceService.DeviceField;
import smartkv.client.tables.CachedColumnTable;
import smartkv.client.tables.ColumnObject;
import smartkv.client.tables.ICachedKeyValueTable;
import smartkv.client.tables.TableBuilder;
import smartkv.client.tables.VersionedValue;
import smartkv.client.util.Serializer;
import smartkv.client.workloads.WorkloadLoggerTable;

/**
 * An index that maps key fields of an entity uniquely to a device key
 */
public class DeviceUniqueIndex extends DeviceIndex {
    
	/**
     * The index
     */
    private final ICachedKeyValueTable<IndexedEntity, Long> index;
    
    /**
     * Construct a new device index using the provided key fields
     * @param keyFields the key fields to use
     */
    public DeviceUniqueIndex(EnumSet<DeviceField> keyFields, ColumnObject c) {
        super(keyFields);
        
        index = CachedColumnTable.startCache(WorkloadLoggerTable.withSingletonLogger(new TableBuilder<IndexedEntity,Long>().setTableName("DEVICE_UNIQUE_INDEX")
     		   .setKeySerializer(IndexedEntity.SERIALIZER)
     		   .setValueSerializer(Serializer.LONG).setCid(0)
     		   .setCrossReferenceTable("DMAP") 
     		   .setCrossReferenceColumnSerializer(c)
     		   ));
    }
    
    // ***********
    // DeviceIndex
    // ***********

    @Override
    public Iterator<Long> queryByEntity(Entity entity) {
        final Long deviceKey = findByEntity(entity);
        if (deviceKey != null)
            return Collections.<Long>singleton(deviceKey).iterator();

        return Collections.<Long>emptySet().iterator();
    }

    @Override
    public Iterator<Long> getAll() {
        return index.values().iterator();
    }

    @Override
    public boolean updateIndex(Device device, Long deviceKey) {
        for (Entity e : device.getEntities()) {
            IndexedEntity ie = new IndexedEntity(keyFields, e);
            if (!ie.hasNonNullKeys()) continue;

            Long ret = index.putIfAbsent(ie, deviceKey);
            if (ret != null && !ret.equals(deviceKey)) {
                // If the return value is non-null, then fail the insert
                // (this implies that a device using this entity has
                // already been created in another thread).
                return false;
            }
        }
        return true;
    }
    
    @Override
    public void updateIndex(Entity entity, Long deviceKey) {
        IndexedEntity ie = new IndexedEntity(keyFields, entity);
        if (!ie.hasNonNullKeys()) return;
        index.put(ie, deviceKey);
    }

    @Override
    public void removeEntity(Entity entity) {
        IndexedEntity ie = new IndexedEntity(keyFields, entity);
        index.remove(ie);
    }

    @Override
    public void removeEntity(Entity entity, Long deviceKey) {
        IndexedEntity ie = new IndexedEntity(keyFields, entity);
        index.remove(ie, deviceKey);
    }

    // **************
    // Public Methods
    // **************

    /**
     * Look up a {@link Device} based on the provided {@link Entity}.
     * @param entity the entity to search for
     * @return The key for the {@link Device} object if found
     */
    public Long findByEntity(Entity entity) {
        IndexedEntity ie = new IndexedEntity(keyFields, entity);
        Long deviceKey = index.get(ie);
        if (deviceKey == null)
            return null;
        return deviceKey;
    }
    
    /**
     * Look up a {@link Device} based on the provided {@link Entity}.
     * @param entity the entity to search for
     * @return The key for the {@link Device} object if found
     */
    public VersionedValue<Object> findDeviceByEntity(Entity entity, long ts ) {
        IndexedEntity ie = new IndexedEntity(keyFields, entity);
        
        VersionedValue<Object> device = index.getVersionedValueByReference(ie, ts);
        if (device == null)
            return null;
        return device;
    }
    
    public VersionedValue<Object> findDeviceByEntity(Entity entity, long ts , Set<String> strings) {
        IndexedEntity ie = new IndexedEntity(keyFields, entity);
        VersionedValue<Object> device = index.getColumnsByReference(ie, strings,ts);
        if (device == null)
            return null;
        return device;
    }
    
    public VersionedValue<Object> findDeviceByEntity(Entity entity) {
    	return findDeviceByEntity(entity, 0); 
    }
    
}
