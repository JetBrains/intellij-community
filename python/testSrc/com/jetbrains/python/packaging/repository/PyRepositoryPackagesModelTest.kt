// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.repository

import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.packageRequirements.FlatPackageStructureNode
import com.jetbrains.python.packaging.packageRequirements.PackageCollectionPackageStructureNode
import com.jetbrains.python.packaging.packageRequirements.PackageTreeNode
import junit.framework.TestCase

internal class PyRepositoryPackagesModelTest : TestCase() {

  fun `test fromSnapshot filters by repo packages`() {
    val installed = listOf(pkg("requests", "2.31.0"), pkg("flask", "3.0.0"), pkg("numpy", "1.26.0"))
    val repoPackages = names("requests", "numpy")

    val model = PyRepositoryPackagesModel.fromSnapshot(installed, repoPackages)

    assertEquals(2, model.count)
    assertEquals(listOf("numpy", "requests"), model.treeNodes.map { it.name.name })
  }

  fun `test fromSnapshot returns empty for no matching packages`() {
    val installed = listOf(pkg("flask", "3.0.0"))
    val repoPackages = names("requests")

    val model = PyRepositoryPackagesModel.fromSnapshot(installed, repoPackages)

    assertEquals(0, model.count)
  }

  fun `test filter by query`() {
    val installed = listOf(pkg("requests", "2.31.0"), pkg("requests-oauthlib", "1.3.0"), pkg("flask", "3.0.0"))
    val repoPackages = installed.map { PyPackageName.from(it.name) }.toSet()

    val model = PyRepositoryPackagesModel.fromSnapshot(installed, repoPackages)

    assertEquals(2, model.filter("request").size)
    assertEquals(3, model.filter("").size)
    assertEquals(1, model.filter("flask").size)
    assertEquals(0, model.filter("nonexistent").size)
  }

  fun `test filter is case insensitive`() {
    val installed = listOf(pkg("flask", "3.0.0"))
    val model = PyRepositoryPackagesModel.fromSnapshot(installed, names("flask"))

    assertEquals(1, model.filter("Flask").size)
    assertEquals(1, model.filter("FLASK").size)
  }

  fun `test resolveVersion uses installed version`() {
    val installed = listOf(pkg("requests", "2.31.0"))
    val model = PyRepositoryPackagesModel.fromSnapshot(installed, names("requests"))

    assertEquals("2.31.0", model.resolveVersion(model.treeNodes.first()))
  }

  fun `test resolveVersion falls back to tree node version`() {
    val node = PackageTreeNode(PyPackageName.from("unknown"), mutableListOf(), version = "1.0.0")
    val model = PyRepositoryPackagesModel(listOf(node), emptyMap())

    assertEquals("1.0.0", model.resolveVersion(node))
  }

  fun `test resolveVersion returns empty when no version`() {
    val node = PackageTreeNode(PyPackageName.from("unknown"), mutableListOf())
    val model = PyRepositoryPackagesModel(listOf(node), emptyMap())

    assertEquals("", model.resolveVersion(node))
  }

  fun `test fromPackageTree uses tree nodes`() {
    val treeNodes = listOf(
      PackageTreeNode(PyPackageName.from("requests"), mutableListOf(), version = "2.31.0"),
      PackageTreeNode(PyPackageName.from("flask"), mutableListOf(), version = "3.0.0"),
    )
    val tree = PackageCollectionPackageStructureNode(treeNodes, emptyList())
    val installed = listOf(pkg("requests", "2.31.0"), pkg("flask", "3.0.0"))

    val model = PyRepositoryPackagesModel.fromPackageTree(tree, installed, names("requests"))

    assertEquals(1, model.count)
    assertEquals("requests", model.treeNodes.first().name.name)
  }

  fun `test fromPackageTree falls back to installed when tree is flat`() {
    val installed = listOf(pkg("requests", "2.31.0"), pkg("flask", "3.0.0"))

    val model = PyRepositoryPackagesModel.fromPackageTree(FlatPackageStructureNode, installed, names("requests", "flask"))

    assertEquals(2, model.count)
  }

  private fun pkg(name: String, version: String) = PythonPackage(name, version, isEditableMode = false)
  private fun names(vararg names: String): Set<PyPackageName> = names.map { PyPackageName.from(it) }.toSet()
}
