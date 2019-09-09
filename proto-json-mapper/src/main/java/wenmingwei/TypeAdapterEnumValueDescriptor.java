package wenmingwei;

import com.google.gson.*;
import com.google.protobuf.Descriptors;
import com.google.protobuf.ProtocolMessageEnum;

import java.lang.reflect.Type;

/**
 * Proto.Message.Enum的编解码
 * <p>
 * 因为Proto.Message.Builder需要EnumValueDescriptor对象作为Enum存储，故需要提供特定的编解码程序
 */
public class TypeAdapterEnumValueDescriptor
        implements JsonSerializer<Descriptors.EnumValueDescriptor>, JsonDeserializer<Descriptors.EnumValueDescriptor> {

    /**
     * 将Enum编码为String
     *
     * @param src       Enum描述对象，包含Enum对象
     * @param typeOfSrc 忽略
     * @param context   忽略
     * @return Enum.name对应的String
     */
    @Override
    public JsonElement serialize(Descriptors.EnumValueDescriptor src, Type typeOfSrc, JsonSerializationContext context) {
        return new JsonPrimitive(src.getName());
    }

    /**
     * 将字符串解码为Proto.Message.EnumValueDescriptor描述对象
     *
     * @param json    JsonString，字符串格式，在上层程序保证。
     * @param typeOfT 必须为Enum的子类，在上层程序保证。
     * @param context 忽略
     * @return EnumValueDescriptor对象，包含Enum
     * @throws JsonParseException 解码失败时，抛出
     */
    @Override
    public Descriptors.EnumValueDescriptor deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {

        @SuppressWarnings("unchecked")
        ProtocolMessageEnum enumObj = (ProtocolMessageEnum) Enum.valueOf((Class) typeOfT, json.getAsString());

        return enumObj.getValueDescriptor();
    }
}
