package wenmingwei.consumers;

import io.grpc.Channel;

public interface Customizer {

    Channel customize();
}
