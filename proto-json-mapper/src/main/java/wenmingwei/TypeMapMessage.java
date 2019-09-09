package wenmingwei;

import com.google.protobuf.Message;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * 因为Proto.MessageDescriptor未携带类信息，只能获得Proto.FullName，
 * 所以，本类用于Map proto.fullName名字和对应的类，绑定关联关系。
 */
@Slf4j
class TypeMapMessage {

    private static final String DEFAULT_URI = "classpath:///proto-message-types.properties";
    private static final String PROPERTY_KEY = "proto.message-types-map";

    private final Map<String, Class<? extends Message>> messageTypeMap;
    private final Map<Class<? extends Message>, Method> messageBuilderMap;

    static final TypeMapMessage INSTANCE = new TypeMapMessage();

    private TypeMapMessage() {
        Map<String, Class<? extends Message>> tempMessageTypeMap = new HashMap<>();
        Map<Class<? extends Message>, Method> tempMessageBuilderMap = new HashMap<>();
        try {
            readFromProperties(tempMessageTypeMap, tempMessageBuilderMap);
        } catch (Exception ex) {
            log.error("Something wrong during loading message type map, have to exit whole system.", ex);
            System.exit(1);
        }

        messageTypeMap = Collections.unmodifiableMap(tempMessageTypeMap);
        messageBuilderMap = Collections.unmodifiableMap(tempMessageBuilderMap);
    }

    //查找绑定信息
    Class<? extends Message> lookupMessageType(String messageFullName) {
        return messageTypeMap.get(messageFullName);
    }

    //创建Message.Builder, 反射
    Message.Builder newBuilder(Class<? extends Message> clazz) {

        //获得缓存的Builder方法，可以提速
        Method method = messageBuilderMap.get(clazz);
        if (method == null) {
            throw new IllegalArgumentException("Cannot find newBuilder method for class(" + clazz.getCanonicalName() + ")");
        }
        try {
            return (Message.Builder) method.invoke(clazz);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to invoke newBuilder of class(" + clazz.getCanonicalName() + ")", e);
        }
    }

    //加载绑定信息
    private void readFromProperties(
            Map<String, Class<? extends Message>> tempMessageTypeMap,
            Map<Class<? extends Message>, Method> tempMessageBuilderMap
    ) throws Exception {
        if (log.isDebugEnabled()) {
            log.debug("Loading properties of TypeMapMessage.");
        }
        Properties properties = new PropertiesLoader().loadProperties(PROPERTY_KEY, DEFAULT_URI);

        ClassLoader threadContextClassLoader = Thread.currentThread().getContextClassLoader();
        for (String messageFullName : properties.stringPropertyNames()) {
            String clazzName = properties.getProperty(messageFullName);

            if (clazzName == null) {
                throw new IllegalArgumentException("Class name is not set, message full name is (" + messageFullName + ")");
            }

            Class<?> clazz = threadContextClassLoader.loadClass(clazzName);

            if (Message.class.isAssignableFrom(clazz)) {
                @SuppressWarnings("unchecked")
                Class<? extends Message> messageClazz = (Class<? extends Message>) clazz;

                Method method = messageClazz.getMethod("newBuilder");
                tempMessageTypeMap.put(messageFullName, messageClazz);
                tempMessageBuilderMap.put(messageClazz, method);
            } else {
                throw new IllegalArgumentException("Class(" + clazzName + ") is not a Protobuf.Message class");
            }
        }
    }
}
