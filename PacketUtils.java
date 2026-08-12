import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class PacketUtils {

    public static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while ((value & 0xFFFFFF80) != 0) {
            out.writeByte(value & 0x7F | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }

    public static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] data = value.getBytes(StandardCharsets.UTF_8);
        byte[] after = new byte[data.length];
        writeVarInt(out, data.length);
        out.write(data, 0, data.length);
    }

    public static int readVarInt(DataInputStream in) throws IOException {
        byte k;
        int i = 0;
        int j = 0;
        do {
            k = in.readByte();
            i |= (k & Byte.MAX_VALUE) << j++ * 7;
            if (j > 5)
                throw new RuntimeException("VarInt too big");
        } while ((k & 0x80) == 128);
        return i;
    }

    public static String readString(DataInputStream in) throws IOException {
        int len = readVarInt(in);
        byte[] data = new byte[len];
        in.readFully(data);
        return new String(data, 0, len, StandardCharsets.UTF_8);
    }

    public static byte[] readByteArray(DataInputStream in) throws IOException {
        int len = readVarInt(in);
        byte[] data = new byte[len];
        in.readFully(data);
        return data;
    }

    public static void writeByteArray(DataOutputStream out, byte[] data) throws IOException {
        writeVarInt(out, data.length);
        out.write(data, 0, data.length);
    }

    public static void writeHandshakePacket(DataOutputStream out, String ip, int port, int protocol, int state) throws IOException {
        writeVarInt(out, 0);
        writeVarInt(out, protocol);
        writeString(out, ip);
        out.writeShort(port);
        writeVarInt(out, state);
    }

    public static void writePacket(byte[] packetData, DataOutputStream out) throws IOException {
        writeVarInt(out, packetData.length);
        out.write(packetData);
    }

}
