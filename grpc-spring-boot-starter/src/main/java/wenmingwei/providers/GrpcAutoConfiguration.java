package wenmingwei.providers;

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.netty.NettyServerBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
@ConditionalOnProperty(value = "grpc.provider.enabled", havingValue = "true", matchIfMissing = true)
public class GrpcAutoConfiguration {

    @Autowired
    private GrpcConfig grpcConfig;

    @Autowired(required = false)
    private Map<String, Customizer> customizers;

    @Autowired(required = false)
    private Map<String, BindableService> services;

    private Map<String, Server> servers;

    @PostConstruct
    public void startup() {
        grpcConfig.getCustomizer().forEach(customizer -> {
            if (customizers == null || customizers.get(customizer) == null) {
                throw new IllegalArgumentException("Customizer(" + customizer + ") is NOT Defined.");
            }
        });

        for (int i = 0; i < grpcConfig.getName().size(); i++) {
            String name = grpcConfig.getName().get(i);
            String host = grpcConfig.getHost().get(i);
            Integer port = grpcConfig.getPort().get(i);
            String customizer = grpcConfig.getCustomizer().size() > i ? grpcConfig.getCustomizer().get(i) : null;
            String serviceNames = grpcConfig.getServices().size() > i ? grpcConfig.getServices().get(i) : null;

            InetAddress address;
            try {
                address = InetAddress.getByName(host);
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException("Illegal host(" + host + ")", e);
            }
            InetSocketAddress bindPoint = new InetSocketAddress(address, port);
            servers.put(name, buildServer(name, bindPoint, customizer, serviceNames));
        }

        servers.forEach((name, server) -> {
            try {
                log.info("starting server({})", name);
                server.start();
            } catch (IOException e) {
                log.error("Failed to start server({})", name, e);
                throw new RuntimeException("Failed to start server(" + name + ")", e);
            }
        });
    }

    @PreDestroy
    public void shutdown() {
        servers.forEach((serverName, server) -> server.shutdown());
        servers.forEach((serverName, server) -> {
            try {
                while (!server.awaitTermination(1, TimeUnit.SECONDS)) {
                    log.info("Server({}) is still running.", serverName);
                }
            } catch (InterruptedException e) {
                //do nothing
            }
        });
    }

    @SuppressWarnings("unchecked")
    private Server buildServer(String serverName, InetSocketAddress bindPoint, String customizer, String serviceNames) {
        log.info("Create server with serverName({}) and customizer({}), bind to ({})", serverName, customizer, bindPoint);
        NettyServerBuilder serverBuilder = NettyServerBuilder
                .forAddress(bindPoint);

        if (customizer != null) {
            customizers.get(customizer).customize(serverBuilder);
        }

        if (serviceNames == null || serviceNames.trim().length() == 0) {
            log.info("bind all services to server({})", serverName);
            services.forEach((serviceName, service) -> serverBuilder.addService(service));
        } else {
            String[] nameArr = serviceNames.split(",");
            Set<String> names = new HashSet<>(Arrays.asList(nameArr));
            names.forEach(name -> {
                BindableService service = services.get(name);

                if (service == null) {
                    throw new IllegalArgumentException("Cannot bind Service(" + name + ") to Server(" + serverName + "), it doesn't exist");
                } else {
                    serverBuilder.addService(service);
                }
            });
        }

        return serverBuilder.build();
    }
}
