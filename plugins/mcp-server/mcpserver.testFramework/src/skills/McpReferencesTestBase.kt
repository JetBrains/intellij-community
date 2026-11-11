// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.testFramework.skills

import com.intellij.mcpserver.McpTool
import com.intellij.mcpserver.McpToolsProvider
import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.impl.util.asTools
import com.intellij.mcpserver.skills.BundledSkill
import com.intellij.testFramework.TestApplicationManager
import org.jetbrains.annotations.ApiStatus
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.nio.file.FileSystemAlreadyExistsException
import java.nio.file.FileSystems
import java.nio.file.Path
import kotlin.io.path.relativeTo
import kotlin.system.exitProcess

/**
 * Checks the skills that one provider registers against the live MCP tools:
 * - [declaredToolsExist]: every declared tool is a live tool;
 * - [claimedToolsAreDeclared]: every tool that the skill Markdown claims is a declared tool;
 * - [bundledReferencesAreUpToDate]: the `reference/` tree of each [generatedReferenceSkillIds] skill matches the live
 *   descriptors.
 */
@ApiStatus.Internal
abstract class McpReferencesTestBase {
  /** The working-copy root that holds one directory per registered skill, named by the skill id. */
  protected abstract val bundledSkillsRoot: Path

  /** Every skill the provider registers, whatever kind of `reference/` tree it ships. */
  protected abstract val registeredSkills: List<BundledSkill>

  /**
   * Ids of the registered skills whose `reference/` tree is generated from the live MCP descriptors. Only these skills
   * get the golden assertion and the regeneration. The default is empty, because most skills ship hand-written text.
   */
  protected open val generatedReferenceSkillIds: Set<String> = emptySet()

  /**
   * Ids of the registered skills that declare no tool on purpose. [declaredToolsExist] fails on every other skill
   * without a tool, because its check would pass over nothing.
   */
  protected open val skillsWithoutTools: Set<String> = emptySet()

  /**
   * Ids of the registered skills that declare tools but name them in prose only. [claimedToolsAreDeclared] fails on
   * every other skill that declares tools and claims none, because its check would pass over nothing.
   */
  protected open val skillsWithoutClaims: Set<String> = emptySet()

  /**
   * Ids of the registered skills whose `SKILL.md` claims every declared tool. [skillFileClaimsEveryDeclaredTool] checks
   * only these skills, because most skills describe their tools in `reference/`.
   */
  protected open val skillsThatClaimEveryToolInSkillFile: Set<String> = emptySet()

  /**
   * The toolset class of each registered skill that has to declare every tool of that toolset, by skill id.
   * [skillDeclaresEveryToolOfItsToolset] checks them. The other checks go from the skill to the tools, so a tool that
   * the toolset adds later stays out of the skill without this check.
   */
  protected open val toolsetsThatTheSkillCovers: Map<String, String> = emptyMap()

  /** The working-copy directory of [skill]. Override when the provider packages its skills from more than one root. */
  protected open fun skillDirectory(skill: BundledSkill): Path = bundledSkillsRoot.resolve(skill.skillId)

  protected open fun availableTools(): List<McpTool> {
    return McpToolsProvider.EP.extensionList.flatMap { it.getTools() }
  }

  /**
   * The resource directory on the test classpath that holds [skill], for a [bundledSkillsRoot] override. The skill is
   * found through the `SKILL.md` under its [BundledSkill.resourcePath].
   *
   * A sandboxed test target has no working copy, so it reads the skills from the jar of their module.
   */
  protected fun classpathSkillsRoot(skill: BundledSkill): Path {
    val resource = "${skill.resourcePath}/${BundledSkillFiles.SKILL_FILE_NAME}"
    val uri = checkNotNull(javaClass.classLoader.getResource(resource)) { "$resource is not on the test classpath" }.toURI()
    if (uri.scheme == "jar") {
      try {
        FileSystems.newFileSystem(uri, emptyMap<String, Any>())
      }
      catch (_: FileSystemAlreadyExistsException) {
      }
    }
    return Path.of(uri).parent.parent
  }

  /**
   * The tools of [toolset], for an [availableTools] override. [asTools] ignores [McpToolset.isEnabled], so a toolset
   * behind a registry key still counts.
   */
  protected fun toolsetTools(toolset: McpToolset): List<McpTool> = toolset.asTools()

