// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.sdk

import com.intellij.platform.eel.provider.localEel
import com.intellij.python.junit5Tests.framework.pyProjectFixture
import com.intellij.python.sdk.common.PyEnvRef
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.pathInProjectFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.sdk.add.v2.EelFileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.flavors.PyFlavorData
import org.jdom.Element
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.nio.file.Path
import kotlin.io.path.writeText

@TestApplication
@Subsystems.Interpreters
@Layers.Functional
internal class PixiSdkDataProviderTest {
  private val rootFixture = tempPathFixture()
  private val projectFixture = projectFixture(rootFixture)
  private val childPathFixture = projectFixture.pathInProjectFixture(Path.of("child"))
  private val childProjectFixture = projectFixture.pyProjectFixture(childPathFixture)

  @ParameterizedTest
  @CsvSource(
    "/workspace/.pixi/envs/docs/bin/python, docs",
    "/workspace/.pixi/envs/default/bin/python3, default",
    "C:\\workspace\\.pixi\\envs\\docs\\python.exe, docs",
    "C:/workspace/.pixi/envs/docs/Scripts/python.exe, docs",
    "/workspace/.venv/bin/python, NULL",
    "/workspace/other/envs/docs/bin/python, NULL",
    "/workspace/.pixi/other/docs/bin/python, NULL",
    nullValues = ["NULL"],
  )
  fun `a target SDK keeps the Pixi environment name`(pythonBinary: String, expected: String?) {
    assertThat(PixiSdkFlavor.toolEnvRefOf(PathHolder.Target(pythonBinary), PyFlavorData.Empty)).isEqualTo(expected)
  }

  @ParameterizedTest
  @CsvSource("pixi.toml, false", "pyproject.toml, false", "pixi.toml, true", "pyproject.toml, true")
  fun `a member SDK tracks the owning workspace manifest`(manifestName: String, suppliedMemberManifest: Boolean): Unit = timeoutRunBlocking {
    val root = rootFixture.get()
    val prefix = if (manifestName == "pyproject.toml") "tool.pixi." else ""
    val manifest = root.resolve(manifestName)
    manifest.writeText("[${prefix}workspace]\nchannels = ['conda-forge']\n")
    val child = childProjectFixture.get()
    val childManifest = child.baseDir.resolve("pyproject.toml")
    childManifest.writeText("[project]\nname = 'child'\nversion = '0.1.0'\n")

    val data = PixiSdkDataProvider().sdkDataOf(
      child, PyEnvRef("default"), PathHolder.Eel(root.resolve(".pixi/envs/default/bin/python")), EelFileSystem(localEel),
      childManifest.takeIf { suppliedMemberManifest },
    )

    assertThat(data.requirementsPath).isEqualTo(manifest)
    val serialized = Element("additional")
    data.save(serialized)
    val restored = PythonSdkAdditionalData.loadFromElement(serialized)
    assertThat(restored.requirementsPath).isEqualTo(manifest)
    val reserialized = Element("additional")
    restored.save(reserialized)
    assertThat(reserialized.getAttributeValue("REQUIREMENTS_FILE")).isEqualTo(serialized.getAttributeValue("REQUIREMENTS_FILE"))
  }
}
