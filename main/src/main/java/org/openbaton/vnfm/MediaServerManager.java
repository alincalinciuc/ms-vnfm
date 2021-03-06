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

package org.openbaton.vnfm;

import org.openbaton.autoscaling.core.management.ElasticityManagement;
import org.openbaton.catalogue.mano.descriptor.VNFComponent;
import org.openbaton.catalogue.mano.descriptor.VirtualDeploymentUnit;
import org.openbaton.catalogue.mano.record.Status;
import org.openbaton.catalogue.mano.record.VNFCInstance;
import org.openbaton.catalogue.mano.record.VNFRecordDependency;
import org.openbaton.catalogue.mano.record.VirtualNetworkFunctionRecord;
import org.openbaton.catalogue.nfvo.Action;
import org.openbaton.catalogue.nfvo.Location;
import org.openbaton.catalogue.nfvo.Server;
import org.openbaton.catalogue.nfvo.VimInstance;
import org.openbaton.catalogue.nfvo.messages.Interfaces.NFVMessage;
import org.openbaton.common.vnfm_sdk.amqp.AbstractVnfmSpringAmqp;
import org.openbaton.common.vnfm_sdk.utils.VnfmUtils;
import org.openbaton.exceptions.NotFoundException;
import org.openbaton.exceptions.VimDriverException;
import org.openbaton.exceptions.VimException;
import org.openbaton.plugin.utils.PluginStartup;
import org.openbaton.plugin.utils.RabbitPluginBroker;
import org.openbaton.sdk.NFVORequestor;
import org.openbaton.sdk.api.exception.SDKException;
import org.openbaton.vim.drivers.VimDriverCaller;
import org.openbaton.vnfm.catalogue.ManagedVNFR;
import org.openbaton.vnfm.configuration.ApplicationProperties;
import org.openbaton.vnfm.configuration.NfvoProperties;
import org.openbaton.vnfm.configuration.SpringProperties;
import org.openbaton.vnfm.configuration.VnfmProperties;
import org.openbaton.vnfm.core.api.MediaServerResourceManagement;
import org.openbaton.vnfm.core.interfaces.ApplicationManagement;
import org.openbaton.vnfm.core.interfaces.MediaServerManagement;
import org.openbaton.vnfm.repositories.ManagedVNFRRepository;
import org.openbaton.vnfm.utils.Utils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.orm.jpa.EntityScan;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.rmi.RemoteException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Created by lto on 27/05/15.
 */
@SpringBootApplication
@EntityScan("org.openbaton.vnfm.catalogue")
@ComponentScan({"org.openbaton.vnfm.api", "org.openbaton.autoscaling.api", "org.openbaton.autoscaling"})
@EnableJpaRepositories("org.openbaton.vnfm")
public class MediaServerManager extends AbstractVnfmSpringAmqp implements ApplicationListener<ContextClosedEvent> {

    @Autowired
    private ElasticityManagement elasticityManagement;

    @Autowired
    private ConfigurableApplicationContext context;

    @Autowired
    private ApplicationManagement applicationManagement;

    @Autowired
    private MediaServerManagement mediaServerManagement;

    @Autowired
    private ManagedVNFRRepository managedVnfrRepository;

    private NFVORequestor nfvoRequestor;

    @Autowired
    private NfvoProperties nfvoProperties;

    @Autowired
    private ApplicationProperties applicationProperties;

    @Autowired
    private SpringProperties springProperties;
    @Autowired
    private VnfmProperties vnfmProperties;

    @Autowired
    private MediaServerResourceManagement mediaServerResourceManagement;

    /**
     * Vim must be initialized only after the registry is up and plugin registered
     */
    private void initilize() {
        this.nfvoRequestor = new NFVORequestor(nfvoProperties.getUsername(), nfvoProperties.getPassword(), nfvoProperties.getIp(), nfvoProperties.getPort(), "1");
        this.mediaServerResourceManagement.initializeClient();
        //resourceManagement = (ResourceManagement) context.getBean("openstackVIM", "15672");
    }

