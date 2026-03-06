package org.foreverempty.coochat.netty;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Slf4j
@Component
public class ChatWsRegistration {

    @Value("${spring.cloud.nacos.discovery.server-addr}")
    private String nacosServerAddr;

    @Value("${spring.cloud.nacos.discovery.namespace:}")
    private String namespace;

    @Value("${spring.cloud.nacos.discovery.group:DEFAULT_GROUP}")
    private String groupName;

    @Value("${spring.cloud.nacos.discovery.cluster-name:DEFAULT}")
    private String clusterName;

    @Value("${spring.cloud.nacos.discovery.ip:}")
    private String configuredIp;

    @Value("${spring.cloud.nacos.discovery.weight:1}")
    private double weight;

    @Value("${spring.cloud.nacos.discovery.ephemeral:true}")
    private boolean ephemeral;

    @Value("${coo.chat.ws.service-name:Coo-chat-ws}")
    private String wsServiceName;

    @Value("${coo.chat.ws.metadata.protocol:ws}")
    private String protocol;

    @Value("${netty.port}")
    private int nettyPort;

    private final Object lock = new Object();
    private NamingService namingService;
    private boolean registered;
    private String registeredIp;

    public void registerOnNettyStarted() {
        synchronized (lock) {
            if (registered) {
                return;
            }
            try {
                namingService = buildNamingService();
                registeredIp = resolveIp();

                Instance instance = new Instance();
                instance.setIp(registeredIp);
                instance.setPort(nettyPort);
                instance.setClusterName(clusterName);
                instance.setWeight(weight);
                instance.setEphemeral(ephemeral);

                Map<String, String> metadata = new HashMap<>();
                metadata.put("protocol", protocol);
                instance.setMetadata(metadata);

                namingService.registerInstance(wsServiceName, groupName, instance);
                registered = true;
                log.info("Registered websocket service to Nacos: {} {}:{} group={}",
                        wsServiceName, registeredIp, nettyPort, groupName);
            } catch (Exception e) {
                log.error("Failed to register websocket service to Nacos", e);
            }
        }
    }

    @PreDestroy
    public void deregister() {
        synchronized (lock) {
            if (!registered || namingService == null || !StringUtils.hasText(registeredIp)) {
                return;
            }
            try {
                namingService.deregisterInstance(wsServiceName, groupName, registeredIp, nettyPort);
                log.info("Deregistered websocket service from Nacos: {} {}:{} group={}",
                        wsServiceName, registeredIp, nettyPort, groupName);
            } catch (Exception e) {
                log.error("Failed to deregister websocket service from Nacos", e);
            } finally {
                try {
                    namingService.shutDown();
                } catch (Exception ignored) {
                }
                registered = false;
                namingService = null;
                registeredIp = null;
            }
        }
    }

    private NamingService buildNamingService() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("serverAddr", nacosServerAddr);
        if (StringUtils.hasText(namespace)) {
            properties.setProperty("namespace", namespace);
        }
        return NacosFactory.createNamingService(properties);
    }

    private String resolveIp() {
        if (StringUtils.hasText(configuredIp)) {
            return configuredIp;
        }
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }
}
