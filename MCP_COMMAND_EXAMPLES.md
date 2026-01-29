# MCP Command Line Parsing Examples

The MCP stdio command parser now correctly handles quoted arguments across all platforms.

## Usage

Configure MCP stdio servers in your `application.properties`:

```properties
chappie.mcp.servers=stdio:<command line>
```

## Examples

### Windows

#### Node.js server in Program Files
```properties
# With quotes (recommended for paths with spaces)
chappie.mcp.servers="stdio:\"C:\\Program Files\\nodejs\\node.exe\" \"C:\\Users\\Alice\\mcp-server\\index.js\" --config=\"C:\\Users\\Alice\\config.json\""

# Alternative using forward slashes (Node.js accepts both)
chappie.mcp.servers=stdio:"C:/Program Files/nodejs/node.exe" "C:/Users/Alice/mcp-server/index.js" --config="C:/Users/Alice/config.json"
```

Parsed as:
1. `C:\Program Files\nodejs\node.exe`
2. `C:\Users\Alice\mcp-server\index.js`
3. `--config=C:\Users\Alice\config.json`

#### Python server
```properties
chappie.mcp.servers=stdio:"C:\\Python\\python.exe" server.py --port 3000
```

Parsed as:
1. `C:\Python\python.exe`
2. `server.py`
3. `--port`
4. `3000`

### Linux/macOS

#### Node.js server with spaces in path
```properties
chappie.mcp.servers=stdio:/usr/local/bin/node '/home/user/my server/index.js' --config='/etc/mcp/config.json'
```

Parsed as:
1. `/usr/local/bin/node`
2. `/home/user/my server/index.js`
3. `--config=/etc/mcp/config.json`

#### Python server
```properties
chappie.mcp.servers=stdio:/usr/bin/python3 /opt/mcp-server/server.py --port 3000
```

Parsed as:
1. `/usr/bin/python3`
2. `/opt/mcp-server/server.py`
3. `--port`
4. `3000`

## Quote Handling

### Double Quotes
```properties
chappie.mcp.servers=stdio:node "arg with spaces" arg2
```
Parsed as: `["node", "arg with spaces", "arg2"]`

### Single Quotes
```properties
chappie.mcp.servers=stdio:node 'arg with spaces' arg2
```
Parsed as: `["node", "arg with spaces", "arg2"]`

### Escaped Quotes (for nested quotes)
```properties
chappie.mcp.servers=stdio:node "arg with \"nested\" quotes"
```
Parsed as: `["node", "arg with "nested" quotes"]`

### Mixed Arguments
```properties
chappie.mcp.servers=stdio:node server.js --host=localhost --config="path with spaces/config.json"
```
Parsed as: `["node", "server.js", "--host=localhost", "--config=path with spaces/config.json"]`

## Platform-Specific Notes

### Windows
- **Backslashes**: Use double backslashes in quoted paths: `"C:\\Program Files\\..."`
- **Forward slashes**: Many Windows programs (like Node.js) accept forward slashes: `"C:/Program Files/..."`
- **8.3 short names**: Alternative workaround: `C:\PROGRA~1\nodejs\node.exe` (no quotes needed)

### Linux/macOS
- **Tilde expansion**: Not supported. Use full paths: `/home/user/...` instead of `~/...`
- **Environment variables**: Not expanded. Use absolute paths.

## Multiple MCP Servers

Configure multiple servers using a comma-separated list:

```properties
chappie.mcp.servers=\
  stdio:"C:\\Program Files\\nodejs\\node.exe" server1.js,\
  stdio:python server2.py,\
  https://mcp.example.com/api
```

## Troubleshooting

If your MCP server fails to start, check the logs for:
```
CHAPPiE MCP: added stdio server: [...]
```

The parsed command will be displayed. Verify that:
1. Paths with spaces are properly quoted
2. Executable path is correct
3. All arguments are separated correctly
