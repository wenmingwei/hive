package wenmingwei;

import com.google.gson.*;
import com.google.protobuf.*;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Type;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Proto.Message与JsonElement之间编解码
 * <p>
 * 目前支持:
 * 1. Repeated Field
 * 2. Map Field
 * 3. Enum Field
 * 4. 正常 Field（Int/Long/Double/String...）
 */
@Slf4j
public class TypeAdapterProtoMessage
        implements JsonSerializer<Message>, JsonDeserializer<Message> {

    /**
     * 将Proto.Message编码为JsonObject
     *
     * @param message   Proto.Message
     * @param typeOfSrc 忽略
     * @param context   用于迭代编码
     * @return 返回JsonObject
     */
    @Override
    public JsonElement serialize(Message message, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject ret = new JsonObject();
        Map<Descriptors.FieldDescriptor, Object> fields = message.getAllFields();
        for (Map.Entry<Descriptors.FieldDescriptor, Object> entry : fields.entrySet()) {
            Descriptors.FieldDescriptor fieldDescriptor = entry.getKey();
            Object value = entry.getValue();
            ret.add(fieldDescriptor.getJsonName(), context.serialize(value));
        }
        return ret;
    }

    /**
     * 将Json解码为Proto.Message
     *
     * @param json    Json内容
     * @param typeOfT 指定Proto.Message类型
     * @param context 用于迭代解码
     * @return Proto.Message对象
     * @throws JsonParseException 解码失败时，抛出
     */
    @Override
    public Message deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {

        if (log.isDebugEnabled()) {
            log.debug("Decoding json to proto.message({})", typeOfT);
        }

        if (log.isTraceEnabled()) {
            log.trace("Json content: {}", json);
        }

        //传入必须为JsonObject，否则抛出异常
        JsonObject jsonObject = json.getAsJsonObject();

        //typeOfT必须为Proto.Message的子类，否则抛出异常
        @SuppressWarnings("unchecked")
        Class<? extends Message> messageType = (Class<? extends Message>) typeOfT;

        Message.Builder builder = TypeMapMessage.INSTANCE.newBuilder(messageType);

        List<Descriptors.FieldDescriptor> fieldDescriptors = builder.getDescriptorForType().getFields();

        for (Descriptors.FieldDescriptor fieldDescriptor : fieldDescriptors) {
            String jsonName = fieldDescriptor.getJsonName();
            JsonElement jsonElement = jsonObject.get(jsonName);

            if (jsonElement != null) {
                if (log.isDebugEnabled()) {
                    log.debug("Found field({})", jsonName);
                }
                Descriptors.FieldDescriptor.JavaType javaType = fieldDescriptor.getJavaType();

                //如果数据类型为Map，需要特别处理，MapEntry没有可访问的构造函数，并且无法携带泛型
                //另外，Map类型在Proto.Message的访问方式为List<MapEntry>, getRepeatedField
                if (Descriptors.FieldDescriptor.JavaType.MESSAGE.equals(javaType) && fieldDescriptor.isMapField()) {
                    Descriptors.Descriptor messageDescriptor = fieldDescriptor.getMessageType();
                    JsonArray elements = jsonElement.getAsJsonArray();
                    Iterator<JsonElement> iterator = elements.iterator();

                    List<Descriptors.FieldDescriptor> mapFieldDescriptors = messageDescriptor.getFields();

                    //必须有且仅有两个Field，Key和value，否则报错
                    if (mapFieldDescriptors.size() != 2) {
                        StringBuilder sb = new StringBuilder();
                        for (Descriptors.FieldDescriptor mapFieldDescriptor : mapFieldDescriptors) {
                            sb.append("\r\nname: ").append(mapFieldDescriptor.getJsonName())
                                    .append(", type: ").append(mapFieldDescriptor.getJavaType());
                            if (Descriptors.FieldDescriptor.JavaType.MESSAGE.equals(mapFieldDescriptor.getJavaType())) {
                                sb.append(", message: ").append(mapFieldDescriptor.getMessageType().getFullName());
                            }
                        }
                        log.error("Should not be here, found corrupted descriptor. {}", sb.toString());
                        throw new IllegalArgumentException("size of Map Field Descriptor is NOT 2");
                    }

                    Descriptors.FieldDescriptor keyFieldDescriptor = mapFieldDescriptors.get(0);
                    if (!"key".equals(keyFieldDescriptor.getJsonName())) {
                        log.error("keyField json name is NOT 'key', actual value is {}", keyFieldDescriptor.getJsonName());
                        throw new IllegalArgumentException("KeyField json name is NOT 'key', actual value is " + keyFieldDescriptor.getJsonName());
                    }

                    Descriptors.FieldDescriptor valueFieldDescriptor = mapFieldDescriptors.get(1);
                    if (!"value".equals(valueFieldDescriptor.getJsonName())) {
                        log.error("valueField json name is NOT 'value', actual value is {}", valueFieldDescriptor.getJsonName());
                        throw new IllegalArgumentException("valueField json name is NOT 'value', actual value is " + valueFieldDescriptor.getJsonName());
                    }

                    Type keyType = buildType(keyFieldDescriptor);
                    Type valueType = buildType(valueFieldDescriptor);

                    WireFormat.FieldType keyFieldType = keyFieldDescriptor.getLiteType();
                    WireFormat.FieldType valueFieldType = valueFieldDescriptor.getLiteType();

                    while (iterator.hasNext()) {
                        JsonElement element = iterator.next();

                        JsonObject entryObject = element.getAsJsonObject();
                        JsonElement keyElement = entryObject.get("key");
                        JsonElement valueElement = entryObject.get("value");

                        Object key = context.deserialize(keyElement, keyType);
                        Object value = context.deserialize(valueElement, valueType);
                        MapEntry entry = MapEntry.newDefaultInstance(messageDescriptor, keyFieldType, key, valueFieldType, value);
                        builder.addRepeatedField(fieldDescriptor, entry);
                    }
                } else {
                    Type fieldType = buildType(fieldDescriptor);
                    setField(fieldDescriptor, jsonElement, builder, fieldType, context);
                }
            }
        }

        return builder.build();
    }

    /**
     * 判定数据类型
     *
     * @param fieldDescriptor 属性描述器
     * @return Java.Reflect.Type数据类型
     */
    private Type buildType(Descriptors.FieldDescriptor fieldDescriptor) {
        Descriptors.FieldDescriptor.JavaType javaType = fieldDescriptor.getJavaType();
        switch (javaType) {
            case INT:
                return Integer.class;
            case LONG:
                return Long.class;
            case FLOAT:
                return Float.class;
            case DOUBLE:
                return Double.class;
            case BOOLEAN:
                return Boolean.class;
            case STRING:
                return String.class;
            case BYTE_STRING:
                return ByteString.class;
            case ENUM:
                return TypeMapEnum.INSTANCE.lookupEnumType(fieldDescriptor.getEnumType().getFullName());
            case MESSAGE:
                return TypeMapMessage.INSTANCE.lookupMessageType(fieldDescriptor.getMessageType().getFullName());
        }

        throw new IllegalStateException("Should not be here.");
    }

    private void setField(
            Descriptors.FieldDescriptor fieldDescriptor,
            JsonElement jsonElement,
            Message.Builder builder,
            Type typeOfT,
            JsonDeserializationContext context

    ) {
        if (fieldDescriptor.isRepeated()) {
            //如果时Repeated字段，List/Array
            JsonArray elements = jsonElement.getAsJsonArray();

            for (JsonElement element : elements) {
                builder.addRepeatedField(fieldDescriptor, context.deserialize(element, typeOfT));
            }
        } else {
            //普通字段
            builder.setField(fieldDescriptor, context.deserialize(jsonElement, typeOfT));
        }
    }
}
