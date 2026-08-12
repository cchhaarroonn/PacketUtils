# PacketUtils

A utility class for serializing and deserializing network packets in a format similar to the Minecraft protocol. It provides methods for working with `VarInt` values, strings, byte arrays, and for building handshake packets.

Package: `utils`

---

## Table of Contents

- [Overview](#overview)
- [Requirements](#requirements)
- [API Reference](#api-reference)
  - [writeVarInt](#writevarintdataoutputstream-out-int-value)
  - [readVarInt](#readvarintdatainputstream-in)
  - [writeString](#writestringdataoutputstream-out-string-value)
  - [readString](#readstringdatainputstream-in)
  - [writeByteArray](#writebytearraydataoutputstream-out-byte-data)
  - [readByteArray](#readbytearraydatainputstream-in)
  - [writeHandshakePacket](#writehandshakepacketdataoutputstream-out-string-ip-int-port-int-protocol-int-state)
  - [writePacket](#writepacketbyte-packetdata-dataoutputstream-out)
- [VarInt Format](#varint-format)
- [Usage Example](#usage-example)
- [Known Limitations and Notes](#known-limitations-and-notes)

---

## Overview

`PacketUtils` bundles a set of static methods that let you read and write basic data types over `DataInputStream` / `DataOutputStream` interfaces, using **VarInt** encoding for lengths and numeric values — the same principle used by the Minecraft network protocol. The class is meant as a building block for implementing your own packet-based protocol over a TCP socket.

## Requirements

- Java 8 or newer
- Standard libraries: `java.io`, `java.nio.charset`

No external dependencies.

---

## API Reference

### `writeVarInt(DataOutputStream out, int value)`

Writes an `int` value in **VarInt** format (variable length, 1–5 bytes).

**Parameters:**
| Parameter | Type | Description |
|---|---|---|
| `out` | `DataOutputStream` | Output stream to write to |
| `value` | `int` | Value to encode |

**Throws:** `IOException` if writing to the stream fails.

**How it works:** The value is split into groups of 7 bits. As long as bits remain outside the last 7 (checked via `value & 0xFFFFFF80`), a byte is written with the MSB set (`0x80`), signaling "more bytes follow," and the value is shifted (`>>>`) right by 7 bits. The final byte is written without the MSB set.

---

### `readVarInt(DataInputStream in)`

Reads a **VarInt** value from the stream and returns it as an `int`.

**Parameters:**
| Parameter | Type | Description |
|---|---|---|
| `in` | `DataInputStream` | Input stream to read from |

**Returns:** the decoded `int`.

**Throws:**
- `IOException` — error reading from the stream
- `RuntimeException("VarInt too big")` — if the VarInt spans more than 5 bytes (a sign of corrupted or malicious data)

**How it works:** Reads bytes one at a time, takes the lower 7 bits of each (`k & 0x7F`, written here as `Byte.MAX_VALUE`), shifts them to the correct position (`<< j*7`), and ORs them into the result. The loop continues as long as the MSB is set (`k & 0x80`).

---

### `writeString(DataOutputStream out, String value)`

Writes a string as a VarInt length (number of bytes in UTF-8 encoding) followed by the raw UTF-8 bytes.

**Parameters:**
| Parameter | Type | Description |
|---|---|---|
| `out` | `DataOutputStream` | Output stream |
| `value` | `String` | String to write |

**Throws:** `IOException`

---

### `readString(DataInputStream in)`

Reads a string written by `writeString` — first the VarInt length, then the corresponding number of UTF-8 bytes.

**Parameters:**
| Parameter | Type | Description |
|---|---|---|
| `in` | `DataInputStream` | Input stream |

**Returns:** the decoded `String`.

**Throws:** `IOException`

---

### `writeByteArray(DataOutputStream out, byte[] data)`

Writes a byte array as a VarInt length followed by the raw contents of the array.

**Parameters:**
| Parameter | Type | Description |
|---|---|---|
| `out` | `DataOutputStream` | Output stream |
| `data` | `byte[]` | Data to write |

**Throws:** `IOException`

---

### `readByteArray(DataInputStream in)`

Reads a byte array written by `writeByteArray`.

**Parameters:**
| Parameter | Type | Description |
|---|---|---|
| `in` | `DataInputStream` | Input stream |

**Returns:** the read `byte[]`.

**Throws:** `IOException`

---

### `writeHandshakePacket(DataOutputStream out, String ip, int port, int protocol, int state)`

Builds and writes a **handshake packet** — the initial packet a client sends to a server when establishing a connection (e.g. packet ID `0x00` in the Minecraft protocol).

**Parameters:**
| Parameter | Type | Description |
|---|---|---|
| `out` | `DataOutputStream` | Output stream |
| `ip` | `String` | Server address/hostname |
| `port` | `int` | Server port (written as a `short`) |
| `protocol` | `int` | Protocol version |
| `state` | `int` | Next state (e.g. 1 = status, 2 = login) |

**Write order:**
1. VarInt `0` — packet ID of the handshake packet
2. VarInt `protocol`
3. String `ip`
4. `short port`
5. VarInt `state`

**Throws:** `IOException`

**Note:** this method only writes the packet *body* (payload), without a total-length prefix. To send it over the network, the payload must first be serialized into a temporary buffer (e.g. `ByteArrayOutputStream`) and then passed to `writePacket`, which adds the length.

---

### `writePacket(byte[] packetData, DataOutputStream out)`

Wraps already-serialized packet data into the final format ready to send: VarInt length + raw data.

**Parameters:**
| Parameter | Type | Description |
|---|---|---|
| `packetData` | `byte[]` | The complete, already-serialized packet contents (packet ID + fields) |
| `out` | `DataOutputStream` | Output stream (e.g. a socket stream) |

**Throws:** `IOException`

---

## VarInt Format

VarInt is a variable-length encoding for integers, where the number is split into groups of 7 bits:

- Each byte carries 7 bits of data + 1 flag bit (MSB)
- MSB = `1` → more bytes follow
- MSB = `0` → this is the last byte
- Bytes are ordered from least significant group first
- The maximum length for an `int` is 5 bytes (this implementation throws past that)

Example: the number `300` (binary `100101100`) encodes into two bytes: `10101100 00000010`.

---

## Usage Example

```java
import java.io.*;
import java.net.Socket;
import utils.PacketUtils;

public class Example {
    public static void main(String[] args) throws IOException {
        try (Socket socket = new Socket("play.example.com", 25565);
             DataOutputStream out = new DataOutputStream(socket.getOutputStream());
             DataInputStream in = new DataInputStream(socket.getInputStream())) {

            // 1. Serialize the handshake packet into a temporary buffer
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            DataOutputStream bufferOut = new DataOutputStream(buffer);
            PacketUtils.writeHandshakePacket(bufferOut, "play.example.com", 25565, 763, 1);

            // 2. Send the packet (length + contents)
            PacketUtils.writePacket(buffer.toByteArray(), out);
            out.flush();

            // 3. Read the response
            int packetLength = PacketUtils.readVarInt(in);
            // ... further processing of the payload of length packetLength
        }
    }
}
```

---

## Known Limitations and Notes

- **Unused variable:** in `writeString` a local variable `after` is allocated but never used — it can be safely removed.
- **Throwing `RuntimeException`:** `readVarInt` throws an unchecked exception instead of a proper/checked one (e.g. an `IOException` subtype), which can make error handling harder further up the call stack.
- **No validation of negative lengths:** `readString` and `readByteArray` don't check whether the decoded length is negative or unreasonably large before allocating the `byte[]`, which with malicious or corrupted input can lead to a `NegativeArraySizeException` or excessive memory use (a DoS risk). It's recommended to add an upper bound (e.g. comparable to the Minecraft protocol, which caps strings at roughly 32767 characters).
- **Not thread-safe per stream:** the class itself is stateless (all methods are static), but a shared `DataInputStream`/`DataOutputStream` object must be synchronized if accessed by multiple threads concurrently.
- **`writeHandshakePacket` does not include a packet length prefix** — it must be combined with `writePacket` (see example above).

---
