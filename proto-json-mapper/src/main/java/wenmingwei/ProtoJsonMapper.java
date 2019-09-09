package wenmingwei;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import com.google.protobuf.ProtocolMessageEnum;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Proto.Message 与 Json 互相转换的 Mapper.
 * <p>
 * encode编码:
 * Proto.Message -> Json
 * <p>
 * decode解码:
 * Json -> Proto.Message
 */
@Slf4j
public class ProtoJsonMapper {
    private final Gson gson;

    public ProtoJsonMapper() {

        if (log.isDebugEnabled()) {
            log.debug("Create GSON object to translate json and proto.message.");
        }

        this.gson = new GsonBuilder()
                //处理 ByteString
                .registerTypeHierarchyAdapter(ByteString.class, new TypeAdapterByteString())
                //处理 Proto.Message
                .registerTypeHierarchyAdapter(Message.class, new TypeAdapterProtoMessage())
                //处理 Proto.Message.Enum 的解码
                .registerTypeHierarchyAdapter(ProtocolMessageEnum.class, new TypeAdapterEnumValueDescriptor())
                //处理 Proto.Message.Enum 的编码
                .registerTypeHierarchyAdapter(Descriptors.EnumValueDescriptor.class, new TypeAdapterEnumValueDescriptor())
                .setPrettyPrinting()
                .create();
    }

    /**
     * 将Proto.Message 编码为 JsonString
     *
     * @param message Proto.Message对象
     * @return Json String
     */
    public String encode(Message message) {
        return this.gson.toJson(message);
    }

    /**
     * 将 JsonString 解码为 Proto.Message
     * <p>
     * IMPORTANT: MessageDescriptor用来帮助TypeAdapterProtoMessage确定转换Proto.Message的类型
     *
     * @param json              Json Content, 直接使用的Netty Http 解码得到的ByteBuf，减少Memory-Copy
     * @param messageDescriptor Proto.Message格式描述
     * @return Proto.Message对象
     * @throws IOException 解码异常时返回
     */
    public Message decode(ByteBuf json, Descriptors.Descriptor messageDescriptor) throws IOException {

        Class<? extends Message> messageType = TypeMapMessage.INSTANCE.lookupMessageType(messageDescriptor.getFullName());

        if (messageType == null) {
            throw new IllegalArgumentException("Cannot find class type for message (" + messageDescriptor.getFullName() + ")");
        }

        try (InputStreamReader reader = new InputStreamReader(
                new ByteBufInputStream(json),
                StandardCharsets.UTF_8  //只支持UTF8编码的Json请求。
        )) {
            return gson.fromJson(reader, messageType);
        }
    }
}