    @Override
    public VirtualNetworkFunctionRecord instantiate(VirtualNetworkFunctionRecord virtualNetworkFunctionRecord, Object object, List<VimInstance> vimInstances) {
        Iterable<ManagedVNFR> managedVnfrs = managedVnfrRepository.findByVnfrId(virtualNetworkFunctionRecord.getId());
        ManagedVNFR managedVNFR = null;
        if (managedVnfrs.iterator().hasNext()) {
            managedVNFR = managedVnfrs.iterator().next();
        } else {
            managedVNFR = new ManagedVNFR();
            managedVNFR.setNsrId(virtualNetworkFunctionRecord.getParent_ns_id());
            managedVNFR.setVnfrId(virtualNetworkFunctionRecord.getId());
        }
        managedVNFR.setTask(Action.INSTANTIATE);
        managedVnfrRepository.save(managedVNFR);

        log.info("Instantiation of VirtualNetworkFunctionRecord " + virtualNetworkFunctionRecord.getName());
        log.trace("Instantiation of VirtualNetworkFunctionRecord " + virtualNetworkFunctionRecord);
        /**
         * Allocation of Resources
         *  the grant operation is already done before this method
         */
        log.debug("Processing allocation of Recources for vnfr: " + virtualNetworkFunctionRecord);
        for (VirtualDeploymentUnit vdu : virtualNetworkFunctionRecord.getVdu()) {
            VimInstance vimInstance = null;
            try {
                vimInstance = Utils.getVimInstance(vdu.getVimInstanceName(), vimInstances);
            } catch (NotFoundException e) {
                log.error(e.getMessage(), e);
            }
            List<Future<VNFCInstance>> vnfcInstancesFuturePerVDU = new ArrayList<>();
            log.debug("Creating " + vdu.getVnfc().size() + " VMs");
            for (VNFComponent vnfComponent : vdu.getVnfc()) {
                Future<VNFCInstance> allocate = null;
                try {
                    allocate = mediaServerResourceManagement.allocate(vimInstance, vdu, virtualNetworkFunctionRecord, vnfComponent);
                    vnfcInstancesFuturePerVDU.add(allocate);
                } catch (VimException e) {
                    log.error(e.getMessage());
                    if (log.isDebugEnabled())
                        log.error(e.getMessage(), e);
                }
            }
            //Print ids of deployed VNFCInstances
            for (Future<VNFCInstance> vnfcInstanceFuture : vnfcInstancesFuturePerVDU) {
                try {
                    VNFCInstance vnfcInstance = vnfcInstanceFuture.get();
                    vdu.getVnfc_instance().add(vnfcInstance);
                    log.debug("Created VNFCInstance with id: " + vnfcInstance);
                } catch (InterruptedException e) {
                    log.error(e.getMessage());
                    if (log.isDebugEnabled())
                        log.error(e.getMessage(), e);
                    //throw new RuntimeException(e.getMessage(), e);
                } catch (ExecutionException e) {
                    log.error(e.getMessage());
                    if (log.isDebugEnabled())
                        log.error(e.getMessage(), e);
                    //throw new RuntimeException(e.getMessage(), e);
                }
            }

        }
        log.trace("I've finished initialization of vnfr " + virtualNetworkFunctionRecord.getName() + " in facts there are only " + virtualNetworkFunctionRecord.getLifecycle_event().size() + " events");
        ManagedVNFR managedVnfr = managedVnfrRepository.findByVnfrId(virtualNetworkFunctionRecord.getId()).iterator().next();
        managedVnfr.setTask(Action.START);
        managedVnfrRepository.save(managedVnfr);
        return virtualNetworkFunctionRecord;
    }

    @Override
    public void query() {
        throw new UnsupportedOperationException();
    }

    @Override
    public VirtualNetworkFunctionRecord scale(Action scaleInOrOut, VirtualNetworkFunctionRecord virtualNetworkFunctionRecord, VNFCInstance component, Object scripts, VNFRecordDependency dependency) throws Exception {
        //TODO implement scale
        return virtualNetworkFunctionRecord;
    }

    @Override
    public void checkInstantiationFeasibility() {
        throw new UnsupportedOperationException();
    }

