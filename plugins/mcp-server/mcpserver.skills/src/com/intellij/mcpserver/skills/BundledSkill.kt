package com.intellij.mcpserver.skills

import org.jetbrains.annotations.ApiStatus

/**
 * One skill that a [BundledSkillsProvider] bundles in its plugin resources.
 *
 * @property skillId canonical skill id (folder id under bundled skills root)
 * @property tools IDE MCP tool names that this skill uses. CLI tools and external MCP tools do not belong here.
 * @property targets agents this skill is addressed to
 * @property resourcePath the skill directory on the class path of the provider. The default is the module's own
 * `bundledSkills/<skillId>`. A provider that packages a skill elsewhere, such as at the root of a resource-only
 * module, passes that path instead. Its last segment is always [skillId]: the installed directory takes its name
 * from the path, and the gates, the tool allowlist and FUS look the skill up by its id.
 * @property enabledRegistryKey the registry key that turns the skill on, or `null` when the skill has no gate of its own.
 * The default [BundledSkillsProvider.isSkillAvailable] reads it. The module that registers the skill declares the key.
 * @property requiredTools the subset of [tools] that an agent must be able to call. Without them, the agent does not get the skill.
 */
@ApiStatus.Experimental
class BundledSkill(
  val skillId: String,
  val tools: Set<String> = emptySet(),
  val targets: Set<AgentSkillTarget> = AgentSkillTarget.ALL,
  val resourcePath: String = "$DEFAULT_RESOURCE_ROOT/$skillId",
  val enabledRegistryKey: String? = null,
  val requiredTools: Set<String> = emptySet(),
) {
  init {
    require(skillId.isNotBlank()) { "skillId must not be blank" }
    require(resourcePath.substringAfterLast('/') == skillId) {
      "resourcePath '$resourcePath' must end with the skill id '$skillId'"
    }
    require(tools.containsAll(requiredTools)) { "Skill '$skillId' requires tools that it does not declare: ${requiredTools - tools}" }
  }

  override fun equals(other: Any?): Boolean =
    this === other || other is BundledSkill &&
    skillId == other.skillId && tools == other.tools && targets == other.targets &&
    resourcePath == other.resourcePath && enabledRegistryKey == other.enabledRegistryKey && requiredTools == other.requiredTools

  override fun hashCode(): Int = listOf(skillId, tools, targets, resourcePath, enabledRegistryKey, requiredTools).hashCode()

  override fun toString(): String =
    "BundledSkill(skillId=$skillId, tools=$tools, targets=$targets, resourcePath=$resourcePath, " +
    "enabledRegistryKey=$enabledRegistryKey, requiredTools=$requiredTools)"

  companion object {
    /** The resource root of the bundled skills in a provider module. */
    const val DEFAULT_RESOURCE_ROOT: String = "bundledSkills"
  }
}
