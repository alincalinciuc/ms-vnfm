package org.openbaton.vnfm.core;

import org.openbaton.catalogue.mano.record.VirtualNetworkFunctionRecord;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.HashMap;

@Service
@Scope
public class VnfrMonitorVNFM {

    private HashMap<String, VirtualNetworkFunctionRecord> virtualNetworkFunctionRecords;

    @PostConstruct
    public synchronized void init() {
        this.virtualNetworkFunctionRecords = new HashMap<>();
    }

    public synchronized VirtualNetworkFunctionRecord getVNFR(String id) {
        return virtualNetworkFunctionRecords.get(id);
    }

    public synchronized void addVNFR(VirtualNetworkFunctionRecord virtualNetworkFunctionRecord) {
        virtualNetworkFunctionRecords.put(virtualNetworkFunctionRecord.getId(), virtualNetworkFunctionRecord);
    }

    public synchronized void removeVNFR(String id) {
        virtualNetworkFunctionRecords.remove(id);
    }

}
