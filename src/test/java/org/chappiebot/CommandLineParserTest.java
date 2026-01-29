package org.chappiebot;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for the command line parser in ChappieService.
 * Verifies that command parsing works correctly across Windows, Linux, and macOS.
 */
class CommandLineParserTest {

    /**
     * Uses reflection to access the private parseCommandLine method
     */
    @SuppressWarnings("unchecked")
    private List<String> parseCommandLine(String commandLine) throws Exception {
        ChappieService service = new ChappieService();
        Method method = ChappieService.class.getDeclaredMethod("parseCommandLine", String.class);
        method.setAccessible(true);
        return (List<String>) method.invoke(service, commandLine);
    }

    @Test
    void testSimpleCommand() throws Exception {
        List<String> result = parseCommandLine("node server.js");
        assertEquals(2, result.size());
        assertEquals("node", result.get(0));
        assertEquals("server.js", result.get(1));
    }

    @Test
    void testWindowsPathWithSpaces() throws Exception {
        // Windows path with Program Files
        List<String> result = parseCommandLine("\"C:\\Program Files\\nodejs\\node.exe\" server.js");
        assertEquals(2, result.size());
        assertEquals("C:\\Program Files\\nodejs\\node.exe", result.get(0));
        assertEquals("server.js", result.get(1));
    }

    @Test
    void testUnixPathWithSpaces() throws Exception {
        // Unix path with spaces
        List<String> result = parseCommandLine("\"/usr/local/my app/bin/node\" server.js");
        assertEquals(2, result.size());
        assertEquals("/usr/local/my app/bin/node", result.get(0));
        assertEquals("server.js", result.get(1));
    }

    @Test
    void testSingleQuotes() throws Exception {
        List<String> result = parseCommandLine("cmd 'arg with spaces' arg2");
        assertEquals(3, result.size());
        assertEquals("cmd", result.get(0));
        assertEquals("arg with spaces", result.get(1));
        assertEquals("arg2", result.get(2));
    }

    @Test
    void testDoubleQuotes() throws Exception {
        List<String> result = parseCommandLine("cmd \"arg with spaces\" arg2");
        assertEquals(3, result.size());
        assertEquals("cmd", result.get(0));
        assertEquals("arg with spaces", result.get(1));
        assertEquals("arg2", result.get(2));
    }

    @Test
    void testMixedQuotes() throws Exception {
        List<String> result = parseCommandLine("cmd \"double quoted\" 'single quoted' unquoted");
        assertEquals(4, result.size());
        assertEquals("cmd", result.get(0));
        assertEquals("double quoted", result.get(1));
        assertEquals("single quoted", result.get(2));
        assertEquals("unquoted", result.get(3));
    }

    @Test
    void testEscapedQuotes() throws Exception {
        List<String> result = parseCommandLine("cmd \"arg with \\\"nested\\\" quotes\"");
        assertEquals(2, result.size());
        assertEquals("cmd", result.get(0));
        assertEquals("arg with \"nested\" quotes", result.get(1));
    }

    @Test
    void testMultipleSpaces() throws Exception {
        List<String> result = parseCommandLine("cmd    arg1     arg2");
        assertEquals(3, result.size());
        assertEquals("cmd", result.get(0));
        assertEquals("arg1", result.get(1));
        assertEquals("arg2", result.get(2));
    }

    @Test
    void testEmptyString() throws Exception {
        List<String> result = parseCommandLine("");
        assertEquals(0, result.size());
    }

    @Test
    void testOnlySpaces() throws Exception {
        List<String> result = parseCommandLine("   ");
        assertEquals(0, result.size());
    }

    @Test
    void testComplexWindowsCommand() throws Exception {
        // Real-world Windows MCP server command
        List<String> result = parseCommandLine("\"C:\\Program Files\\Node\\node.exe\" \"C:\\Users\\Alice\\mcp-server\\index.js\" --config=\"C:\\Users\\Alice\\config.json\"");
        assertEquals(3, result.size());
        assertEquals("C:\\Program Files\\Node\\node.exe", result.get(0));
        assertEquals("C:\\Users\\Alice\\mcp-server\\index.js", result.get(1));
        assertEquals("--config=C:\\Users\\Alice\\config.json", result.get(2));
    }

    @Test
    void testComplexUnixCommand() throws Exception {
        // Real-world Unix MCP server command
        List<String> result = parseCommandLine("/usr/local/bin/node '/home/user/my server/index.js' --config='/etc/mcp/config.json'");
        assertEquals(3, result.size());
        assertEquals("/usr/local/bin/node", result.get(0));
        assertEquals("/home/user/my server/index.js", result.get(1));
        assertEquals("--config=/etc/mcp/config.json", result.get(2));
    }

    @Test
    void testArgumentsWithEquals() throws Exception {
        List<String> result = parseCommandLine("node server.js --port=3000 --host=localhost");
        assertEquals(4, result.size());
        assertEquals("node", result.get(0));
        assertEquals("server.js", result.get(1));
        assertEquals("--port=3000", result.get(2));
        assertEquals("--host=localhost", result.get(3));
    }

    @Test
    void testBackslashInPath() throws Exception {
        // Backslashes should be preserved in paths (not treated as escape unless before quote)
        List<String> result = parseCommandLine("C:\\Windows\\System32\\cmd.exe /c dir");
        assertEquals(3, result.size());
        assertEquals("C:\\Windows\\System32\\cmd.exe", result.get(0));
        assertEquals("/c", result.get(1));
        assertEquals("dir", result.get(2));
    }
}
