// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.packaging

import com.intellij.python.requirements.parser.PyRequirementParser
import com.intellij.testFramework.junit5.TestApplication
import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonPackageMetadata
import com.jetbrains.python.packaging.management.metadataDependencyGraph
import com.jetbrains.python.packaging.packageRequirements.PackageTreeNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
internal class MetadataDependencyGraphTest {

  @Test
  fun `reads the name out of a specifier, extras and a marker`() {
    val graph = metadataDependencyGraph(
      installed = listOf(pkg("httpx", "0.19.0"), pkg("certifi", "2024.2.2"), pkg("anyio", "4.3.0"), pkg("h2", "4.1.0")),
      metadata = mapOf(metadata("httpx", "certifi (>=2017.4.17)", """anyio ; python_version >= "3.8"""", "h2[extra]>=3,<5")),
    )
    assertEquals(listOf("certifi", "anyio", "h2"), graph.single { it.name.name == "httpx" }.children.map { it.name.name })
  }

  @Test
  fun `drops a requirement of an extra that nobody asks for`() {
    val graph = metadataDependencyGraph(
      installed = listOf(pkg("httpx", "0.19.0"), pkg("socksio", "1.0.0"), pkg("brotli", "1.1.0"), pkg("trio", "0.25.0")),
      metadata = mapOf(metadata(
        "httpx",
        """socksio ; extra == "socks"""",
        """brotli ; platform_python_implementation == 'CPython' and extra == 'brotli'""",
        """trio; (extra=="trio")""",
      )),
    )
    assertTrue(graph.single { it.name.name == "httpx" }.children.isEmpty()) { "Unexpected edge for an extra" }
  }

  @Test
  fun `keeps a requirement of an extra that a declaration asks for`() {
    val graph = metadataDependencyGraph(
      installed = listOf(pkg("httpx", "0.19.0"), pkg("socksio", "1.0.0"), pkg("brotli", "1.1.0")),
      metadata = mapOf(metadata("httpx", """socksio ; extra == "socks"""", """brotli ; extra == 'brotli'""")),
      declared = listOf(requirement("HTTPX[Socks]")),
    )
    assertEquals(listOf("socksio"), childrenOf(graph, "httpx"))
  }

  @Test
  fun `follows an extra that a requirement line asks for`() {
    val graph = metadataDependencyGraph(
      installed = listOf(pkg("fastapi", "0.115.0"), pkg("uvicorn", "0.30.0"), pkg("uvloop", "0.20.0"), pkg("httptools", "0.6.1")),
      metadata = mapOf(
        metadata("fastapi", """uvicorn[standard]>=0.12 ; extra == "all""""),
        metadata("uvicorn", """uvloop>=0.15.1 ; extra == "standard"""", """httptools ; extra == "httptools""""),
      ),
      declared = listOf(requirement("fastapi[all]")),
    )
    assertEquals(listOf("uvicorn"), childrenOf(graph, "fastapi"))
    assertEquals(listOf("uvloop"), childrenOf(graph, "uvicorn"))
  }

  @Test
  fun `follows an extra that the package asks for itself`() {
    val graph = metadataDependencyGraph(
      installed = listOf(pkg("dask", "2024.8.0"), pkg("distributed", "2024.8.0")),
      metadata = mapOf(metadata("dask", """dask[distributed] ; extra == "complete"""", """distributed ; extra == "distributed"""")),
      declared = listOf(requirement("dask[complete]")),
    )
    assertEquals(listOf("distributed"), childrenOf(graph, "dask"))
  }

  @Test
  fun `follows an extra that an unconditional requirement asks for`() {
    val graph = metadataDependencyGraph(
      installed = listOf(pkg("app", "1.0"), pkg("uvicorn", "0.30.0"), pkg("uvloop", "0.20.0")),
      metadata = mapOf(metadata("app", "uvicorn[standard]"), metadata("uvicorn", """uvloop ; extra == "standard"""")),
    )
    assertEquals(listOf("uvloop"), childrenOf(graph, "uvicorn"))
  }

  @Test
  fun `matches an extra spelled differently from the marker`() {
    val graph = metadataDependencyGraph(
      installed = listOf(pkg("fastapi", "0.115.0"), pkg("fastapi-cli", "0.0.5")),
      metadata = mapOf(metadata("fastapi", """fastapi-cli ; extra == "standard-no-fastapi-cloud-cli"""")),
      declared = listOf(requirement("fastapi[Standard_No.FastAPI_Cloud_CLI]")),
    )
    assertEquals(listOf("fastapi-cli"), childrenOf(graph, "fastapi"))
  }

  @Test
  fun `keeps a requirement whose marker only quotes the word extra`() {
    val graph = metadataDependencyGraph(
      installed = listOf(pkg("httpx", "0.19.0"), pkg("idna", "3.7")),
      metadata = mapOf(metadata("httpx", """idna ; sys_platform == "extra"""")),
    )
    assertEquals(listOf("idna"), graph.single { it.name.name == "httpx" }.children.map { it.name.name })
  }

  @Test
  fun `matches a requirement spelled differently from the installed name`() {
    // `zope.interface`, `zope_interface` and `Twisted` are the same two names once PEP 503 normalization is applied,
    // which `PyPackageName` does for every node it holds
    val graph = metadataDependencyGraph(
      installed = listOf(pkg("zope.interface", "6.2"), pkg("Twisted", "24.3.0")),
      metadata = mapOf(metadata("twisted", "zope_interface (>=5)")),
    )
    assertEquals(listOf("zope-interface"), graph.single { it.name.name == "twisted" }.children.map { it.name.name })
  }

  @Test
  fun `drops a requirement that is not installed`() {
    val graph = metadataDependencyGraph(
      installed = listOf(pkg("httpx", "0.19.0")),
      metadata = mapOf(metadata("httpx", "socksio", "certifi")),
    )
    assertTrue(graph.single().children.isEmpty()) { "Unexpected edge to a package that is not installed" }
  }

  private fun childrenOf(graph: List<PackageTreeNode>, name: String): List<String> =
    graph.single { it.name.name == name }.children.map { it.name.name }

  private fun requirement(line: String) = PyRequirementParser.fromLine(line) ?: error("Not a requirement: $line")

  private fun pkg(name: String, version: String) = PythonPackage(name, version, false)

  private fun metadata(name: String, vararg requiresDist: String) =
    PyPackageName.from(name) to PythonPackageMetadata(name = name, requiresDist = requiresDist.toList())
}
