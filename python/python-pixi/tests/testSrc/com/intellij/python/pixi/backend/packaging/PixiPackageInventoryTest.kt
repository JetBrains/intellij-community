// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.packaging

import com.intellij.testFramework.common.timeoutRunBlocking
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.isSuccess
import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.packaging.common.PythonPackageMetadata
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

@Subsystems.PackagingRequirements
@Layers.Functional
internal class PixiPackageInventoryTest {
  @Test
  fun `native records retain artifact and dependency proof metadata`() {
    val digest = "a".repeat(64)
    val records = """[{
      "name":"probe","version":"1.0","build":"0","url":"https://example.org/probe-1.0-0.conda","sha256":"$digest",
      "depends":["python >=3.13"],"constrains":["optional <2"]
    }]"""

    assertThat(parseInstalledPixiCondaRecords(records).getOrThrow()).containsExactly(
      PixiInstalledCondaPackage(
        "probe", "1.0", dependencies = listOf("python >=3.13"), constraints = listOf("optional <2"),
        artifact = createPixiNativeArtifact("1.0", "0", "https://example.org/probe-1.0-0.conda", digest, null),
      ),
    )
  }

  @Test
  fun `target records retain native ownership of Python distributions`() {
    val result = parseInstalledPixiCondaRecords("""[
      {"name":"native-owner","version":"9.0","files":["Lib/site-packages/python_alias-2.0.dist-info/METADATA"]},
      {"name":"python","version":"3.13.0"}
    ]""")

    assertThat(result.isSuccess).isTrue()
    assertThat(result.getOrThrow()).containsExactly(
      PixiInstalledCondaPackage("native-owner", "9.0", mapOf(PyPackageName.from("python-alias") to "2.0")),
      PixiInstalledCondaPackage("python", "3.13.0"),
    )
  }

  @ParameterizedTest
  @ValueSource(strings = ["{}", "[{}]", "null", "[{\"name\":\"python\",\"version\":null}]"])
  fun `invalid target records report a failed inventory`(output: String) {
    assertThat(parseInstalledPixiCondaRecords(output).isSuccess).isFalse()
  }

  @Test
  fun `native inventory reads installed records without a lock file`(@TempDir prefix: Path): Unit = timeoutRunBlocking {
    val records = prefix.resolve("conda-meta").createDirectories()
    records.resolve("native_probe-2.0-0.json").writeText("""{"name":"native_probe","version":"2.0","build":"0"}""")
    records.resolve("history").writeText("The environment history is not a package record.")

    val result = readInstalledPixiCondaPackages(prefix)

    assertThat(result.isSuccess).isTrue()
    assertThat(result.getOrThrow()).containsExactly(PixiInstalledCondaPackage("native_probe", "2.0"))
  }

  @Test
  fun `an invalid native record reports a failed inventory`(@TempDir prefix: Path): Unit = timeoutRunBlocking {
    prefix.resolve("conda-meta").createDirectories().resolve("broken.json").writeText("{}")
    assertThat(readInstalledPixiCondaPackages(prefix).isSuccess).isFalse()
  }

  @Test
  fun `installed Python metadata replaces stale lock records and keeps native packages`() {
    val native = listOf(PixiInstalledCondaPackage("python", "3.13.0"), PixiInstalledCondaPackage("native_probe", "1.0.0"))
    val metadata = listOf(
      PythonPackageMetadata("native-probe", "1.0"), PythonPackageMetadata("external", "2.0"), PythonPackageMetadata("editable", "3.0"),
    ).associateBy { PyPackageName.from(it.name) }
    val locked = listOf(PixiPackage("removed", "1.0", "pypi"), PixiPackage("editable", "2.0", "pypi", editable = true))

    val result = mergeInstalledPixiPackages(native, metadata, locked)

    assertThat(result).containsExactly(
      PixiPackage("editable", "3.0", "pypi", editable = true), PixiPackage("external", "2.0", "pypi"),
      PixiPackage("native_probe", "1.0.0", "conda"), PixiPackage("python", "3.13.0", "conda"),
    )
  }

