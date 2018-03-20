import de.evoila.Application;
import de.evoila.cf.broker.bean.BoshProperties;
import de.evoila.cf.broker.custom.mongodb.MongoDBCustomImplementation;
import de.evoila.cf.broker.exception.PlatformException;
import de.evoila.cf.broker.model.Plan;
import de.evoila.cf.broker.model.Platform;
import de.evoila.cf.broker.model.ServiceInstance;
import de.evoila.cf.broker.model.VolumeUnit;
import de.evoila.cf.broker.service.availability.ServicePortAvailabilityVerifier;
import de.evoila.cf.cpi.bosh.BoshPlatformService;
import de.evoila.cf.cpi.bosh.connection.BoshConnection;
import io.bosh.client.DirectorClient;
import io.bosh.client.deployments.Deployment;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.ConfigFileApplicationContextInitializer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = { Application.class})
@ContextConfiguration(initializers = ConfigFileApplicationContextInitializer.class)
@ActiveProfiles("BoshLite")
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestBoshPlatformService {

    @Autowired
    ServicePortAvailabilityVerifier availabilityVerifier;
    @Autowired
    BoshPlatformService platformService;
    @Autowired
    ApplicationContext context;
    @Autowired
    BoshProperties properties;

    @Autowired
    private MongoDBCustomImplementation mongodb;

    private DirectorClient connection;
    ServiceInstance instance;
    Plan plan;
    Plan updatedPlan;

    @Before
    public void before(){
        connection = new BoshConnection(properties.getUsername(),
                                        properties.getPassword(),
                                        properties.getHost(),
                                        properties.getPort(),
                                        properties.getAuthentication()).authenticate().connection();

        Map<String,Object> meta = new HashMap<>();
        meta.put("nodes", 3);
        plan = new Plan("planId", "Plan", "Test Plan", Platform.BOSH, 20, VolumeUnit.G, "", false,200);
        plan.getMetadata().setCustomParameters(meta);

        meta = new HashMap<>();
        meta.put("nodes", 5);
        updatedPlan = new Plan("updatedPlanId", "UpdatedPlan", "Updated Test Plan", Platform.BOSH, 30, VolumeUnit.G, "", false, 200);

        updatedPlan.getMetadata().setCustomParameters(meta);
        instance = new ServiceInstance("serviceId", "serviceDefId", "planId","none","none",null,"");
    }

    @Test
    public void a_testCreate() throws PlatformException {
        platformService.createInstance(instance,plan, new HashMap<>());
        assertNotNull(connection.deployments().list().toBlocking().first()
                .stream().filter(e -> e.getName().equals(instance.getId())).findFirst().get());

        assertEquals(connection.vms().listDetails(instance.getId()).toBlocking().first().size(), 3);
        assertEquals(3, instance.getHosts().size());
    }

    @Test
    public void b_testGet() {
        Deployment deployment = connection.deployments().get(instance.getId()).toBlocking().first();
        assertEquals(deployment.getName(), instance.getId());
        assertTrue(deployment.getManifest().containsValue("9b61cd26-8e25-4272-b45d-340eaaf47f08"));
    }

    @Test
    public void c_testUpdate() throws PlatformException {
        platformService.updateInstance(instance, updatedPlan);
        assertNotNull(connection.deployments().list().toBlocking().first()
                .stream().filter(e -> e.getName().equals(instance.getId())).findFirst().get());
        assertEquals(connection.vms().listDetails(instance.getId()).toBlocking().first().size(), 5);
        assertEquals(5, instance.getHosts().size());
    }

    @Test
    public void d_testDelete() throws PlatformException, InterruptedException {
        platformService.deleteInstance(instance);
        while (connection.tasks().listRunning().toBlocking().first().size() > 0){
            Thread.sleep(3000);
        }
        assertTrue(connection.deployments().list().toBlocking().first().isEmpty());
    }
}