  /**
   * The tools of the toolset class [className], for an [availableTools] override.
   *
   * The class is loaded by name, because a toolset is often `internal` to its module. A renamed toolset fails here by
   * name.
   */
  protected fun toolsetTools(className: String): List<McpTool> {
    return toolsetTools(Class.forName(className, true, javaClass.classLoader).getDeclaredConstructor().newInstance() as McpToolset)
  }

  /**
   * Every tool a skill declares has to be a live tool, one test per registered skill.
   *
   * A stale name is not inert: the consumer of the skill gives the declared names to `McpToolFilterProvider` as an
   * allowlist, so the skill would ship with a tool it can never call.
   */
  @TestFactory
  fun declaredToolsExist(): List<DynamicTest> {
    checkRegistered("skillsWithoutTools", skillsWithoutTools)
    // Assembled on the first test and shared by the rest. Schema building is the expensive part.
    val toolNames by lazy { availableTools().mapTo(mutableSetOf()) { it.descriptor.name } }

    return registeredSkills
      .sortedBy(BundledSkill::skillId)
      .map { skill ->
        DynamicTest.dynamicTest(skill.skillId) {
          if (skill.skillId in skillsWithoutTools) {
            check(skill.tools.isEmpty()) { "Skill '${skill.skillId}' declares tools now. Remove it from skillsWithoutTools." }
            return@dynamicTest
          }
          // A skill that declares no tool at all would make the assertion below vacuous.
          check(skill.tools.isNotEmpty()) {
            "Skill '${skill.skillId}' declares no tool, so there is nothing to check. " +
            "If the skill has no tool on purpose, add it to skillsWithoutTools."
          }
          val missing = (skill.tools - toolNames).sorted()

          assertTrue(missing.isEmpty()) {
            val missingRequired = missing.filter { it in skill.requiredTools }
            "Skill '${skill.skillId}' declares tools that do not exist: $missing.\n\n" +
            "A declared name that no tool answers to is either a tool that was renamed or removed, or a toolset that " +
            "${javaClass.simpleName} does not assemble. Fix the declaration in the provider, or add the missing toolset." +
            if (missingRequired.isEmpty()) "" else "\n\nThe skill requires $missingRequired, so no agent gets the skill."
          }
        }
      }
  }

  /**
   * Every tool the Markdown of a skill claims has to be a tool the skill declares, one test per registered skill.
   *
   * The declared names are the router allowlist, so an agent that follows the text to an undeclared tool can find it
   * hidden. [declaredToolsExist] then makes every claim a live tool. What counts as a claim is defined by
   * [SkillToolClaims].
   */
  @TestFactory
  fun claimedToolsAreDeclared(): List<DynamicTest> {
    checkRegistered("skillsWithoutClaims", skillsWithoutClaims)

    return registeredSkills.sortedBy(BundledSkill::skillId).map { skill ->
      DynamicTest.dynamicTest(skill.skillId) {
        val claims = SkillToolClaims.claims(skillDirectory(skill))
        if (skill.skillId in skillsWithoutClaims) {
          check(claims.isEmpty()) { "Skill '${skill.skillId}' claims tools now. Remove it from skillsWithoutClaims." }
        }
        else {
          // The claim forms are conventions, not a syntax that the skills must follow. A text in another shape would
          // leave this check green over nothing. Fail instead, and widen `SkillToolClaims`.
          check(skill.tools.isEmpty() || claims.isNotEmpty()) {
            "No tool claim was found in the Markdown of skill '${skill.skillId}', but the skill declares tools. " +
            "If the skill moved to another shape, teach SkillToolClaims that shape. " +
            "If the skill names its tools in prose only, add it to skillsWithoutClaims."
          }
        }

        val undeclared = claims
          .filterNot { claim -> claim.tool in skill.tools }
          .map { claim -> "${displayPath(claim.file)}: '${claim.tool}'" }
          .sorted()

        assertTrue(undeclared.isEmpty()) {
          "Skill '${skill.skillId}' documents tools that it does not declare:\n" +
          undeclared.joinToString(separator = "\n") + "\n\n" +
          "Add the tool to the skill's tools in the provider, or fix the skill text if the name is wrong."
        }
      }
    }
  }

