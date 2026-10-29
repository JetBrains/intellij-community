// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.ide.starter.build.server

import com.intellij.platform.bazel.runfiles.BazelLabel
import com.intellij.platform.bazel.runfiles.BazelRunfiles
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

internal class DevDistSelectionTest {
  @TempDir
  lateinit var projectHome: Path

  @Test
  fun `product key of a frontend starts with the base IDE prefix`() {
    assertEquals("idea", devDistProductKey("idea", null))
    assertEquals("ideaJetBrainsClient", devDistProductKey("JetBrainsClient", "idea"))
  }

  @Test
  fun `selects the distribution of the product`() {
    val candidates = listOf(
      candidate("idea", product = "idea"),
      candidate("idea_frontend", product = "ideaJetBrainsClient", modularLoader = true),
      candidate("pycharm", product = "Python"),
    )
    assertEquals("pycharm", select(candidates, "Python")?.name)
    assertEquals("idea_frontend", select(candidates, devDistProductKey("JetBrainsClient", "idea"))?.name)
    assertNull(select(candidates, "GoLand"))
  }

  @Test
  fun `requested modules may come from the additional and the bundled modules`() {
    val candidates = listOf(
      candidate("idea", additionalModules = listOf("intellij.performanceTesting"), bundledModules = listOf("intellij.lombok.plugin")),
    )
    assertEquals("idea", select(candidates, modules = listOf("intellij.performanceTesting", "intellij.lombok.plugin"))?.name)
    assertNull(select(candidates, modules = listOf("intellij.performanceTesting", "intellij.space")))
  }

  @Test
  fun `requested runtime module repository needs the flag or the modular loader`() {
    val candidates = listOf(
      candidate("plain"),
      candidate("with_repository", additionalModules = listOf("a"), runtimeModuleRepository = true),
    )
    assertEquals("plain", select(candidates)?.name)
    assertEquals("with_repository", select(candidates, runtimeModuleRepository = true)?.name)
    assertEquals("modular", select(listOf(candidate("plain"), candidate("modular", modularLoader = true)), runtimeModuleRepository = true)?.name)
    assertNull(select(listOf(candidate("plain")), runtimeModuleRepository = true))
  }

  @Test
  fun `fewest additional modules win, then the name`() {
    val candidates = listOf(
      candidate("wide", additionalModules = listOf("a", "b")),
      candidate("narrow_b", additionalModules = listOf("a")),
      candidate("narrow_a", additionalModules = listOf("a")),
    )
    assertEquals("narrow_a", select(candidates, modules = listOf("a"))?.name)
    assertEquals("wide", select(candidates, modules = listOf("b"))?.name)
  }

  @Test
  fun `distribution without a runtime module repository wins a tie`() {
    val candidates = listOf(candidate("build_runtime_module_repository", runtimeModuleRepository = true), candidate("idea"))
    assertEquals("idea", select(candidates)?.name)
    assertEquals("build_runtime_module_repository", select(candidates, runtimeModuleRepository = true)?.name)
  }

  @Test
  fun `real community index selects the community IDE`() {
    val file = BazelRunfiles.getFileByLabelOrNull(BazelLabel("community", "build", "dev-dist-distributions.json"))
               ?: BazelRunfiles.getFileByLabel(BazelLabel("", "build", "dev-dist-distributions.json"))
    val index = readDevDistIndex(file.parent.parent)
    assertEquals("//build:idea_community_dist", select(index.distributions, "Idea")?.label)
  }

  @Test
  fun `no distribution matches`() {
    assertNull(select(emptyList()))
    assertNull(select(listOf(candidate("idea")), modules = listOf("intellij.go.template")))
  }

  @Test
  fun `subset rule accepts declared and bundled modules`() {
    assertEquals(emptyList<String>(), missingDevDistModules(listOf("a"), declared = listOf("a"), bundled = emptyList()))
    assertEquals(emptyList<String>(), missingDevDistModules(listOf("b"), declared = emptyList(), bundled = listOf("b")))
    assertEquals(listOf("c", "d"), missingDevDistModules(listOf("d", "a", "c", "b"), declared = listOf("a"), bundled = listOf("b")))
  }

