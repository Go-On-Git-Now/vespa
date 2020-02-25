// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.test.TestTimer;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.applicationmodel.ApplicationInstance;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceId;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceReference;
import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.applicationmodel.ConfigId;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.applicationmodel.ServiceCluster;
import com.yahoo.vespa.applicationmodel.ServiceInstance;
import com.yahoo.vespa.applicationmodel.ServiceStatus;
import com.yahoo.vespa.applicationmodel.ServiceType;
import com.yahoo.vespa.applicationmodel.TenantId;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.orchestrator.controller.ClusterControllerClientFactory;
import com.yahoo.vespa.orchestrator.controller.ClusterControllerClientFactoryMock;
import com.yahoo.vespa.orchestrator.model.ApplicationApiFactory;
import com.yahoo.vespa.orchestrator.model.NodeGroup;
import com.yahoo.vespa.orchestrator.policy.BatchHostStateChangeDeniedException;
import com.yahoo.vespa.orchestrator.policy.HostStateChangeDeniedException;
import com.yahoo.vespa.orchestrator.policy.HostedVespaClusterPolicy;
import com.yahoo.vespa.orchestrator.policy.HostedVespaPolicy;
import com.yahoo.vespa.orchestrator.status.HostStatus;
import com.yahoo.vespa.orchestrator.status.MutableStatusRegistry;
import com.yahoo.vespa.orchestrator.status.StatusService;
import com.yahoo.vespa.orchestrator.status.ZookeeperStatusService;
import com.yahoo.vespa.service.monitor.ServiceModel;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.yahoo.vespa.orchestrator.status.ApplicationInstanceStatus.ALLOWED_TO_BE_DOWN;
import static com.yahoo.vespa.orchestrator.status.ApplicationInstanceStatus.NO_REMARKS;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsCollectionContaining.hasItem;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.internal.verification.VerificationModeFactory.atLeastOnce;

/**
 * Test Orchestrator with a mock backend (the MockCurator)
 *
 * @author smorgrav
 */
public class OrchestratorImplTest {

    private final ApplicationApiFactory applicationApiFactory = new ApplicationApiFactory(3);
    private final InMemoryFlagSource flagSource = new InMemoryFlagSource();

    private ApplicationId app1;
    private ApplicationId app2;
    private HostName app1_host1;

    private OrchestratorImpl orchestrator;
    private ClusterControllerClientFactoryMock clustercontroller;

    @Before
    public void setUp() {
        // Extract applications and hosts from dummy instance lookup service
        Iterator<ApplicationInstance> iterator = DummyInstanceLookupService.getApplications().iterator();
        ApplicationInstanceReference app1_ref = iterator.next().reference();
        app1 = OrchestratorUtil.toApplicationId(app1_ref);
        app1_host1 = DummyInstanceLookupService.getContentHosts(app1_ref).iterator().next();
        app2 = OrchestratorUtil.toApplicationId(iterator.next().reference());

        clustercontroller = new ClusterControllerClientFactoryMock();
        orchestrator = new OrchestratorImpl(new HostedVespaPolicy(new HostedVespaClusterPolicy(), clustercontroller, applicationApiFactory),
                                            clustercontroller,
                                            new ZookeeperStatusService(new MockCurator(), mock(Metric.class), new TestTimer()),
                                            new DummyInstanceLookupService(),
                                            0,
                                            new ManualClock(),
                                            applicationApiFactory,
                                            flagSource);

        clustercontroller.setAllDummyNodesAsUp();
    }

    @Test
    public void application_has_initially_no_remarks() throws Exception {
        assertThat(orchestrator.getApplicationInstanceStatus(app1), is(NO_REMARKS));
    }

    @Test
    public void application_can_be_set_in_suspend() throws Exception {
        orchestrator.suspend(app1);
        assertThat(orchestrator.getApplicationInstanceStatus(app1), is(ALLOWED_TO_BE_DOWN));
    }