    @Override
    public VirtualNetworkFunctionRecord heal(VirtualNetworkFunctionRecord virtualNetworkFunctionRecord, VNFCInstance component, String cause) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateSoftware() {
        throw new UnsupportedOperationException();
    }

    @Override
    public VirtualNetworkFunctionRecord modify(VirtualNetworkFunctionRecord virtualNetworkFunctionRecord, VNFRecordDependency dependency) {
        return virtualNetworkFunctionRecord;
    }

    @Override
    public void upgradeSoftware() {
        throw new UnsupportedOperationException();
    }

    @Override
    public VirtualNetworkFunctionRecord terminate(VirtualNetworkFunctionRecord virtualNetworkFunctionRecord) {
        log.info("Terminating vnfr with id " + virtualNetworkFunctionRecord.getId());
        ManagedVNFR managedVnfr = null;
        Iterable<ManagedVNFR> managedVnfrs = managedVnfrRepository.findByVnfrId(virtualNetworkFunctionRecord.getId());
        if (managedVnfrs.iterator().hasNext()) {
            managedVnfr = managedVnfrs.iterator().next();
        } else {
            managedVnfr = new ManagedVNFR();
            managedVnfr.setNsrId(virtualNetworkFunctionRecord.getParent_ns_id());
            managedVnfr.setVnfrId(virtualNetworkFunctionRecord.getId());
        }
        managedVnfr.setTask(Action.RELEASE_RESOURCES);
        managedVnfrRepository.save(managedVnfr);
        //Set<Event> events = lifecycleManagement.listEvents(virtualNetworkFunctionRecord);
        //if (events.contains(Event.SCALE))
        elasticityManagement.deactivate(virtualNetworkFunctionRecord.getParent_ns_id(), virtualNetworkFunctionRecord.getId());
        try {
            virtualNetworkFunctionRecord = nfvoRequestor.getNetworkServiceRecordAgent().getVirtualNetworkFunctionRecord(virtualNetworkFunctionRecord.getParent_ns_id(), virtualNetworkFunctionRecord.getId());
        } catch (SDKException e) {
            log.error(e.getMessage(), e);
        }
        for (VirtualDeploymentUnit vdu : virtualNetworkFunctionRecord.getVdu()) {
            Set<VNFCInstance> vnfciToRem = new HashSet<>();
            VimInstance vimInstance = null;
            try {
                vimInstance = Utils.getVimInstance(vdu.getVimInstanceName(), nfvoRequestor);
            } catch (NotFoundException e) {
                log.error(e.getMessage(), e);
            }
            for (VNFCInstance vnfcInstance : vdu.getVnfc_instance()) {
                log.debug("Releasing resources for vdu with id " + vdu.getId());
                try {
                    mediaServerResourceManagement.release(vnfcInstance, vimInstance);
                    log.debug("Removed VNFCinstance: " + vnfcInstance);
                } catch (VimException e) {
                    log.error(e.getMessage(), e);
                    throw new RuntimeException(e.getMessage(), e);
                }
                vnfciToRem.add(vnfcInstance);
                log.debug("Released resources for vdu with id " + vdu.getId());
            }
            vdu.getVnfc_instance().removeAll(vnfciToRem);
        }
        log.info("Terminated vnfr with id " + virtualNetworkFunctionRecord.getId());
        try {
            applicationManagement.deleteByVnfrId(virtualNetworkFunctionRecord.getId());
            mediaServerManagement.deleteByVnfrId(virtualNetworkFunctionRecord.getId());
            managedVnfrRepository.deleteByVnfrId(virtualNetworkFunctionRecord.getId());
        } catch (NotFoundException e) {
            log.warn(e.getMessage());
        }
        try {
            managedVnfrRepository.deleteByVnfrId(virtualNetworkFunctionRecord.getId());
        } catch (NotFoundException e) {
            log.warn("ManagedVNFR were not existing and therefore not deletable");
        }
        return virtualNetworkFunctionRecord;
    }

