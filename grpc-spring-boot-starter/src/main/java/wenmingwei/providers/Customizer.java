package wenmingwei.providers;

import io.grpc.ServerBuilder;

public interface Customizer<T extends ServerBuilder<T>> {

    void customize(ServerBuilder<T> serverBuilder);
}