    @Test
    public void application_can_be_removed_from_suspend() throws Exception {
        orchestrator.suspend(app1);
        orchestrator.resume(app1);
        assertThat(orchestrator.getApplicationInstanceStatus(app1), is(NO_REMARKS));
    }

    @Test
    public void appliations_list_returns_empty_initially() {
        assertThat(orchestrator.getAllSuspendedApplications(), is(empty()));
    }

    @Test
    public void appliations_list_returns_suspended_apps() throws Exception {
        // One suspended app
        orchestrator.suspend(app1);
        assertThat(orchestrator.getAllSuspendedApplications().size(), is(1));
        assertThat(orchestrator.getAllSuspendedApplications(), hasItem(app1));

        // Two suspended apps
        orchestrator.suspend(app2);
        assertThat(orchestrator.getAllSuspendedApplications().size(), is(2));
        assertThat(orchestrator.getAllSuspendedApplications(), hasItem(app1));
        assertThat(orchestrator.getAllSuspendedApplications(), hasItem(app2));

        // Back to one when resetting one app to no_remarks
        orchestrator.resume(app1);
        assertThat(orchestrator.getAllSuspendedApplications().size(), is(1));
        assertThat(orchestrator.getAllSuspendedApplications(), hasItem(app2));
    }


    @Test
    public void application_operations_are_idempotent() throws Exception {
        // Two suspends
        orchestrator.suspend(app1);
        orchestrator.suspend(app1);
        assertThat(orchestrator.getApplicationInstanceStatus(app1), is(ALLOWED_TO_BE_DOWN));
        assertThat(orchestrator.getApplicationInstanceStatus(app2), is(NO_REMARKS));

        // Three no_remarks
        orchestrator.resume(app1);
        orchestrator.resume(app1);
        orchestrator.resume(app1);
        assertThat(orchestrator.getApplicationInstanceStatus(app1), is(NO_REMARKS));
        assertThat(orchestrator.getApplicationInstanceStatus(app2), is(NO_REMARKS));

        // Two suspends and two on two applications interleaved
        orchestrator.suspend(app2);
        orchestrator.resume(app1);
        orchestrator.suspend(app2);
        orchestrator.resume(app1);
        assertThat(orchestrator.getApplicationInstanceStatus(app1), is(NO_REMARKS));
        assertThat(orchestrator.getApplicationInstanceStatus(app2), is(ALLOWED_TO_BE_DOWN));
    }

    @Test
    public void application_suspend_sets_application_nodes_in_maintenance_and_allowed_to_be_down() throws Exception {
        // Pre condition
        assertEquals(NO_REMARKS, orchestrator.getApplicationInstanceStatus(app1));
        assertEquals(HostStatus.NO_REMARKS, orchestrator.getNodeStatus(app1_host1));
        assertFalse(isInMaintenance(app1, app1_host1));

        orchestrator.suspend(app1);

        assertEquals(HostStatus.ALLOWED_TO_BE_DOWN, orchestrator.getNodeStatus(app1_host1));
        assertTrue(isInMaintenance(app1, app1_host1));
    }

    @Test
    public void node_suspend_while_app_is_resumed_set_allowed_to_be_down_and_set_it_in_maintenance() throws Exception {
        // Pre condition
        assertEquals(NO_REMARKS, orchestrator.getApplicationInstanceStatus(app1));
        assertEquals(HostStatus.NO_REMARKS, orchestrator.getNodeStatus(app1_host1));
        assertFalse(isInMaintenance(app1, app1_host1));

        orchestrator.suspend(app1_host1);

        assertEquals(HostStatus.ALLOWED_TO_BE_DOWN, orchestrator.getNodeStatus(app1_host1));
        assertTrue(isInMaintenance(app1, app1_host1));
    }

