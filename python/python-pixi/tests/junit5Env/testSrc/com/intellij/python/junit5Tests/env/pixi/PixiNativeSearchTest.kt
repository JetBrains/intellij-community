// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.pixi

import com.intellij.python.community.execService.Args
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.execGetStdout
import com.intellij.python.junit5Tests.framework.LeakedProcessReporterExtension
import com.intellij.python.pixi.backend.packaging.parsePixiNativePackageSearch
import com.intellij.python.pixi.test.env.PixiPyEnvironment
import com.intellij.python.pixi.test.env.pixiEnvironment
import com.intellij.python.test.env.core.PyEnvironmentFactory
import com.intellij.python.test.env.junit5.PythonFactoryExtension
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.isSuccess
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.minutes

@TestApplication
@Subsystems.PackagingRequirements
@Layers.Functional
@ExtendWith(PythonFactoryExtension::class, LeakedProcessReporterExtension::class)
internal class PixiNativeSearchTest {
  @Test
  fun `native JSON search returns more than five packages and versions`(
    @TempDir temporaryDirectory: Path, factory: PyEnvironmentFactory,
  ): Unit = timeoutRunBlocking(5.minutes) {
    val environment = checkNotNull(factory.createEnvironment(pixiEnvironment(PIXI_TEST_VERSION) {
      pythonVersion = pythonVersion("3.13")
    }).unwrap<PixiPyEnvironment>())
    val channel = temporaryDirectory.resolve("channel with spaces").createDirectories()
    val names = (1..7).map { "pixi_search_probe_$it" }
    val versions = (1..7).map { "1.0.$it" }
    val records = names.flatMap { name ->
      versions.map { version ->
        """"$name-$version-0.tar.bz2":{"name":"$name","version":"$version","build":"0","build_number":0,"subdir":"noarch","noarch":"generic","depends":[]}"""
      }
    }.joinToString(",")
    channel.resolve("noarch").createDirectories().resolve("repodata.json").writeText(
      """{"info":{"subdir":"noarch"},"packages":{$records},"packages.conda":{},"repodata_version":1}""",
    )
    channel.resolve("linux-64").createDirectories().resolve("repodata.json").writeText(
      """{"info":{"subdir":"linux-64"},"packages":{},"packages.conda":{},"repodata_version":1}""",
    )

    val output = ExecService().execGetStdout(environment.pixiExecutable, Args(
      "search", "--json", "--platform=linux-64", "--channel=${channel.toUri().toASCIIString()}", "--", "pixi_search_probe_*",
    ))

    assertThat(output.isSuccess).isTrue()
    val parsed = parsePixiNativePackageSearch(output.getOrThrow())
    assertThat(parsed.isSuccess).isTrue()
    val packages = parsed.getOrThrow()
    assertThat(packages.map { it.name }.distinct()).containsExactlyInAnyOrderElementsOf(names)
    assertThat(packages).hasSize(names.size * versions.size)
    for (name in names) {
      assertThat(packages.filter { it.name == name }.map { it.version }).containsExactlyInAnyOrderElementsOf(versions)
    }
  }
}