  @Test
  fun `platform bundled modules count only on their host platforms`() {
    val candidates = listOf(candidate("idea", platformBundledModules = mapOf("intellij.wsl" to listOf("windows_aarch64", "windows_x64"))))
    assertEquals("idea", select(candidates, modules = listOf("intellij.wsl"), hostPlatform = "windows_x64")?.name)
    assertNull(select(candidates, modules = listOf("intellij.wsl"), hostPlatform = "linux_x64"))
    assertNull(select(candidates, modules = listOf("intellij.wsl"), hostPlatform = null))
  }

  @Test
  fun `host platform comes from the OS name and architecture`() {
    assertEquals("darwin_aarch64", hostPlatformKey("Mac OS X", "aarch64"))
    assertEquals("darwin_x64", hostPlatformKey("Mac OS X", "x86_64"))
    assertEquals("linux_aarch64", hostPlatformKey("Linux", "aarch64"))
    assertEquals("windows_x64", hostPlatformKey("Windows 11", "amd64"))
    assertEquals("windows_aarch64", hostPlatformKey("Windows 11", "aarch64"))
    // A JVM in WSL reports Linux, so the Windows-only modules do not count there.
    assertEquals("linux_x64", hostPlatformKey("Linux", "amd64"))
    assertNull(hostPlatformKey("FreeBSD", "amd64"))
    assertNull(hostPlatformKey("Linux", "riscv64"))
  }

  @Test
  fun `community labels get the repository prefix and its own products`() {
    writeIndex(DEV_DIST_INDEX_PATH, mapOf("idea" to product("[\"intellij.ultimate\"]")), entry("idea", "//build:idea_dist", "idea"))
    writeIndex("community/$DEV_DIST_INDEX_PATH", mapOf("Idea" to product("[\"intellij.java\"]")), entry("idea", "//build:idea_dist", "Idea"))
    val candidate = readDevDistIndex(projectHome).distributions.single { it.product == "Idea" }
    assertEquals("@community//build:idea_dist", candidate.label)
    assertEquals(listOf("intellij.java"), candidate.bundledModules)
  }

  @Test
  fun `distribution of a product without an entry fails`() {
    writeIndex(DEV_DIST_INDEX_PATH, mapOf("Idea" to product()), entry("python", "//build:python_dist", "Python"))
    assertThrows<IllegalStateException> { readDevDistIndex(projectHome) }
  }

  @Test
  fun `malformed index fails`() {
    val file = projectHome.resolve(DEV_DIST_INDEX_PATH)
    Files.createDirectories(file.parent)
    Files.writeString(file, """{"generatedBy":"test","distributions":[""")
    assertThrows<IllegalStateException> { readDevDistIndex(projectHome) }
    Files.writeString(file, """{"generatedBy":"test","products":{},"distributions":[{"name":"idea"}]}""")
    assertThrows<IllegalStateException> { readDevDistIndex(projectHome) }
  }

  @Test
  fun `standalone community checkout reads its index without a prefix`() {
    writeIndex(
      DEV_DIST_INDEX_PATH,
      mapOf("Idea" to product("[\"intellij.java\"]", platformBundled = """{"intellij.wsl":["windows_x64"]}""")),
      entry("idea", "//build:idea_dist", "Idea", additionalModules = """["intellij.performanceTesting"]"""),
    )
    val index = readDevDistIndex(projectHome)
    assertEquals(emptyList<Path>(), index.missingFiles)
    val candidate = index.distributions.single()
    assertEquals(
      DevDistCandidate(
        name = "idea",
        label = "//build:idea_dist",
        product = "Idea",
        additionalModules = listOf("intellij.performanceTesting"),
        bundledModules = listOf("intellij.java"),
        platformBundledModules = mapOf("intellij.wsl" to listOf("windows_x64")),
        runtimeModuleRepository = false,
        modularLoader = false,
        ultimate = false,
      ),
      candidate,
    )
  }

