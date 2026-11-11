// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.testFramework.skills

import com.intellij.idea.AppMode
import com.intellij.openapi.application.PathManager
import com.intellij.platform.bazel.runfiles.BazelRunfiles
import com.intellij.platform.bazel.runfiles.BazelRunfilesManifest
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Files
import java.nio.file.Path

/**
 * The files of the bundled skills in the checkout, for the tests that check a skill root.
 *
 * A test walks the skill directories of a root in the checkout instead of asking a provider, so a skill directory
 * that no provider registers is checked too.
 */
@ApiStatus.Internal
object BundledSkillFiles {
  const val SKILL_FILE_NAME: String = "SKILL.md"
  const val METADATA_FILE_NAME: String = "metadata.json"

  /**
   * The checkout root that a test reads its inputs from.
   *
   * Under `bazel test` a `jps_test` with `sandbox = True` runs in a sandbox, and the checkout is not there.
   * The `data` of the `jps_test` in `BUILD.bazel` declares the files a test reads, and Bazel lays them out under
   * the runfiles root with their repository path. Windows has no runfiles tree, see [manifestRepoRoot].
   * In every other run the root is the working copy. A dev-build run has the dev-build output as its home, so it
   * reads the working copy from `idea.dev.project.root`.
   */
  fun repoRoot(): Path = when {
    isBazelSandbox() && isRunfilesManifestOnly() -> manifestRepoRoot
    isBazelSandbox() -> BazelRunfiles.bazelJavaRunfilesPath.resolve("_main")
    AppMode.isRunningFromDevBuild() -> Path.of(checkNotNull(AppMode.getDevIdeaProjectDir()) { "Couldn't find dev-idea project directory" })
    else -> PathManager.getHomeDir()
  }

  /** `jps_test` in `tests-options.bzl` sets [BAZEL_TEST_SANDBOX_ENV] from its `sandbox` parameter. */
  private fun isBazelSandbox(): Boolean = BazelRunfiles.isRunningFromBazel && System.getenv(BAZEL_TEST_SANDBOX_ENV).toBoolean()

  private const val BAZEL_TEST_SANDBOX_ENV: String = "JB_TEST_SANDBOX"

  /** Bazel sets [RUNFILES_MANIFEST_ONLY_ENV] on a platform without a runfiles tree, such as Windows. */
  private fun isRunfilesManifestOnly(): Boolean = System.getenv(RUNFILES_MANIFEST_ONLY_ENV) == "1"

  private const val RUNFILES_MANIFEST_ONLY_ENV: String = "RUNFILES_MANIFEST_ONLY"

  /**
   * The checkout root on a platform without a runfiles tree.
   *
   * The runfiles manifest maps the `_main/<path>` of a source file to `<checkout>/<path>`. A generated file maps to
   * `<path>` under `bazel-out`, so the root comes from the first entry outside `bazel-out`.
   */
  private val manifestRepoRoot: Path by lazy {
    val root = BazelRunfilesManifest().entries.firstNotNullOfOrNull { (key, realPath) ->
      val repositoryPath = key.removePrefix("_main/")
      val isSourceFile = repositoryPath != key && realPath.endsWith("/$repositoryPath") && "/bazel-out/" !in realPath
      if (isSourceFile) realPath.removeSuffix("/$repositoryPath") else null
    }
    Path.of(checkNotNull(root) { "The runfiles manifest has no source file under _main, so the checkout root is unknown" })
  }

  /** A file or a directory of the checkout, see [repoRoot]. */
  fun repoPath(relativePath: String): Path = repoRoot().resolve(relativePath)

  /** The skill directories directly under [root]. Fails on a missing root: a moved root must not shrink a check to nothing. */
  fun skillDirectories(root: Path): List<Path> {
    check(Files.isDirectory(root)) { "Skill root ${relativePath(root)} does not exist. If it moved, update the test that names it." }
    return Files.list(root).use { entries -> entries.filter(Files::isDirectory).sorted().toList() }
  }

  fun skillId(skillDirectory: Path): String = skillDirectory.fileName.toString()

  fun relativePath(path: Path): String = repoRoot().relativize(path).joinToString("/")
}
