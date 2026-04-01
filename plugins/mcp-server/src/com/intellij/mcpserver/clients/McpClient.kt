@file:OptIn(ExperimentalSerializationApi::class)

package com.intellij.mcpserver.clients

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.mcpserver.clients.configs.ExistingConfig
import com.intellij.mcpserver.clients.configs.STDIOServerConfig
import com.intellij.mcpserver.clients.configs.ServerConfig
import com.intellij.mcpserver.createStdioMcpServerCommandLine
import com.intellij.mcpserver.impl.McpServerService
import com.intellij.mcpserver.impl.util.network.McpServerConnectionAddressProvider
import com.intellij.mcpserver.stdio.IJ_MCP_SERVER_PORT
import com.intellij.mcpserver.stdio.main
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.eel.ExecuteProcessException
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.eel.provider.utils.lines
import com.intellij.platform.eel.spawnProcess
import com.intellij.util.text.SemVer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.ClassDiscriminatorMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.json.jsonObject
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.net.URI
import java.nio.file.AccessDeniedException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Locale
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream
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

  /**
   * Attempts to configure an MCP client with the provider server configuration.
   *
   * It is possible that the client does not support this particular configuration (i.e., the client is not ready for Streamable HTTP at the moment).
   * If this is the case, then an [McpClientConfigurationException] gets thrown.
   * @return null if configuration was completed without errors, or an error message otherwise
   */
  @Throws(McpClientConfigurationException::class)
  open suspend fun configure(config: ServerConfig) {
    val existingConfig = readExistingConfig()
    val updatedConfig = buildUpdatedConfig(existingConfig, config)
    writeConfigToFile(updatedConfig)
  }

  class McpClientConfigurationException(override val message: String): Exception(message)

  suspend fun autoConfigure() {
    val streamableHttpConfig = getStreamableHttpConfig()
    if (streamableHttpConfig != null && runCatching { configure(streamableHttpConfig) }.isSuccess) {
      return
    }
    val sseConfig = getSSEConfig()
    if (sseConfig != null && runCatching { configure(sseConfig) }.isSuccess) {
      return
    }
    return configure(getStdioConfig())
  }

  suspend fun getPreferredConfig(): ServerConfig = getStreamableHttpConfig() ?: getSSEConfig() ?: getStdioConfig()

  open suspend fun getStreamableHttpConfig(): ServerConfig? = null

  open suspend fun getSSEConfig(): ServerConfig? = null

  fun getStdioConfig(): ServerConfig {
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

  protected fun isSSEOrStreamConfigured(): Boolean? {
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

  fun getConfiguredTransportTypes(): Set<TransportType> {
    val mcpServers = readMcpServers() ?: return emptySet()
    if (mcpServers.isEmpty()) return emptySet()

    val result = mutableSetOf<TransportType>()

    for ((_, serverConfig) in mcpServers) {
      if (serverConfig.command != null) {
        result.add(TransportType.STDIO)
      }

      when (serverConfig.type) {
        "sse" -> result.add(TransportType.SSE)
        "http" -> result.add(TransportType.STREAMABLE_HTTP)
      }
    }

    return result
  }

  fun getTransportTypesDisplayString(): String? {
    val types = getConfiguredTransportTypes()
    if (types.isEmpty()) return null

    return types.joinToString(", ") { type ->
      when (type) {
        TransportType.STDIO -> "Stdio"
        TransportType.SSE -> "SSE"
        TransportType.STREAMABLE_HTTP -> "HTTP Stream"
      }
    }
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
    catch (_: AccessDeniedException) {
      // on Windows, other AI agent frontends can open the config file. While they do it, we cannot move other file in place of it
      configPath.outputStream().use { output -> json.encodeToStream(config, output) }
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
    enum class TransportType {
      STDIO,
      SSE,
      STREAMABLE_HTTP
    }

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

    fun CoroutineScope.getSemVerOfVscodeFork(exe: String): Deferred<SemVer?> {
      return async(start = CoroutineStart.LAZY, context = Dispatchers.IO) {
        val localEelApi = LocalEelDescriptor.toEelApi()
        val versionStdout = try {
          localEelApi.exec.spawnProcess(exe, "-v").eelIt().stdout.lines().first().trim()
        } catch (_: ExecuteProcessException) {
          return@async null
        }
        SemVer.parseFromText(versionStdout)
      }
    }
  }

  @JsonIgnoreUnknownKeys
  @Serializable
  private data class McpServers(
    @JsonNames("servers", "mcpServers")
    val mcpServers: Map<String, ExistingConfig>,
  )
}