    @Override
    public void handleError(VirtualNetworkFunctionRecord virtualNetworkFunctionRecord) {
        log.error("Error arrised.");
    }

    @Override
    public VirtualNetworkFunctionRecord start(VirtualNetworkFunctionRecord virtualNetworkFunctionRecord) {
        ManagedVNFR managedVnfr = managedVnfrRepository.findByVnfrId(virtualNetworkFunctionRecord.getId()).iterator().next();
        managedVnfr.setTask(Action.START);
        managedVnfrRepository.save(managedVnfr);
        log.debug("Initializing Nubomedia MediaServers:");
        for (VirtualDeploymentUnit vdu : virtualNetworkFunctionRecord.getVdu()) {
            for (VNFCInstance vnfcInstance : vdu.getVnfc_instance()) {
                try {
                    mediaServerManagement.add(virtualNetworkFunctionRecord.getId(), vnfcInstance);
                } catch (Exception e) {
                    log.warn(e.getMessage(), e);
                }
            }
        }
        //TODO where to set it to active?
        virtualNetworkFunctionRecord.setStatus(Status.ACTIVE);
        if (virtualNetworkFunctionRecord.getAuto_scale_policy().size() > 0) {
            log.debug("Activating Elasticity for VNFR with id: " + virtualNetworkFunctionRecord.getId());
            try {
                elasticityManagement.activate(virtualNetworkFunctionRecord.getParent_ns_id(), virtualNetworkFunctionRecord.getId());
            } catch (NotFoundException e) {
                log.warn(e.getMessage());
                if (log.isDebugEnabled()) {
                    log.error(e.getMessage(), e);
                }
            } catch (VimException e) {
                log.warn(e.getMessage());
                if (log.isDebugEnabled()) {
                    log.error(e.getMessage(), e);
                }
            }
        } else {
            log.debug("Do not activate Elasticity because there are no AutoScalePolicies defined.");
        }
        managedVnfr = managedVnfrRepository.findByVnfrId(virtualNetworkFunctionRecord.getId()).iterator().next();
        managedVnfr.setTask(Action.SCALING);
        managedVnfrRepository.save(managedVnfr);
        return virtualNetworkFunctionRecord;
    }

    @Override
    public VirtualNetworkFunctionRecord configure(VirtualNetworkFunctionRecord virtualNetworkFunctionRecord) {
        /**
         * This message should never arrive!
         */
        return virtualNetworkFunctionRecord;
    }

    @Override
    protected void setup() {
        super.setup();
        context.registerShutdownHook();
    }

//    public static void main(String[] args) {
//        SpringApplication.run(MediaServerManager.class, args);
//    }