  @Test
  fun `an external Python upgrade replaces the native version in the inventory`() {
    val native = listOf(PixiInstalledCondaPackage("native_probe", "1.0"))
    val metadata = mapOf(PyPackageName.from("native-probe") to PythonPackageMetadata("native-probe", "2.0"))

    assertThat(mergeInstalledPixiPackages(native, metadata, emptyList()))
      .containsExactly(PixiPackage("native-probe", "2.0", "pypi"))
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `owned distributions keep their native package identity`(pathsData: Boolean, @TempDir prefix: Path): Unit = timeoutRunBlocking {
    val metadataPath = "lib/python3.13/site-packages/python_alias-2.0.dist-info/METADATA"
    val files = if (pathsData) """"paths_data":{"paths":[{"_path":"$metadataPath"}]}"""
    else """"files":["$metadataPath"]"""
    prefix.resolve("conda-meta").createDirectories().resolve("native-owner-9.0-0.json")
      .writeText("""{"name":"native-owner","version":"9.0",$files}""")
    val installed = readInstalledPixiCondaPackages(prefix)
    assertThat(installed.isSuccess).isTrue()
    val metadata = mapOf(PyPackageName.from("python-alias") to PythonPackageMetadata("python-alias", "2.0"))

    val packages = mergeInstalledPixiPackages(installed.getOrThrow(), metadata, emptyList())

    assertThat(packages).containsExactly(PixiPackage("native-owner", "9.0", "conda"))
  }

  @Test
  fun `an external alias upgrade uses the Python version`() {
    val native = listOf(PixiInstalledCondaPackage("native-owner", "9.0", mapOf(PyPackageName.from("python-alias") to "2.0")))
    val metadata = mapOf(PyPackageName.from("python-alias") to PythonPackageMetadata("python-alias", "3.0"))

    assertThat(mergeInstalledPixiPackages(native, metadata, emptyList()))
      .containsExactly(PixiPackage("python-alias", "3.0", "pypi"))
  }

  @ParameterizedTest
  @ValueSource(strings = [
    "Lib\\site-packages\\native_owner-2.0.dist-info\\METADATA",
    "lib/python3.13/site-packages/native_owner-2.0-py3.13.egg-info/PKG-INFO",
  ])
  fun `owned metadata versions can differ from the native version`(metadataPath: String, @TempDir prefix: Path): Unit = timeoutRunBlocking {
    val path = metadataPath.replace("\\", "\\\\")
    prefix.resolve("conda-meta").createDirectories().resolve("native_owner-9.0-0.json")
      .writeText("""{"name":"native_owner","version":"9.0","files":["$path"]}""")
    val installed = readInstalledPixiCondaPackages(prefix)
    assertThat(installed.isSuccess).isTrue()
    val metadata = mapOf(PyPackageName.from("native-owner") to PythonPackageMetadata("native-owner", "2.0"))

    assertThat(mergeInstalledPixiPackages(installed.getOrThrow(), metadata, emptyList()))
      .containsExactly(PixiPackage("native_owner", "9.0", "conda"))
  }

  @Test
  fun `a missing owned distribution does not fabricate a Python package`() {
    val native = listOf(PixiInstalledCondaPackage("native-owner", "9.0", mapOf(PyPackageName.from("python-alias") to "2.0")))

    assertThat(mergeInstalledPixiPackages(native, emptyMap(), emptyList()))
      .containsExactly(PixiPackage("native-owner", "9.0", "conda"))
  }

  @Test
  fun `an external upgrade preserves other distributions owned by the same native package`() {
    val native = listOf(PixiInstalledCondaPackage("native-owner", "9.0", mapOf(
      PyPackageName.from("first-alias") to "2.0", PyPackageName.from("second-alias") to "2.0",
    )))
    val metadata = listOf(PythonPackageMetadata("first-alias", "3.0"), PythonPackageMetadata("second-alias", "2.0"))
      .associateBy { PyPackageName.from(it.name) }

    assertThat(mergeInstalledPixiPackages(native, metadata, emptyList())).containsExactly(
      PixiPackage("first-alias", "3.0", "pypi"), PixiPackage("native-owner", "9.0", "conda"),
    )
  }
}