    @Test
    public void node_suspend_while_app_is_suspended_does_nothing() throws Exception {
        // Pre condition
        orchestrator.suspend(app1);
        assertEquals(ALLOWED_TO_BE_DOWN, orchestrator.getApplicationInstanceStatus(app1));
        assertEquals(HostStatus.ALLOWED_TO_BE_DOWN, orchestrator.getNodeStatus(app1_host1));
        assertTrue(isInMaintenance(app1, app1_host1));

        orchestrator.suspend(app1_host1);

        // Should not change anything
        assertEquals(ALLOWED_TO_BE_DOWN, orchestrator.getApplicationInstanceStatus(app1));
        assertEquals(HostStatus.ALLOWED_TO_BE_DOWN, orchestrator.getNodeStatus(app1_host1));
        assertTrue(isInMaintenance(app1, app1_host1));
    }

    @Test
    public void node_resume_after_app_is_resumed_removes_allowed_be_down_and_set_it_up() throws Exception {
        // Pre condition
        orchestrator.suspend(app1);
        assertEquals(ALLOWED_TO_BE_DOWN, orchestrator.getApplicationInstanceStatus(app1));
        assertEquals(HostStatus.ALLOWED_TO_BE_DOWN, orchestrator.getNodeStatus(app1_host1));
        assertTrue(isInMaintenance(app1, app1_host1));

        orchestrator.resume(app1);
        orchestrator.resume(app1_host1);

        assertEquals(HostStatus.NO_REMARKS, orchestrator.getNodeStatus(app1_host1));
        assertFalse(isInMaintenance(app1, app1_host1));
    }

    @Test
    public void node_resume_while_app_is_suspended_does_nothing() throws Exception {
        orchestrator.suspend(app1_host1);
        orchestrator.suspend(app1);

        orchestrator.resume(app1_host1);

        assertEquals(HostStatus.ALLOWED_TO_BE_DOWN, orchestrator.getNodeStatus(app1_host1));
        assertTrue(isInMaintenance(app1, app1_host1));
    }

    @Test
    public void applicationReferenceHasTenantAndAppInstance() {
        InstanceLookupService service = new DummyInstanceLookupService();
        String applicationInstanceId = service.findInstanceByHost(DummyInstanceLookupService.TEST1_HOST_NAME).get()
                .reference().toString();
        assertEquals("test-tenant-id:application:prod:utopia-1:instance", applicationInstanceId);
    }

    @Test
    public void testSetNodeState() throws OrchestrationException {
        assertEquals(HostStatus.NO_REMARKS, orchestrator.getNodeStatus(app1_host1));
        orchestrator.setNodeStatus(app1_host1, HostStatus.ALLOWED_TO_BE_DOWN);
        assertEquals(HostStatus.ALLOWED_TO_BE_DOWN, orchestrator.getNodeStatus(app1_host1));
        orchestrator.setNodeStatus(app1_host1, HostStatus.NO_REMARKS);
        assertEquals(HostStatus.NO_REMARKS, orchestrator.getNodeStatus(app1_host1));
    }

    @Test
    public void suspendAllWorks() throws Exception {
        // A spy is preferential because suspendAll() relies on delegating the hard work to suspend() and resume().
        OrchestratorImpl orchestrator = spy(this.orchestrator);

        orchestrator.suspendAll(
                new HostName("parentHostname"),
                Arrays.asList(
                        DummyInstanceLookupService.TEST1_HOST_NAME,
                        DummyInstanceLookupService.TEST3_HOST_NAME,
                        DummyInstanceLookupService.TEST6_HOST_NAME));

        // As of 2016-06-07 the order of the node groups are as follows:
        //   TEST3: mediasearch:imagesearch:default
        //   TEST6: tenant-id-3:application-instance-3:default
        //   TEST1: test-tenant-id:application:instance
        InOrder order = inOrder(orchestrator);
        verifySuspendGroup(order, orchestrator, DummyInstanceLookupService.TEST3_NODE_GROUP, true);
        verifySuspendGroup(order, orchestrator, DummyInstanceLookupService.TEST6_NODE_GROUP, true);
        verifySuspendGroup(order, orchestrator, DummyInstanceLookupService.TEST1_NODE_GROUP, true);
        verifySuspendGroup(order, orchestrator, DummyInstanceLookupService.TEST3_NODE_GROUP, false);
        verifySuspendGroup(order, orchestrator, DummyInstanceLookupService.TEST6_NODE_GROUP, false);
        verifySuspendGroup(order, orchestrator, DummyInstanceLookupService.TEST1_NODE_GROUP, false);
        order.verifyNoMoreInteractions();
    }