    @Override
    public void NotifyChange() {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void checkEmsStarted(String hostname) {
        //throw new UnsupportedOperationException();
    }

    private void startPlugins() throws IOException {
        PluginStartup.startPluginRecursive("./plugins", true, vnfmProperties.getRabbitmq().getBrokerIp(), String.valueOf(springProperties.getRabbitmq().getPort()), 15, springProperties.getRabbitmq().getUsername(), springProperties.getRabbitmq().getPassword(), vnfmProperties.getRabbitmq().getManagement().getPort());
    }

    private void destroyPlugins() {
        PluginStartup.destroy();
    }

    @PostConstruct
    private void init() {
        if (!Utils.isNfvoStarted(nfvoProperties.getIp(), nfvoProperties.getPort())) {
            log.error("NFVO is not available");
            System.exit(1);
        }
        log.debug("Initializing MS-VNFM");
        try {
            startPlugins();
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        initilize();
        for (ManagedVNFR managedVNFR : managedVnfrRepository.findAll()) {
            VirtualNetworkFunctionRecord vnfr = null;
            try {
                vnfr = nfvoRequestor.getNetworkServiceRecordAgent().getVirtualNetworkFunctionRecord(managedVNFR.getNsrId(), managedVNFR.getVnfrId());
                NFVMessage nfvMessage = null;
                if (vnfr != null && vnfr.getId() != null) {
                    if (managedVNFR.getTask() == Action.INSTANTIATE) {
                        try {
                            List<VimInstance> vimInstances = nfvoRequestor.getVimInstanceAgent().findAll();
                            nfvMessage = VnfmUtils.getNfvMessage(Action.INSTANTIATE, instantiate(vnfr, null, vimInstances));
                        } catch (SDKException e) {
                            log.error(e.getMessage(), e);
                        } catch (ClassNotFoundException e) {
                            log.error(e.getMessage(), e);
                        }
                    } else if (managedVNFR.getTask() == Action.START) {
                        nfvMessage = VnfmUtils.getNfvMessage(Action.START, start(vnfr));
                    } else if (managedVNFR.getTask() == Action.RELEASE_RESOURCES) {
                        nfvMessage = VnfmUtils.getNfvMessage(Action.RELEASE_RESOURCES, terminate(vnfr));
                    } else if (managedVNFR.getTask() == Action.SCALING) {
                        try {
                            elasticityManagement.activate(managedVNFR.getNsrId(), managedVNFR.getVnfrId());
                        } catch (NotFoundException e) {
                            log.error(e.getMessage(), e);
                        } catch (VimException e) {
                            log.error(e.getMessage(), e);
                        }
                    }
                    if (nfvMessage != null) {
                        vnfmHelper.sendToNfvo(nfvMessage);
                    }
                } else {
                    log.error("VNFR with id: " + managedVNFR.getVnfrId() + " is not available anymore on NFVO-side");
                    managedVnfrRepository.delete(managedVNFR);
                }
            } catch (SDKException e) {
                log.error(e.getMessage(), e);
            }
        }
        if (applicationProperties.getHeartbeat().isActivate()) {
            applicationManagement.startHeartbeatCheck();
        }
        //test();
    }


    private void test() {
        VimDriverCaller client = null;
        client = (VimDriverCaller) ((RabbitPluginBroker) context.getBean("rabbitPluginBroker")).getVimDriverCaller("localhost", "admin", "openbaton", 5672, "openstack", "msopenstack", "15672");

        final ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(25);
        taskScheduler.setWaitForTasksToCompleteOnShutdown(true);
        taskScheduler.setRemoveOnCancelPolicy(true);
        taskScheduler.initialize();

        final VimDriverCaller finalClient = client;
        class Launch implements Callable<Server> {

            @Override
            public Server call() throws RemoteException {
                try {
                    HashSet<String> networks = new HashSet<String>();
                    networks.add("89d7bbde-f9e6-4ac7-bac6-863a4c8fbc62");
                    Map<String, String> floatingIps = new HashMap<String, String>();
                    floatingIps.put("internal_nubomedia", "random");
                    return finalClient.launchInstanceAndWait(createVimInstance(), "test", "f690e49c-151a-48de-bca8-6239dde8dd21", "ea071529-6bed-4299-a463-7e182d5b119f", "tub-nubomedia", networks, new HashSet<String>(), "#user", floatingIps);
                } catch (VimDriverException e) {
                    e.printStackTrace();
                    return null;
                }
            }
        }
        List<Future<Server>> futures = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            futures.add(taskScheduler.submit(new Launch()));
        }

        System.out.println("Wait for servers");

        for (Future<Server> future : futures)
            try {
                System.out.println(future.get());
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
    }

    private static VimInstance createVimInstance() {
        VimInstance vimInstance = new VimInstance();
        vimInstance.setName("vim-instance");
        vimInstance.setTenant("nubomedia");
        vimInstance.setAuthUrl("http://80.96.122.48:5000/v2.0");
        vimInstance.setUsername("nubomedia");
        vimInstance.setPassword("nub0m3d1@");
        vimInstance.setKeyPair("tub-nubomedia");
        vimInstance.setSecurityGroups(new HashSet<String>());
        Location location = new Location();
        location.setName("location");
        vimInstance.setLocation(location);
        return vimInstance;
    }

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        for (ManagedVNFR managedVNFR : managedVnfrRepository.findAll()) {
            elasticityManagement.deactivate(managedVNFR.getNsrId(), managedVNFR.getVnfrId());
        }
        try {
            Thread.sleep(2500);
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
        }
    }
}
