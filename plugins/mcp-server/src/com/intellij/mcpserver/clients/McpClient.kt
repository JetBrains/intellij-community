@file:OptIn(ExperimentalSerializationApi::class)

package com.intellij.mcpserver.clients

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.mcpserver.clients.configs.ExistingConfig
import com.intellij.mcpserver.clients.configs.STDIOServerConfig
import com.intellij.mcpserver.createStdioMcpServerCommandLine
import com.intellij.mcpserver.impl.McpServerService
import com.intellij.mcpserver.clients.configs.ServerConfig
import com.intellij.mcpserver.impl.util.network.McpServerConnectionAddressProvider
import com.intellij.mcpserver.stdio.IJ_MCP_SERVER_PORT
import com.intellij.mcpserver.stdio.main
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.registry.Registry
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.*
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream
import java.net.URI
import kotlin.reflect.jvm.javaMethod

abstract class McpClient(
  @NlsContexts.BorderTitle val mcpClientInfo: McpClientInfo,
  val configPath: Path,
) {

  override fun toString(): String = mcpClientInfo.displayName

  protected val sseUrl: String
    get() = connectionAddressProvider?.serverSseUrl ?: defaultSseUrl

  protected open val streamableHttpUrl: String
    get() = connectionAddressProvider?.serverStreamUrl ?: defaultStreamUrl

  open fun isConfigured(): Boolean? = true

  open fun mcpServersKey(): String = "mcpServers"

  open fun configure(): Unit = updateServerConfig(getConfig())

  fun getConfig(): ServerConfig = getSSEConfig() ?: getStdioConfig()

  protected open fun getSSEConfig(): ServerConfig? = null

  private fun getStdioConfig(): ServerConfig {
    val cmd = createStdioMcpServerCommandLine(McpServerService.getInstance().port, null)
    return STDIOServerConfig(command = cmd.exePath, args = cmd.parametersList.parameters, env = cmd.environment)
  }

  protected open fun readMcpServers(): Map<String, ExistingConfig>? {
    return runCatching {
      if (!configPath.exists()) return null
      json.decodeFromStream<McpServers>(configPath.inputStream()).mcpServers
    }.getOrNull()
  }

  protected fun isStdIOConfigured(): Boolean? {
    val mcpServers = readMcpServers()
    if (mcpServers?.isEmpty() ?: true) return null
    return mcpServers.any { (_, serverConfig) ->
      serverConfig.command?.contains("java") == true &&
      serverConfig.env?.containsKey(::IJ_MCP_SERVER_PORT.name) == true &&
      serverConfig.args?.contains(::main.javaMethod!!.declaringClass.name) == true
    }
  }

  protected fun isSSEConfigured(): Boolean? {
    val mcpServers = readMcpServers()
    if (mcpServers?.isEmpty() ?: true) return null
    return mcpServers.any { (_, serverConfig) ->
      serverConfig.url?.let { matchesCurrentServerUrl(it) } == true
    }
  }

  fun isPortCorrect(): Boolean {
    if (!McpServerService.getInstance().isRunning) return true
    val currentPort = McpServerService.getInstance().port
    val servers = readMcpServers() ?: return true
    return servers.any { (_, serverConfig) -> isPortMatching(serverConfig, currentPort) }
  }

  private fun isPortMatching(serverConfig: ExistingConfig, targetPort: Int): Boolean {
    serverConfig.url?.let { url ->
      val parsed = parseServerUrl(url) ?: return false
      if (parsed.path in STREAM_PATHS && hostMatchesCurrent(parsed.host)) {
        return parsed.port == targetPort
      }
      return false
    }

    if (serverConfig.command?.contains("java") == true &&
        serverConfig.env?.containsKey(::IJ_MCP_SERVER_PORT.name) == true &&
        serverConfig.args?.contains(::main.javaMethod!!.declaringClass.name) == true) {
      val configuredPort = serverConfig.env[::IJ_MCP_SERVER_PORT.name]?.toIntOrNull()
      return configuredPort == targetPort
    }
    return true
  }

  protected fun updateServerConfig(serverEntry: ServerConfig) {
    val existingConfig = readExistingConfig()
    val updatedConfig = buildUpdatedConfig(existingConfig, serverEntry)
    writeConfigToFile(updatedConfig)
  }

  @Deprecated("Use product-specific terminology")
  protected fun jetBrainsServerKey(): String = productSpecificServerKey()

  private fun readExistingConfig(): JsonObject {
    return if (configPath.exists()) {
      runCatching { json.decodeFromStream<JsonObject>(configPath.inputStream()) }
        .getOrElse { buildJsonObject {} }
    }
    else buildJsonObject {}
  }

  /**
   * Default JSON layout: root contains `mcpServers` object.
   * Preserve non-server fields, then rewrite `mcpServers` with target + optional legacy.
   */
  protected open fun buildUpdatedConfig(existingConfig: JsonObject, serverEntry: ServerConfig): JsonObject {
    val existingServers = existingConfig[mcpServersKey()]?.jsonObject ?: buildJsonObject {}
    val targetKey = jetBrainsServerKey()

    val updatedServers = buildJsonObject {
      existingServers.forEach { (key, value) ->
        if (key != targetKey && key !in LEGACY_SERVER_KEYS) put(key, value)
      }
      put(targetKey, json.encodeToJsonElement(serverEntry))
      if (writeLegacy() && LEGACY_KEY != targetKey) {
        put(LEGACY_KEY, json.encodeToJsonElement(serverEntry))
      }
    }

    return buildJsonObject {
      existingConfig.forEach { (key, value) -> if (key != mcpServersKey()) put(key, value) }
      put(mcpServersKey(), updatedServers)
    }
  }

  private fun writeConfigToFile(config: JsonObject) {
    configPath.parent?.createParentDirectories()
    val tmp = configPath.resolveSibling("${configPath.fileName}.tmp")
    tmp.outputStream().use { output -> json.encodeToStream(config, output) }
    try {
      Files.move(
        tmp,
        configPath,
        StandardCopyOption.REPLACE_EXISTING,
        StandardCopyOption.ATOMIC_MOVE
      )
    }
    catch (_: AtomicMoveNotSupportedException) {
      Files.move(tmp, configPath, StandardCopyOption.REPLACE_EXISTING)
    }
  }

  private val connectionAddressProvider: McpServerConnectionAddressProvider?
    get() = McpServerConnectionAddressProvider.getInstanceOrNull()

  private val defaultSseUrl: String
    get() = "http://localhost:${McpServerService.getInstance().port}/sse"

  private val defaultStreamUrl: String
    get() = "http://localhost:${McpServerService.getInstance().port}/stream"

  private fun matchesCurrentServerUrl(url: String): Boolean {
    val parsed = parseServerUrl(url) ?: return false
    return parsed.path in STREAM_PATHS && hostMatchesCurrent(parsed.host)
  }

  private fun hostMatchesCurrent(host: String): Boolean {
    val normalized = host.normalizeHostForComparison()
    val currentHost = connectionAddressProvider?.currentHost?.normalizeHostForComparison() ?: "localhost"
    return normalized == currentHost ||
           normalized == "localhost" ||
           normalized == "127.0.0.1"
  }

  private fun parseServerUrl(url: String): ParsedServerUrl? {
    val uri = runCatching { URI(url) }.getOrNull() ?: return null
    val host = uri.host ?: return null
    val path = uri.path ?: return null
    val port = if (uri.port == -1) 80 else uri.port
    return ParsedServerUrl(host, port, path)
  }

  private fun String.normalizeHostForComparison(): String =
    trim().removePrefix("[").removeSuffix("]").lowercase()

  private data class ParsedServerUrl(val host: String, val port: Int, val path: String)

  companion object {
    private val STREAM_PATHS: Set<String> = setOf("/sse", "/stream")

    @Volatile
    private var writeLegacyOverride: Boolean? = null

    @TestOnly
    fun overrideWriteLegacyForTests(value: Boolean?) {
      writeLegacyOverride = value
    }

    fun writeLegacy(): Boolean =
      writeLegacyOverride ?: Registry.`is`("mcp.server.write.legacy.key", false)

    const val LEGACY_KEY: String = "jetbrains"
    val LEGACY_SERVER_KEYS: Set<String> = setOf(LEGACY_KEY)

    private val PRODUCT_SPECIFIC_SERVER_KEY: String by lazy { computeProductSpecificServerKey() }

    @Volatile
    private var productSpecificServerKeyOverride: String? = null

    val json: Json by lazy {
      Json {
        allowComments = true
        allowTrailingComma = true
        prettyPrint = true
        prettyPrintIndent = "  "
        classDiscriminatorMode = ClassDiscriminatorMode.NONE
        ignoreUnknownKeys = true
      }
    }

    fun productSpecificServerKey(): String = productSpecificServerKeyOverride ?: PRODUCT_SPECIFIC_SERVER_KEY

    @TestOnly
    fun overrideProductSpecificServerKeyForTests(value: String?) {
      productSpecificServerKeyOverride = value
    }

    private fun computeProductSpecificServerKey(): String {
      val namesInfo = ApplicationNamesInfo.getInstance()

      return computeProductSpecificServerKey(
        productName = namesInfo.productName,
        editionName = namesInfo.editionName,
        runningFromSources = PluginManagerCore.isRunningFromSources(),
      )
    }

    @VisibleForTesting
    internal fun computeProductSpecificServerKey(
      productName: String,
      editionName: String?,
      runningFromSources: Boolean = false,
    ): String {
      val sanitizedProductName = sanitizeSegment(productName).ifBlank { LEGACY_KEY }

      val sanitizedEdition = editionName
        ?.let(::sanitizeSegment)
        ?.removeSuffix("edition")
        ?.takeUnless { it.isBlank() }
        ?.takeUnless { sanitizedProductName.endsWith(it) }
        .orEmpty()

      return buildString {
        append(sanitizedProductName)
        if (sanitizedEdition.isNotEmpty()) append(sanitizedEdition)
        if (runningFromSources) append("dev")
      }
    }

    private fun sanitizeSegment(segment: String): String =
      segment.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "")

  }

  @JsonIgnoreUnknownKeys
  @Serializable
  private data class McpServers(
    @JsonNames("servers", "mcpServers")
    val mcpServers: Map<String, ExistingConfig>,
  )
}
