# Minecraft TCP Server Mod

一个 Fabric 模组，使 Minecraft 能够与外部程序建立 TCP 连接。
A Fabric mod that enables Minecraft to make TCP connections with external programs.

## 环境要求 Requirements

- Minecraft 1.19.x (Tested version: 1.19.3)
- Fabric API

## 使用方法 Usage

**命令权限等级：3**
**Command permission level: 3**

### 命令列表 Commands

#### `/tcpserver create [port]`
创建一个 TCP 服务器，并在指定端口监听。
监听期间会自动接受所有客户端连接。
若未指定端口，则会自动选择一个可用端口。
Creates a TCP server and listens on the specified port.  
While listening, all client connections are automatically accepted.  
If no port is specified, an available port will be chosen automatically.

#### `/tcpserver close [port]`
关闭指定端口上的 TCP 服务器。
这会在5毫秒内中断所有可能正在进行的数据传输，并断开客户端连接。
若未指定端口，则关闭所有 TCP 服务器。
Closes the TCP server on the specified port.  
This will interrupt any ongoing data transfers and disconnect clients in 5 ms.  
If no port is specified, all TCP servers are closed.

#### `/tcpserver list`
列出所有正在监听的 TCP 服务器端口。
Lists all TCP server ports that are currently listening.

#### `/tcpserver list <port>`
列出所有连接到指定端口的客户端地址。
Lists all client addresses connected to the specified port.

#### `/tcpserver send <port> <nbt>`
将 NBT 数据发送给所有连接到指定端口的客户端。
NBT 必须为复合标签（`NbtCompound`），数据将以 SNBT 字符串的格式发送。
数据将以一个换行符（`\n`）作为结束标记。
Sends NBT data to all clients connected to the specified port.  
The NBT must be a compound tag (`NbtCompound`). Data is sent as an SNBT string.
Data will end with a line break (`\n`).

#### `/tcpserver send <port> <entity|block|storage> <target> <path>`
发送指定路径下的 NBT 数据。
规则同上。
Sends NBT data from the specified path.  
Rules are the same as above.

#### `/tcpserver recvpath <port> [entity|block|storage] <target> <path>`
设置指定端口接收数据时的存放位置。
当任意连接到该端口的客户端发送 SNBT 格式的数据时，会以 NBT 复合标签的形式将数据保存到指定路径。
若客户端发送了非 SNBT 数据，则丢弃。
若未指定路径，则丢弃接收到的数据。
Sets the storage location for data received on the specified port.  
When any client connected to the port sends data in SNBT format, it is stored as an NBT compound tag at the specified path.  
If the client sends non-SNBT data, it is discarded.  
If no path is specified, received data is discarded.

#### `/tcpserver recvfunc <port> [function]`
设置指定端口接收数据时的回调函数。
调用函数时权限等级为 3。
当任意连接到该端口的客户端发送 SNBT 格式的数据时，调用指定的函数。
若客户端发送了非 SNBT 数据，则不回调。
即使未使用`recvpath`指定数据接收路径，也会回调函数。
若未指定函数，则不回调。
Sets a callback function for data received on the specified port.  
The function is called with permission level 3.  
When any client connected to the port sends data in SNBT format, the specified function is called.  
If the client sends non-SNBT data, no callback is triggered.  
The callback is invoked even if `recvpath` is not used to store the data.  
If no function specified, no callback will be triggered.

### 注意事项 Notes
- 服务器关闭时，所有 TCP 服务器将自动关闭。
- 本模组无客户端验证机制，**请勿将创建了 TCP 服务器的端口开放到公网**。
- All TCP servers are automatically closed when the Minecraft server shuts down.
- This mod has no client authentication mechanism; **do not expose ports with active TCP servers to the public internet**.

## 示例 Examples

```mcfunction
tcpserver create 12345               # 创建一个 TCP 服务器 Create a TCP server on port 12345
tcpserver recvpath 12345 block 0 64 0 Items[0]  # 将接收到的数据存入容器 Store received data in a container
tcpserver recvfunc 12345 mypack:callback        # 设置回调函数 Set a callback function
tcpserver send 12345 {action:"request_item"}    # 发送数据 Send data to all clients
```

## 开源协议 License

MIT

## 其它文件说明 Additional Files

- `mcconnection.py` —— 包含了一个 Python 实现的 TCP 客户端，可与本模组的 TCP 服务器进行通信。
- `mcconnection.py` – A Python implementation of a TCP client that can communicate with this mod's TCP server.