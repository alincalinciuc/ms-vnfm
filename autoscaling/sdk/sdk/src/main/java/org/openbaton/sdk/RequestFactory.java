/*
 *
 *  * Copyright (c) 2015 Technische Universität Berlin
 *  *  Licensed under the Apache License, Version 2.0 (the "License");
 *  *  you may not use this file except in compliance with the License.
 *  *  You may obtain a copy of the License at
 *  *
 *  *         http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  *  Unless required by applicable law or agreed to in writing, software
 *  *  distributed under the License is distributed on an "AS IS" BASIS,
 *  *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  *  See the License for the specific language governing permissions and
 *  *  limitations under the License.
 *
 */

package org.openbaton.sdk;

import org.openbaton.sdk.api.rest.*;
import org.openbaton.sdk.api.util.PropertyReader;
import org.openbaton.sdk.api.util.AbstractRestAgent;

public class RequestFactory {

    private static final String SDK_PROPERTIES_FILE = "sdk.api.properties";
    private static final PropertyReader propertyReader = new PropertyReader(SDK_PROPERTIES_FILE);
    private static RequestFactory instance;
    // create the requester here, maybe shift this to a manager
    private static ConfigurationRestRequest configurationRequest = null;
    private static ImageRestAgent imageRequest = null;
    private static NetworkServiceDescriptorRestAgent networkServiceDescriptorRequest = null;
    private static NetworkServiceRecordRestAgent networkServiceRecordRequest = null;
    private static VimInstanceRestAgent vimInstanceRequest = null;
    private static VirtualLinkRestAgent virtualLinkRequest = null;
    private static VNFFGRestAgent vNFFGRequest = null;
    private static EventAgent eventAgent = null;
    private static String username;
    private static String password;
    private final String nfvoPort;
    private final String nfvoIp;
    private final String version;

    private RequestFactory(String username, String password, String nfvoIp, String nfvoPort, String version) {
        this.username = username;
        this.password = password;
        this.nfvoPort = nfvoPort;
        this.nfvoIp = nfvoIp;
        this.version = version;
    }

    public static RequestFactory getInstance(String username, String password, String nfvoIp, String nfvoPort, String version) {
        if (instance == null) {
            return new RequestFactory(username, password, nfvoIp, nfvoPort, version);
        } else
            return instance;
    }

    public ConfigurationRestRequest getConfigurationAgent() {
        if (configurationRequest == null) {
            configurationRequest = new ConfigurationRestRequest(username, password, propertyReader.getRestConfigurationUrl(),nfvoIp, nfvoPort, version);
        }
        return configurationRequest;
    }

    public ImageRestAgent getImageAgent() {
        if (imageRequest == null) {
            imageRequest = new ImageRestAgent(username, password, nfvoIp, nfvoPort, propertyReader.getRestImageUrl(), version);
        }
        return imageRequest;
    }

    public NetworkServiceDescriptorRestAgent getNetworkServiceDescriptorAgent() {
        if (networkServiceDescriptorRequest == null) {
            networkServiceDescriptorRequest = new NetworkServiceDescriptorRestAgent(username, password, nfvoIp, nfvoPort, propertyReader.getRestNetworkServiceDescriptorUrl(), version);
        }
        return networkServiceDescriptorRequest;
    }

    public NetworkServiceRecordRestAgent getNetworkServiceRecordAgent() {
        if (networkServiceRecordRequest == null) {
            networkServiceRecordRequest = new NetworkServiceRecordRestAgent(username, password, nfvoIp, nfvoPort, propertyReader.getRestNetworkServiceRecordUrl(), version);
        }
        return networkServiceRecordRequest;
    }

    public VimInstanceRestAgent getVimInstanceAgent() {
        if (vimInstanceRequest == null) {
            vimInstanceRequest = new VimInstanceRestAgent(username, password, nfvoIp, nfvoPort, propertyReader.getRestVimInstanceUrl(), version);
        }
        return vimInstanceRequest;
    }

    public VirtualLinkRestAgent getVirtualLinkAgent() {
        if (virtualLinkRequest == null) {
            virtualLinkRequest = new VirtualLinkRestAgent(username, password, nfvoIp, nfvoPort, propertyReader.getRestVirtualLinkUrl(), version);
        }
        return virtualLinkRequest;
    }

    public VNFFGRestAgent getVNFForwardingGraphAgent() {
        if (vNFFGRequest == null) {
            vNFFGRequest = new VNFFGRestAgent(username, password, nfvoIp, nfvoPort, propertyReader.getRestVNFFGUrl(), version);
        }
        return vNFFGRequest;
    }

    public EventAgent getEventAgent() {
        if (eventAgent == null) {
            eventAgent = new EventAgent(username, password, nfvoIp, nfvoPort, propertyReader.getEventUrl(), version);
        }
        return eventAgent;
    }

    public AbstractRestAgent getAbstractAgent(Class clazz, String path) {
        return new AbstractRestAgent(username, password, nfvoIp, nfvoPort, path, version, clazz);
    }
}
