package com.example.pj125.device;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pj125.devices")
public class DevicesProperties {
    private Endpoint main = new Endpoint();
    private Endpoint relay = new Endpoint();

    public Endpoint getMain() {
        return main;
    }

    public void setMain(Endpoint main) {
        this.main = main;
    }

    public Endpoint getRelay() {
        return relay;
    }

    public void setRelay(Endpoint relay) {
        this.relay = relay;
    }

    public static class Endpoint {
        /**
         * Endpoint selector.
         * <p>
         * Supported values:
         * <ul>
         *   <li>local-sim (default): in-process simulated device</li>
         *   <li>host:port (reserved): future remote device agent endpoint</li>
         * </ul>
         */
        private String endpoint = "local-sim";

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }
    }
}

