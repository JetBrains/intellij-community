package com.intellij.mcpserver.notification

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.util.SystemInfo
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists

object ClaudeConfigManager {
    private val configPath: Path = getConfigPath()
    private val gson = Gson()

    fun isProxyConfigured(): Boolean {
        return getExistingJsonObject()?.let { json ->
            json.getAsJsonObject("mcpServers")?.let { servers ->
                isProxyInServers(servers)
            }
        } ?: false
    }

    fun modifyClaudeSettings() {
        val configFile = configPath.toFile()
        if (!configFile.exists()) {
            configFile.parentFile.mkdirs()
            configFile.createNewFile()
        }

        val jsonObject = getExistingJsonObject() ?: JsonObject()

        if (!jsonObject.has("mcpServers")) {
            jsonObject.add("mcpServers", JsonObject())
        }

        val mcpServers = jsonObject.getAsJsonObject("mcpServers")

        if (!isProxyInServers(mcpServers)) {
            val jetbrainsConfig = JsonObject().apply {
                addProperty("command", "npx")
                add("args", gson.toJsonTree(arrayOf("-y", "@jetbrains/mcp-proxy")))
            }
            mcpServers.add("jetbrains", jetbrainsConfig)

            try {
                val prettyGson = GsonBuilder().setPrettyPrinting().create()
                configFile.writeText(prettyGson.toJson(jsonObject))
            } catch (e: Exception) {
                throw RuntimeException("Failed to write configuration file", e)
            }
        }
    }

    fun isClaudeClientInstalled(): Boolean {
        return getClaudeConfigPath().exists()
    }

    private fun getClaudeConfigPath(): Path {
        return when {
            SystemInfo.isMac ->
                Paths.get(System.getProperty("user.home"), "Library", "Application Support", "Claude")

            SystemInfo.isWindows ->
                Paths.get(System.getenv("APPDATA"), "Claude")

            else -> throw IllegalStateException("Unsupported operating system")
        }
    }

    private fun getConfigPath(): Path {
        return getClaudeConfigPath().resolve("claude_desktop_config.json")
    }

    private fun getExistingJsonObject(): JsonObject? {
        val configFile = configPath.toFile()
        if (!configFile.exists()) {
            return null
        }

        val jsonContent = try {
            configFile.readText()
        } catch (e: Exception) {
            return null
        }

        return try {
            JsonParser.parseString(jsonContent).asJsonObject
        } catch (e: Exception) {
            null
        }
    }

    private fun isProxyInServers(mcpServers: JsonObject): Boolean {
        for (serverEntry in mcpServers.entrySet()) {
            val serverConfig = serverEntry.value.asJsonObject
            if (serverConfig.has("command") && serverConfig.has("args")) {
                val command = serverConfig.get("command").asString
                val args = serverConfig.getAsJsonArray("args")
                if (command == "npx" && args.any { it.asString.contains("@jetbrains/mcp-proxy") }) {
                    return true
                }
            }
        }
        return false
    }
}