    private void verifySuspendGroup(InOrder order, OrchestratorImpl orchestrator, NodeGroup nodeGroup, boolean probe)
            throws HostStateChangeDeniedException{
        ArgumentCaptor<OrchestratorContext> argument = ArgumentCaptor.forClass(OrchestratorContext.class);
        order.verify(orchestrator).suspendGroup(argument.capture(), eq(nodeGroup));
        assertEquals(probe, argument.getValue().isProbe());
    }

    @Test
    public void whenSuspendAllFails() throws Exception {
        // A spy is preferential because suspendAll() relies on delegating the hard work to suspend() and resume().
        OrchestratorImpl orchestrator = spy(this.orchestrator);

        Throwable supensionFailure = new HostStateChangeDeniedException(
                DummyInstanceLookupService.TEST6_HOST_NAME,
                "some-constraint",
                "error message");
        doThrow(supensionFailure).when(orchestrator).suspendGroup(any(), eq(DummyInstanceLookupService.TEST6_NODE_GROUP));

        try {
            orchestrator.suspendAll(
                    new HostName("parentHostname"),
                    Arrays.asList(
                            DummyInstanceLookupService.TEST1_HOST_NAME,
                            DummyInstanceLookupService.TEST3_HOST_NAME,
                            DummyInstanceLookupService.TEST6_HOST_NAME));
            fail();
        } catch (BatchHostStateChangeDeniedException e) {
            assertEquals("Failed to suspend NodeGroup{application=tenant-id-3:application-instance-3:prod:utopia-1:default, " +
                            "hostNames=[test6.hostname.tld]} with parent host parentHostname: " +
                            "Changing the state of test6.hostname.tld would violate " +
                            "some-constraint: error message",
                    e.getMessage());
        }

        InOrder order = inOrder(orchestrator);
        order.verify(orchestrator).suspendGroup(any(), eq(DummyInstanceLookupService.TEST3_NODE_GROUP));
        order.verify(orchestrator).suspendGroup(any(), eq(DummyInstanceLookupService.TEST6_NODE_GROUP));
        order.verifyNoMoreInteractions();
    }

    @Test
    public void testLargeLocks() throws Exception {
        var tenantId = new TenantId("tenant");
        var applicationInstanceId = new ApplicationInstanceId("app:dev:us-east-1:default");
        var applicationInstanceReference = new ApplicationInstanceReference(tenantId, applicationInstanceId);

        var policy = mock(HostedVespaPolicy.class);
        var zookeeperStatusService = mock(ZookeeperStatusService.class);
        var instanceLookupService = mock(InstanceLookupService.class);
        var applicationInstance = mock(ApplicationInstance.class);
        var clusterControllerClientFactory = mock(ClusterControllerClientFactory.class);
        var clock = new ManualClock();
        var applicationApiFactory = mock(ApplicationApiFactory.class);
        var hostStatusRegistry = mock(MutableStatusRegistry.class);

        when(instanceLookupService.findInstanceByHost(any())).thenReturn(Optional.of(applicationInstance));
        when(applicationInstance.reference()).thenReturn(applicationInstanceReference);
        when(zookeeperStatusService.lockApplicationInstance_forCurrentThreadOnly(any(), any()))
                .thenReturn(hostStatusRegistry);
        when(hostStatusRegistry.getStatus()).thenReturn(NO_REMARKS);

        var orchestrator = new OrchestratorImpl(
                policy,
                clusterControllerClientFactory,
                zookeeperStatusService,
                instanceLookupService,
                20,
                clock,
                applicationApiFactory,
                flagSource);

        HostName parentHostname = new HostName("parent.vespa.ai");

        orchestrator.suspendAll(parentHostname, List.of(parentHostname));

        ArgumentCaptor<OrchestratorContext> contextCaptor = ArgumentCaptor.forClass(OrchestratorContext.class);
        verify(zookeeperStatusService, times(2)).lockApplicationInstance_forCurrentThreadOnly(contextCaptor.capture(), any());
        List<OrchestratorContext> contexts = contextCaptor.getAllValues();

        // First invocation is probe, second is not.
        assertEquals(2, contexts.size());
        assertTrue(contexts.get(0).isProbe());
        assertTrue(contexts.get(0).largeLocks());
        assertFalse(contexts.get(1).isProbe());
        assertTrue(contexts.get(1).largeLocks());

        verify(applicationApiFactory, times(2)).create(any(), any(), any());
        verify(policy, times(2)).grantSuspensionRequest(any(), any());
        verify(instanceLookupService, atLeastOnce()).findInstanceByHost(any());
        verify(hostStatusRegistry, times(2)).getStatus();

        // Each zookeeperStatusService that is created, is closed.
        verify(zookeeperStatusService, times(2)).lockApplicationInstance_forCurrentThreadOnly(any(), any());
        verify(hostStatusRegistry, times(2)).close();

        verifyNoMoreInteractions(
                policy,
                clusterControllerClientFactory,
                zookeeperStatusService,
                hostStatusRegistry,
                instanceLookupService,
                applicationApiFactory);
    }