  /**
   * Every tool that a skill of [skillsThatClaimEveryToolInSkillFile] declares has to be claimed in its `SKILL.md`, one
   * test per such skill. The agent reads `SKILL.md` first, so a tool that only `reference/` names can stay unused.
   */
  @TestFactory
  fun skillFileClaimsEveryDeclaredTool(): List<DynamicTest> {
    checkRegistered("skillsThatClaimEveryToolInSkillFile", skillsThatClaimEveryToolInSkillFile)

    val skills = registeredSkills.filter { it.skillId in skillsThatClaimEveryToolInSkillFile }
    return skills.sortedBy(BundledSkill::skillId).map { skill ->
      DynamicTest.dynamicTest("${skill.skillId}: SKILL.md claims every declared tool") {
        val directory = skillDirectory(skill)
        val skillFile = directory.resolve(BundledSkillFiles.SKILL_FILE_NAME)
        val claimed = SkillToolClaims.claims(directory)
          .filter { it.file.relativeTo(directory).toString() == BundledSkillFiles.SKILL_FILE_NAME }
          .mapTo(hashSetOf()) { it.tool }
        val unclaimed = (skill.tools - claimed).sorted()

        assertTrue(unclaimed.isEmpty()) {
          "${displayPath(skillFile)} does not name the declared tools $unclaimed. Describe each one, for example in the tool table."
        }
      }
    }
  }

  /** Every tool of the toolset in [toolsetsThatTheSkillCovers] has to be a tool that the skill declares, one test per skill. */
  @TestFactory
  fun skillDeclaresEveryToolOfItsToolset(): List<DynamicTest> {
    checkRegistered("toolsetsThatTheSkillCovers", toolsetsThatTheSkillCovers.keys)

    val skills = registeredSkills.filter { it.skillId in toolsetsThatTheSkillCovers }
    return skills.sortedBy(BundledSkill::skillId).map { skill ->
      val toolset = toolsetsThatTheSkillCovers.getValue(skill.skillId)
      DynamicTest.dynamicTest("${skill.skillId}: declares every tool of ${toolset.substringAfterLast('.')}") {
        val undeclared = (toolsetTools(toolset).mapTo(hashSetOf()) { it.descriptor.name } - skill.tools).sorted()

        assertTrue(undeclared.isEmpty()) {
          "Skill '${skill.skillId}' does not declare the tools $undeclared of $toolset. " +
          "Add them to the skill's tools in the provider, and describe them in its SKILL.md."
        }
      }
    }
  }

  @Test
  fun bundledReferencesAreUpToDate() {
    referenceConfigs().forEach(SkillMcpReferencesUtils::assertUpToDate)
  }

  fun regenerateMcpReferences() {
    TestApplicationManager.getInstance() // init application
    referenceConfigs().forEach(SkillMcpReferencesUtils::regenerate)
    exitProcess(0)
  }

  /** A path in the working copy relative to its root, and a path in a jar as is. */
  private fun displayPath(file: Path): String {
    return if (file.fileSystem == FileSystems.getDefault()) BundledSkillFiles.relativePath(file) else file.toString()
  }

  /** Fails when [ids] name a skill that [registeredSkills] does not hold, so that a stale entry in [property] is loud. */
  private fun checkRegistered(property: String, ids: Set<String>) {
    val unknown = ids - registeredSkills.mapTo(hashSetOf(), BundledSkill::skillId)
    check(unknown.isEmpty()) { "$property names skills that ${javaClass.simpleName} does not register: ${unknown.sorted()}" }
  }

  private fun referenceConfigs(): List<SkillMcpReferencesUtils.Config> {
    checkRegistered("generatedReferenceSkillIds", generatedReferenceSkillIds)
    // Most providers ship no generated tree. Return before the schema building, the expensive part of this test.
    if (generatedReferenceSkillIds.isEmpty()) return emptyList()
    // Built once and passed by value.
    val tools = availableTools().distinctBy { it.descriptor.name }
    return registeredSkills.filter { it.skillId in generatedReferenceSkillIds }.map { skill ->
      SkillMcpReferencesUtils.Config(
        skillDirectory = skillDirectory(skill),
        skill = skill,
        availableTools = tools,
      )
    }.sortedBy { it.skill.skillId }
  }
}
