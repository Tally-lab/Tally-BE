package com.devpulse.mcp.monitor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "monitor")
public class MonitorConfig {

    private List<ServiceEntry> services = new ArrayList<>();

    @Data
    public static class ServiceEntry {
        private String name;
        private String url;
    }
}
