[![official JetBrains project](http://jb.gg/badges/incubator-flat-square.svg)](https://github.com/JetBrains#jetbrains-on-github)
# JetBrains MCP Server Plugin

JetBrains MCP (Model Context Protocol) Server Plugin enables seamless integration between Large Language Models (LLMs) and JetBrains IDEs. This plugin provides the server-side implementation for handling MCP requests and exposes extension points for implementing custom tools.

## Prerequisites

- Installation of [JetBrains MCP Proxy](https://github.com/JetBrains/mcpProxy)
- JetBrains IDE (IntelliJ IDEA, WebStorm, etc.)

## Custom Tools Implementation

The plugin provides an extension point system that allows third-party plugins to implement their own MCP tools. Here's how to implement and register your custom tools.

Refer to the [demo plugin](https://github.com/MaXal/mcpExtensionPlugin) to get started.

### 3. Tool Implementation Guidelines

Your tool implementation should follow these guidelines:

- Tool names should be descriptive and use lowercase with optional underscores
- Create a data class for your tool's arguments that matches the expected JSON input
- Use the Response class appropriately:
  - `Response(result)` for successful operations
  - `Response(error = message)` for error cases
- Use the provided Project instance for accessing IDE services

## How to Publish Update
1. Update `settings.gradle.kts` to provide a new version 
2. Create release on Github, the publishing task will be automatically triggered

## Contributing

We welcome contributions! Please feel free to submit a Pull Request.
