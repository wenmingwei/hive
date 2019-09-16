package wenmingwei.providers;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Configuration
@Data
@ConfigurationProperties(prefix = "grpc.server")
public class GrpcProviderConfig {

    private List<String> host = new ArrayList<>();
    private List<Integer> port = new ArrayList<>();
    private List<String> name = new ArrayList<>();
    private List<String> customizer = new ArrayList<>();
    private List<String> services = new ArrayList<>();

    @PostConstruct
    public void validate() {
        if (name.size() != host.size() || name.size() != port.size()) {
            throw new IllegalArgumentException("host, port, name are required for each server.");
        }

        if (name.size() == 0) {
            try {
                host.add(InetAddress.getLocalHost().getHostName());
                port.add(8080);
                name.add("default-grpc-server");
            } catch (UnknownHostException e) {
                throw new IllegalStateException("Failed to create default settings.", e);
            }
        }

        Set<String> nameSet = new HashSet<>(name);

        if (nameSet.size() != name.size()) {
            throw new IllegalArgumentException("There are duplicate server names in settings.");
        }
    }
}
