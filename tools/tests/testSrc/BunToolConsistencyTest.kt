package com.intellij.tools.cmd

import com.intellij.openapi.application.PathManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.readText

/**
 * `tools/bun.cmd` and the `bun_*` archives in `MODULE.bazel` must pin the same bun.
 *
 * They are two independent entry points to the same runtime: the wrapper serves everything outside
 * Bazel (`BT`, the ij-proxy MCP server, `pnpm run check`), while the archives serve Bazel tests that
 * cannot download anything. If the two pins drift, the same script runs on two different bun versions
 * depending on who invoked it, which is the kind of difference that only shows up as an
 * unreproducible test failure.
 */
@Timeout(1, unit = TimeUnit.MINUTES)
class BunToolConsistencyTest {
  @Test
  fun moduleBazelPinsTheSameVersionAsTheWrapper() {
    val declared = Regex("""^BUN_VERSION = "([^"]+)"""", RegexOption.MULTILINE).find(moduleBazel.readText())
    assertThat(requireNotNull(declared) { "$moduleBazel declares no BUN_VERSION" }.groupValues[1])
      .describedAs(
        "MODULE.bazel BUN_VERSION must match TOOL_VERSION in tools/bun.cmd; update both, and refresh " +
        "BUN_PLATFORMS with the new archive checksums"
      )
      .isEqualTo(CmdToolTestUtil.parseToolVersion("bun.cmd"))
  }

  @Test
  fun moduleBazelPinsTheSameChecksumsAsTheWrapper() {
    // `sha256` in an http_archive and TOOL_CHECKSUM_* in the wrapper both hash the release archive,
    // so they are comparable directly.
    val wrapper = CmdToolTestUtil.resolveToolsDir().resolve("bun.cmd").readText()
    val expected = WRAPPER_CHECKSUM_VARIABLES.mapValues { (_, variable) ->
      val match = Regex("""^export $variable="([0-9a-f]{64})"""", RegexOption.MULTILINE).find(wrapper)
      requireNotNull(match) { "tools/bun.cmd declares no $variable" }.groupValues[1]
    }

    assertThat(starlarkPlatformChecksums())
      .describedAs("MODULE.bazel BUN_PLATFORMS must match the TOOL_CHECKSUM_* values in tools/bun.cmd")
      .isEqualTo(expected)
  }

  /** `BUN_PLATFORMS` as declared in `MODULE.bazel`, keyed by the platform used in the archive name. */
  private fun starlarkPlatformChecksums(): Map<String, String> {
    val block = Regex("""BUN_PLATFORMS = \{(.*?)}""", RegexOption.DOT_MATCHES_ALL).find(moduleBazel.readText())
    requireNotNull(block) { "$moduleBazel declares no BUN_PLATFORMS" }
    return Regex(""""([a-z0-9_]+)":\s*"([0-9a-f]{64})"""").findAll(block.groupValues[1])
      .associate { it.groupValues[1] to it.groupValues[2] }
  }

  private val moduleBazel: Path
    get() = Path.of(PathManager.getCommunityHomePath()).resolve("MODULE.bazel")
}

/** MODULE.bazel platform key to the wrapper variable holding that platform's archive checksum. */
private val WRAPPER_CHECKSUM_VARIABLES = mapOf(
  "linux_x64" to "TOOL_CHECKSUM_LINUX_X64",
  "linux_aarch64" to "TOOL_CHECKSUM_LINUX_ARM64",
  "windows_x64" to "TOOL_CHECKSUM_WINDOWS_X64",
  "windows_aarch64" to "TOOL_CHECKSUM_WINDOWS_ARM64",
  "darwin_x64" to "TOOL_CHECKSUM_MACOS_X64",
  "darwin_aarch64" to "TOOL_CHECKSUM_MACOS_ARM64",
)
