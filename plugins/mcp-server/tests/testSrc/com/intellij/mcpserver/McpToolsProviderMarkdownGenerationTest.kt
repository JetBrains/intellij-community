package com.intellij.mcpserver

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.io.File

@TestApplication
class McpToolsProviderMarkdownGenerationTest {
  @Test
  @Disabled("Used for generation only")
  fun generateMarkdownFromMcpToolsProvider() {
    val allTools = McpToolsProvider.EP.extensionList.flatMap { provider ->
      provider.getTools()
    }.sortedBy { it.descriptor.name }

    val markdown = buildString {
      appendLine("This document lists all available MCP (Model Context Protocol) tools provided by the IntelliJ MCP Server.")
      appendLine()
      appendLine("## Available Tools")
      appendLine()
      appendLine("| Tool Name | Description |")
      appendLine("|-----------|-------------|")
      
      allTools.forEach { tool ->
        val name = tool.descriptor.name.replace("|", "\\|")
        val description = tool.descriptor.description
          .lines()
          .map { line -> 
            if (line.trimStart().startsWith("-")) {
              line
            } else {
              line.trim()
            }
          }
          .dropWhile { it.isEmpty() }
          .dropLastWhile { it.isEmpty() }
          .joinToString("<br>")
          .replace("|", "\\|")
          .replace(". ", ".<br>")
          .replace("! ", "!<br>")
          .replace("? ", "?<br>")
        appendLine("| `$name` | $description |")
      }
    }
    println(markdown)

    val outputFile = File("mcp-tools-documentation.md")
    outputFile.writeText(markdown)
    println("Documentation written to: ${outputFile.absolutePath}")
  }
}