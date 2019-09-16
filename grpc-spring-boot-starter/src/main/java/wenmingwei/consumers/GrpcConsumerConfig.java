package wenmingwei.consumers;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "grpc.consumers")
@Data
public class GrpcConsumerConfig {

    private Map<String, String> service = new HashMap<>();
}
