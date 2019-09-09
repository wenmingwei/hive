package wenmingwei;

import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * 因为Proto.Message.EnumDescriptor未携带类信息，只能获得Proto.FullName，
 * 所以，本类用于Map proto.fullName名字和对应的类，绑定关联关系。
 */
@Slf4j
class TypeMapEnum {

    private static final String PROPERTY_KEY = "proto.enum-types-map";
    private static final String DEFAULT_URI = "classpath:///proto-enum-types.properties";

    private final Map<String, Class<Enum>> enumTypeMap;

    static final TypeMapEnum INSTANCE = new TypeMapEnum();

    private TypeMapEnum() {
        Map<String, Class<Enum>> tempEnumTypeMap = new HashMap<>();
        try {
            readFromProperties(tempEnumTypeMap);
        } catch (Exception ex) {
            log.error("Something wrong during loading enum type map, have to exit whole system.", ex);
            System.exit(1);
        }

        enumTypeMap = Collections.unmodifiableMap(tempEnumTypeMap);
    }

    //查找绑定信息
    Type lookupEnumType(String enumFullName) {
        return enumTypeMap.get(enumFullName);
    }

    //加载绑定信息
    private void readFromProperties(
            Map<String, Class<Enum>> tempEnumTypeMap
    ) throws Exception {
        Properties properties = new PropertiesLoader().loadProperties(PROPERTY_KEY, DEFAULT_URI);

        ClassLoader threadContextClassLoader = Thread.currentThread().getContextClassLoader();
        for (String enumFullName : properties.stringPropertyNames()) {
            String clazzName = properties.getProperty(enumFullName);

            if (clazzName == null) {
                throw new IllegalArgumentException("Class name is not set, enum full name is (" + enumFullName + ")");
            }

            Class<?> clazz = threadContextClassLoader.loadClass(clazzName);

            if (Enum.class.isAssignableFrom(clazz)) {
                @SuppressWarnings("unchecked")
                Class<Enum> enumClazz = (Class<Enum>) clazz;
                tempEnumTypeMap.put(enumFullName, enumClazz);
            } else {
                throw new IllegalArgumentException("Class(" + clazzName + ") is not a Enum");
            }
        }
    }
}