  @Test
  fun `ultimate index wins for a product of both halves`() {
    writeIndex(DEV_DIST_INDEX_PATH, mapOf("idea" to product()), entry("idea", "//build:idea_dist", "idea"))
    writeIndex(
      "community/$DEV_DIST_INDEX_PATH",
      mapOf("idea" to product(), "Idea" to product()),
      entry("idea", "//build:idea_dist", "idea"),
      entry("idea_community", "//build:idea_community_dist", "Idea"),
    )
    val candidates = readDevDistIndex(projectHome).distributions
    assertEquals(listOf("//build:idea_dist", "@community//build:idea_community_dist"), candidates.map { it.label })
    assertEquals(listOf(true, false), candidates.map { it.ultimate })
    assertEquals("//build:idea_dist", select(candidates, "idea")?.label)
  }

  @Test
  fun `missing index gives no distributions and names the file`() {
    val standalone = readDevDistIndex(projectHome)
    assertEquals(emptyList<DevDistCandidate>(), standalone.distributions)
    assertEquals(listOf(projectHome.resolve(DEV_DIST_INDEX_PATH)), standalone.missingFiles)

    Files.createDirectories(projectHome.resolve("community"))
    val monorepo = readDevDistIndex(projectHome)
    assertEquals(listOf(projectHome.resolve(DEV_DIST_INDEX_PATH), projectHome.resolve("community/$DEV_DIST_INDEX_PATH")), monorepo.missingFiles)
  }

  @Test
  fun `build event file names the built files`() {
    val home = projectHome.resolve("out/bin/build")
    val eventFile = projectHome.resolve("events.json")
    Files.writeString(
      eventFile,
      """
      {"id":{"started":{}},"started":{"uuid":"1"}}
      {"id":{"namedSet":{"id":"0"}},"namedSetOfFiles":{"files":[{"name":"build/idea_dist","uri":"${home.resolve("idea_dist").toUri()}"},{"name":"build/idea_dist.ide.config","uri":"${home.resolve("idea_dist.ide.config").toUri()}"},{"name":"remote","uri":"bytestream://cache/blobs/1"}]}}
      {"id":{"targetCompleted":{"label":"//build:idea_dist"}},"completed":{"success":true,"outputGroup":[{"name":"default","fileSets":[{"id":"0"}]}]}}

      """.trimIndent(),
    )
    assertEquals(listOf(home.resolve("idea_dist"), home.resolve("idea_dist.ide.config")), readBuiltFiles(eventFile))
  }

  @Test
  fun `malformed build event line fails`() {
    val eventFile = projectHome.resolve("events.json")
    Files.writeString(eventFile, "{\"id\":{}}\n{\"namedSetOfFiles\":\n")
    assertThrows<IllegalStateException> { readBuiltFiles(eventFile) }
  }

  @Test
  fun `ide config file name is the target name`() {
    assertEquals("idea_dist.ide.config", ideConfigFileName("//build:idea_dist"))
    assertEquals("idea_dist.ide.config", ideConfigFileName("@community//build:idea_dist"))
    assertEquals("idea_dist.ide.config", ideConfigFileName("//build/idea_dist"))
  }

  private fun select(
    candidates: List<DevDistCandidate>,
    product: String = "idea",
    modules: List<String> = emptyList(),
    runtimeModuleRepository: Boolean = false,
    hostPlatform: String? = "linux_x64",
  ): DevDistCandidate? = selectDevDist(candidates, product, modules, runtimeModuleRepository, hostPlatform)

  /** Writes an index file. [products] maps each product key to its JSON object, see [product]. */
  private fun writeIndex(relativePath: String, products: Map<String, String>, vararg entries: String) {
    val file = projectHome.resolve(relativePath)
    Files.createDirectories(file.parent)
    val productSection = products.entries.joinToString(",") { (product, value) -> """"$product":$value""" }
    Files.writeString(file, """{"generatedBy":"test","products":{$productSection},"distributions":[${entries.joinToString(",")}]}""")
  }
}

private fun entry(name: String, label: String, product: String, additionalModules: String = "[]"): String =
  """{"name":"$name","label":"$label","product":"$product","additionalModules":$additionalModules,"runtimeModuleRepository":false}"""

private fun product(bundled: String = "[]", platformBundled: String = "{}"): String =
  """{"bundledModules":$bundled,"platformBundledModules":$platformBundled,"modularLoader":false}"""
