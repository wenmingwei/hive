package wenmingwei;

import com.google.gson.*;
import com.google.protobuf.ByteString;

import java.lang.reflect.Type;
import java.util.Base64;

/**
 * ByteString和Json之间的互相转换。
 * <p>
 * 转换为如下格式：
 * <p>
 * {
 * "b64": "Base64编码字符串"
 * }
 * </p>
 */
public class TypeAdapterByteString
        implements JsonSerializer<ByteString>, JsonDeserializer<ByteString> {

    /**
     * 将JsonObject解码为ByteString
     *
     * @param json    JsonObject
     * @param typeOfT 忽略
     * @param context 忽略
     * @return ByteString
     * @throws JsonParseException 解码失败时，抛出异常
     */
    @Override
    public ByteString deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject obj = json.getAsJsonObject();
        return ByteString.copyFrom(Base64.getDecoder().decode(obj.get("b64").getAsString()));
    }

    /**
     * 将ByteString编码为格式为
     * <p>
     * {
     * "b64" : "Base64编码字符串"
     * }
     * </p>
     * 的JsonObject
     *
     * @param src       ByteString
     * @param typeOfSrc 忽略
     * @param context   忽略
     * @return 返回JsonObject
     */
    @Override
    public JsonElement serialize(ByteString src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject ret = new JsonObject();
        ret.addProperty("b64", Base64.getEncoder().encodeToString(src.toByteArray()));
        return ret;
    }
}
