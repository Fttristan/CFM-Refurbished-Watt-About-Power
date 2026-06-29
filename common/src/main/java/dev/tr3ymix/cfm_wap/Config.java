package dev.tr3ymix.cfm_wap;

import com.mrcrayfish.framework.api.config.ConfigProperty;
import com.mrcrayfish.framework.api.config.ConfigType;
import com.mrcrayfish.framework.api.config.FrameworkConfig;
import com.mrcrayfish.framework.api.config.IntProperty;

public class Config {

    public static final int CIRCUIT_BREAKER_NETWORK_NODE_FLOOR = 5000;

    @FrameworkConfig(id = CFM_WAP.MOD_ID, name = "server", type = ConfigType.SERVER_SYNC)
    public static final Server SERVER = new Server();

    public static class Server{
        @ConfigProperty(name = "electricity", comment = "Electricity related properties")
        public final Electricity electricity = new Electricity();

        public static class Electricity{
            @ConfigProperty(name = "maximumLinksPerCircuitBreaker", comment = """
                The maximum amount of links that can be connected to a circuit breaker.""")
            public final IntProperty maximumLinksPerCircuitBreaker = IntProperty.create(16, 1, 256);

            @ConfigProperty(name = "maximumNodesInCircuitBreakerNetwork", comment = """
                The maximum amount of nodes in a network that can be powered by the circuit breaker.""")
            public final IntProperty maximumNodesInCircuitBreakerNetwork = IntProperty.create(128, 1, 1024);

            @ConfigProperty(name = "lightningStrikeOutageRadius", comment = """
                The radius (in blocks) within which the circuit breaker is disabled when a lightning bolt occurs.""")
            public final IntProperty lightningStrikeOutageRadius = IntProperty.create(30, 15, 1024);
        }
    }
}