    @Test
    public void testGetHost() throws Exception {
        ClusterControllerClientFactory clusterControllerClientFactory = new ClusterControllerClientFactoryMock();
        StatusService statusService = new ZookeeperStatusService(new MockCurator(), mock(Metric.class), new TestTimer());

        HostName hostName = new HostName("host.yahoo.com");
        TenantId tenantId = new TenantId("tenant");
        ApplicationInstanceId applicationInstanceId =
                new ApplicationInstanceId("applicationInstanceId");
        ApplicationInstanceReference reference = new ApplicationInstanceReference(
                tenantId,
                applicationInstanceId);

        ApplicationInstance applicationInstance =
                new ApplicationInstance(
                        tenantId,
                        applicationInstanceId,
                        Set.of(new ServiceCluster(
                                new ClusterId("clusterId"),
                                new ServiceType("serviceType"),
                                Set.of(new ServiceInstance(
                                               new ConfigId("configId1"),
                                               hostName,
                                               ServiceStatus.UP),
                                       new ServiceInstance(
                                               new ConfigId("configId2"),
                                               hostName,
                                               ServiceStatus.NOT_CHECKED)))));

        InstanceLookupService lookupService = new ServiceMonitorInstanceLookupService(
                () -> new ServiceModel(Map.of(reference, applicationInstance)));

        orchestrator = new OrchestratorImpl(new HostedVespaPolicy(new HostedVespaClusterPolicy(), clusterControllerClientFactory, applicationApiFactory),
                                            clusterControllerClientFactory,
                                            statusService,
                                            lookupService,
                                            0,
                                            new ManualClock(),
                                            applicationApiFactory,
                                            flagSource);

        orchestrator.setNodeStatus(hostName, HostStatus.ALLOWED_TO_BE_DOWN);

        Host host = orchestrator.getHost(hostName);
        assertEquals(reference, host.getApplicationInstanceReference());
        assertEquals(hostName, host.getHostName());
        assertEquals(HostStatus.ALLOWED_TO_BE_DOWN, host.getHostInfo().status());
        assertTrue(host.getHostInfo().suspendedSince().isPresent());
        assertEquals(2, host.getServiceInstances().size());
    }

    private boolean isInMaintenance(ApplicationId appId, HostName hostName) throws ApplicationIdNotFoundException {
        for (ApplicationInstance app : DummyInstanceLookupService.getApplications()) {
            if (app.reference().equals(OrchestratorUtil.toApplicationInstanceReference(appId, new DummyInstanceLookupService()))) {
                return clustercontroller.isInMaintenance(app, hostName);
            }
        }
        throw new ApplicationIdNotFoundException();
    }
}
