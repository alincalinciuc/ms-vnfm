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

package org.openbaton.sdk.api.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
* OpenBaton SDK Property Reader. Provides URL information from the properties file
*/
public class PropertyReader {

    private Properties sdkProperties;

    /**
     * Creates a property reader that deserializes the property file from a jar
     *
     * @param sdkPropertiesPath
     */
    public PropertyReader(final String sdkPropertiesPath) {
        sdkProperties = readProperties(sdkPropertiesPath);
    }

    /**
     *
     * @param propertiesPath
     * @return Properties
     */
    private Properties readProperties(final String propertiesPath) {
        Properties properties = null;
        InputStream inputStream = null;
        try {
            // load the jar's properties files
            inputStream = PropertyReader.class.getClassLoader().getResourceAsStream(propertiesPath);
            // if there is an inputstream, execute the following
            properties = new Properties();
            properties.load(inputStream);
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return properties;
    }

    /**
     * Gets the api url
     *
     * @return the api url property
     */
    public String getApiUrl() {
        return sdkProperties.getProperty("apiUrl");
    }

    /**
     * Gets the configuration Url path
     *
     * @return the configuration Url path property
     */
    public String getRestConfigurationUrl() {
        return sdkProperties.getProperty("restConfigurationPath");
    }

    /**
     * Gets the image Url path
     *
     * @return the image Url path property
     */
    public String getRestImageUrl() {
        return sdkProperties.getProperty("restImagePath");
    }

    /**
     * Gets the networkservicedescriptor Url path
     *
     * @return the networkservicedescriptor Url path property
     */
    public String getRestNetworkServiceDescriptorUrl() {
        return sdkProperties.getProperty("restNetworkServiceDescriptorPath");
    }

    /**
     * Gets the networkservicerecord Url path
     *
     * @return the networkservicerecord Url path property
     */
    public String getRestNetworkServiceRecordUrl() {
        return sdkProperties.getProperty("restNetworkServiceRecordPath");
    }

    /**
     * Gets the viminstance Url path
     *
     * @return the viminstance Url path property
     */
    public String getRestVimInstanceUrl() {
        return sdkProperties.getProperty("restVimInstancePath");
    }

    /**
     * Gets the virtuallink Url path
     *
     * @return the virtuallink Url path property
     */
    public String getRestVirtualLinkUrl() {
        return sdkProperties.getProperty("restVirtualLinkPath");
    }

    /**
     * Gets the vnffg Url path
     *
     * @return the vnffg Url path property
     */
    public String getRestVNFFGUrl() {
        return sdkProperties.getProperty("restVNFFGPath");
    }

    public String getEventUrl() {
        return sdkProperties.getProperty("restEventPath");
    }
}