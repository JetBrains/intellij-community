package com.intellij.mcpserver.skills

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * Bundles skill directories in the plugin resources of the provider.
 *
 * The consumer resolves [BundledSkill.resourcePath] on the class loader of the provider class.
 *
 * ### Why this API is in the MCP Server plugin
 *
 * A bundled skill calls MCP tools, and delivery checks the tools that the skill requires.
 * So the skill and its provider ship with the plugin that owns those tools.
 * A consumer such as Air releases faster than that plugin.
 * If the consumer held the skills, a skill and its tools could go out of sync.
 *
 * ### Threading
 *
 * The consumer calls each `suspend` hook on `Dispatchers.IO`, so blocking work is allowed.
 * A hook must honor cancellation.
 * A hook must not start an agent or an MCP server.
 * [registeredSkills] is synchronous. It declares the skills and decides nothing.
 *
 * ### Availability
 *
 * A skill reaches a working directory only when [isProviderAvailable] and [isSkillAvailable] both return `true`.
 */
@ApiStatus.Experimental
interface BundledSkillsProvider {
  /** Declares the complete catalog of this provider. Synchronous, no I/O, no workspace state. */
  fun registeredSkills(): List<BundledSkill>

  /** Provider-level availability hook for one working directory. */
  suspend fun isProviderAvailable(context: SkillWorkspaceContext): Boolean = true

  /**
   * Skill-level availability hook.
   *
   * The default reads [BundledSkill.enabledRegistryKey]. A skill without a key is available.
   */
  suspend fun isSkillAvailable(context: SkillWorkspaceContext, skill: BundledSkill): Boolean =
    skill.enabledRegistryKey?.let { Registry.`is`(it) } ?: true

  /** Called after the consumer extracts the skill directory to [directory] and before delivery. It can add files to the directory. */
  suspend fun prepareSkillDirectory(skill: BundledSkill, directory: Path) {}

  companion object {
    @ApiStatus.Internal
    val EP_NAME: ExtensionPointName<BundledSkillsProvider> = ExtensionPointName("com.intellij.mcpServer.bundledSkillsProvider")
  }
}
