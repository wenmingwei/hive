package wenmingwei.consumers;

import com.google.common.base.Strings;
import io.grpc.Channel;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.AbstractStub;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.beans.factory.support.AutowireCandidateResolver;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;

@Slf4j
@Configuration
@ConditionalOnProperty(value = "grpc.consumers.enabled", havingValue = "true", matchIfMissing = true)
public class GrpcConsumerAutoConfiguration implements BeanFactoryPostProcessor {

    private static final String METHOD_NEW_ASYNC_STUB = "newStub";
    private static final String METHOD_NEW_BLOCKING_STUB = "newBlockingStub";
    private static final String METHOD_NEW_FUTURE_STUB = "newFutureStub";
    private static final String FIELD_SERVICE_NAME = "SERVICE_NAME";

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory factory) throws BeansException {
        if (factory instanceof DefaultListableBeanFactory) {
            DefaultListableBeanFactory listableBeanFactory = (DefaultListableBeanFactory) factory;
            AutowireCandidateResolver original = listableBeanFactory.getAutowireCandidateResolver();
            GrpcAutowireCandidateResolver built = new GrpcAutowireCandidateResolver(original, listableBeanFactory);
            listableBeanFactory.setAutowireCandidateResolver(built);
        }
    }

    private static class GrpcAutowireCandidateResolver implements AutowireCandidateResolver {
        private final AutowireCandidateResolver resolver;
        private final BeanFactory beanFactory;

        GrpcAutowireCandidateResolver(AutowireCandidateResolver resolver, DefaultListableBeanFactory beanFactory) {
            this.resolver = resolver;
            this.beanFactory = beanFactory;
        }

        @Override
        public boolean isAutowireCandidate(BeanDefinitionHolder bdHolder, DependencyDescriptor descriptor) {
            return this.resolver.isAutowireCandidate(bdHolder, descriptor);
        }

        @Override
        public Object getLazyResolutionProxyIfNecessary(DependencyDescriptor descriptor, String beanName) {
            return this.resolver.getLazyResolutionProxyIfNecessary(descriptor, beanName);
        }

        @Override
        public Object getSuggestedValue(DependencyDescriptor descriptor) {
            Field field = descriptor.getField();
            if (field != null) {
                Class<?> clazz = field.getType();
                if (clazz == null) {
                    throw new IllegalArgumentException("Unknown field type(" + field + ")");
                }
                if (AbstractStub.class.isAssignableFrom(clazz)) {
                    Class<?> factoryClass = clazz.getEnclosingClass();
                    if (factoryClass != null) {
                        if (log.isDebugEnabled()) {
                            log.debug("Got factory class(" + factoryClass.getCanonicalName()
                                    + ") of Stub(" + clazz.getCanonicalName() + ")");
                        }

                        return tryBuildObjectWithType(factoryClass, clazz);
                    } else {
                        log.error("Cannot get enclosing class of(" + clazz.getCanonicalName() + ").");
                    }
                    if (log.isDebugEnabled()) {
                        log.debug("Need to built object for Grpc Stub(" + clazz.getCanonicalName() + ")");
                    }
                }
            }

            return resolver.getSuggestedValue(descriptor);
        }

        private Object tryBuildObjectWithType(Class<?> factoryClass, Class<?> clazz) {
            try {
                Method[] methods = new Method[]{
                        factoryClass.getDeclaredMethod(METHOD_NEW_ASYNC_STUB, Channel.class),
                        factoryClass.getDeclaredMethod(METHOD_NEW_BLOCKING_STUB, Channel.class),
                        factoryClass.getDeclaredMethod(METHOD_NEW_FUTURE_STUB, Channel.class)
                };

                Field fieldServiceName = factoryClass.getDeclaredField(FIELD_SERVICE_NAME);

                String serviceName = (String) fieldServiceName.get(factoryClass);

                if (log.isDebugEnabled()) {
                    log.debug("Going to find tracing with serviceName(" + serviceName
                            + "), factory(" + factoryClass.getCanonicalName()
                            + "), class(" + clazz.getCanonicalName() + ")");
                }

                if (Strings.isNullOrEmpty(serviceName)) {
                    throw new IllegalAccessException("Cannot find service name of (" + factoryClass.getCanonicalName() + ")");
                }

                GrpcConsumerConfig bean = this.beanFactory.getBean(GrpcConsumerConfig.class);
                String channelBuilder = bean.getService().get(serviceName);
                if (channelBuilder == null) {
                    throw new IllegalArgumentException("Service(" + serviceName + ")'s provider is not specified.");
                }

                Channel channel;
                if (channelBuilder.startsWith("grpc://")) {
                    URI uri = URI.create(channelBuilder);

                    channel = NettyChannelBuilder
                            .forAddress(uri.getHost(), uri.getPort())
                            .usePlaintext()
                            .build();
                } else {
                    Customizer customizer = this.beanFactory.getBean(channelBuilder, Customizer.class);
                    channel = customizer.customize();
                }

                if (log.isDebugEnabled()) {
                    log.debug("Found channel({}) for service({})", channel, serviceName);
                }

                for (Method method : methods) {
                    if (method.getReturnType().equals(clazz)) {
                        return method.invoke(factoryClass, channel);
                    }
                }

                throw new RuntimeException("Failed to build stub.");
            } catch (Exception ex) {
                log.error("Failed to build stub with parameter(factory(" + factoryClass.getCanonicalName()
                        + "), class(" + clazz.getCanonicalName()
                        + "))", ex);
                throw new IllegalArgumentException(ex);
            }
        }
    }
}
