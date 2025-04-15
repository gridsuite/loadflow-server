package org.gridsuite.loadflow.server.service;

import com.powsybl.commons.extensions.Extension;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.NetworkListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class NetworkStoreListener implements NetworkListener {
    private static final Logger LOG = LoggerFactory.getLogger(NetworkStoreListener.class);

    private List<Modification> modificationList = new ArrayList<>();

    private record Modification(Identifiable<?> identifiable, String attribute, Object oldValue, Object newValue) { }

    @Override
    public void onCreation(Identifiable<?> identifiable) {
        modificationList.add(new Modification(identifiable, "CREATE", null, null));
        LOG.error("NetworkStoreListener onCreation".concat(identifiable.getId()));
    }

    @Override
    public void beforeRemoval(Identifiable<?> identifiable) {
        modificationList.add(new Modification(identifiable, "DELETE", null, null));
    }

    @Override
    public void afterRemoval(String id) {

    }

    @Override
    public void onUpdate(Identifiable<?> identifiable, String attribute, String variantId, Object oldValue, Object newValue) {
        modificationList.add(new Modification(identifiable, attribute, oldValue, newValue));
        LOG.error("NetworkStoreListener onUpdate".concat(identifiable.getId()));
    }

    @Override
    public void onExtensionCreation(Extension<?> extension) {

    }

    @Override
    public void onExtensionAfterRemoval(Identifiable<?> identifiable, String extensionName) {

    }

    @Override
    public void onExtensionBeforeRemoval(Extension<?> extension) {

    }

    @Override
    public void onExtensionUpdate(Extension<?> extendable, String attribute, String variantId, Object oldValue, Object newValue) {

    }

    @Override
    public void onPropertyAdded(Identifiable<?> identifiable, String key, Object newValue) {
        modificationList.add(new Modification(identifiable, key, null, newValue));
        LOG.error("NetworkStoreListener onPropertyAdded".concat(identifiable.getId()));
    }

    @Override
    public void onPropertyReplaced(Identifiable<?> identifiable, String key, Object oldValue, Object newValue) {
        LOG.error("NetworkStoreListener onPropertyReplaced".concat(identifiable.getId()));
    }

    @Override
    public void onPropertyRemoved(Identifiable<?> identifiable, String key, Object oldValue) {

    }

    @Override
    public void onVariantCreated(String sourceVariantId, String targetVariantId) {

    }

    @Override
    public void onVariantOverwritten(String sourceVariantId, String targetVariantId) {

    }

    @Override
    public void onVariantRemoved(String variantId) {

    }

    public void flush() {
        LOG.error("NetworkStoreListener flush");
    }
}
