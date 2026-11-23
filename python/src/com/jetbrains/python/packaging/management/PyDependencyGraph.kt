// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management

import com.intellij.python.requirements.parser.PyRequirementParser
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.getOrNull
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonPackageMetadata
import com.jetbrains.python.packaging.packageRequirements.DependencyTreeProvider
import com.jetbrains.python.packaging.packageRequirements.PackageTreeNode
import com.jetbrains.python.packaging.requirement.PyRequirementEnvMarkerType
import java.util.IdentityHashMap
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting

/**
 * Installed dependency graph as [PackageTreeNode]s versioned by the resolved installed version,
 * not by a declared constraint.
 *
 * Without a [DependencyTreeProvider] — pip, and so every plain-Python SDK — there is no `show --tree` to ask,
 * and the edges come from the installed distributions' Core Metadata instead. Dropping them made the graph
 * depend on the SDK flavor rather than on the environment.
 *
 * [declared] give the requested extras, such as `fastapi[all]`, to the metadata graph.
 */
@ApiStatus.Internal
@RequiresBackgroundThread(generateAssertion = false /* IJPL-115548 */)
suspend fun PythonPackageManager.installedDependencyGraph(declared: List<PyRequirement> = emptyList()): List<PackageTreeNode> {
  val installed = listInstalledPackages()
  val provider = treeProvider ?: return metadataDependencyGraph(installed, awaitInstalledPackagesMetadata(), declared)
  // `<manager> show --tree` prints version *constraints* for transitive nodes (e.g. `urllib3 >=1.21.1,<1.24`)
  // rather than the resolved installed version, so re-key every node to the concrete installed version.
  val installedVersions = installed.associate { PyPackageName.normalizePackageName(it.name) to it.version }
  val copies = IdentityHashMap<PackageTreeNode, PackageTreeNode>()
  return installedRoots(provider.getDependencyTrees().getOrNull().orEmpty(), installedVersions)
    .map { it.withResolvedVersions(installedVersions, copies) }
}

/** `poetry show --tree` can print two locked versions of one package as roots. This keeps the root of the installed version. */
private fun installedRoots(roots: List<PackageTreeNode>, installedVersions: Map<String, String>): List<PackageTreeNode> {
  val rootsByName = roots.groupBy { it.name.name }
  return roots.filter { root ->
    val name = root.name.name
    val sameName = rootsByName.getValue(name)
    val installedVersion = installedVersions[name]
    sameName.size == 1 || sameName.none { it.version == installedVersion } || root.version == installedVersion
  }
}

/**
 * A `Requires-Dist` line with an `extra` marker gives an edge only when the environment asked for that extra, the same as pip.
 * The function does not evaluate other environment markers.
 */
@VisibleForTesting
@ApiStatus.Internal
fun metadataDependencyGraph(
  installed: List<PythonPackage>,
  metadata: Map<PyPackageName, PythonPackageMetadata>,
  declared: List<PyRequirement> = emptyList(),
): List<PackageTreeNode> {
  val nodes = LinkedHashMap<String, PackageTreeNode>()
  installed.forEach { pkg ->
    nodes.putIfAbsent(PyPackageName.normalizePackageName(pkg.name), PackageTreeNode(PyPackageName.from(pkg.name), version = pkg.version))
  }
  val requirements = nodes.keys.associateWith { name ->
    val lines = metadata[PyPackageName.from(name)]?.requiresDist.orEmpty()
    if (lines.isEmpty()) emptyList() else PyRequirementParser.fromText(lines.joinToString("\n"))
  }
  val extras = requestedExtras(nodes.keys, requirements, declared)

  // Linking is a second pass over nodes that all exist already, so a cycle — and Python has them — just closes.
  nodes.forEach { (normalizedName, node) ->
    val ownExtras = extras[normalizedName].orEmpty()
    requirements.getValue(normalizedName)
      .filter { it.isActiveFor(ownExtras) }
      .mapTo(LinkedHashSet()) { it.name }
      .forEach { requiredName ->
        if (requiredName == normalizedName) return@forEach
        nodes[requiredName]?.let { node.children.add(it) }
      }
  }

  return nodes.values.toList()
}

private fun requestedExtras(
  installed: Set<String>,
  requirements: Map<String, List<PyRequirement>>,
  declared: List<PyRequirement>,
): Map<String, Set<String>> {
  val extras = HashMap<String, MutableSet<String>>()
  declared.forEach { extras.getOrPut(it.name) { HashSet() } += it.extraNames() }
  val queue = ArrayDeque(installed)
  while (queue.isNotEmpty()) {
    val name = queue.removeFirst()
    val ownExtras = extras[name]?.toSet().orEmpty()
    for (requirement in requirements.getValue(name)) {
      val required = requirement.name
      if (required in installed && requirement.isActiveFor(ownExtras) &&
          extras.getOrPut(required) { HashSet() }.addAll(requirement.extraNames())) {
        queue.addLast(required)
      }
    }
  }
  return extras
}

/** Without a requested extra, the marker sees `extra == ""`, the same as pip. */
private fun PyRequirement.isActiveFor(requestedExtras: Set<String>): Boolean =
  (requestedExtras + "").any { appliesTo(mapOf(PyRequirementEnvMarkerType.EXTRA to it)) }

private fun PyRequirement.extraNames(): Set<String> =
  extras.split(',').map(PyPackageName::normalizeProjectName).filterTo(HashSet()) { it.isNotEmpty() }

/** Non-blocking flat snapshot (no edges) from the cached list; empty until seeded. */
@ApiStatus.Internal
fun PythonPackageManager.installedDependencyGraphSnapshot(): List<PackageTreeNode> =
  listInstalledPackagesSnapshot().map { PackageTreeNode(PyPackageName.from(it.name), version = it.version) }

/** Copies each node once, keeping the sharing, so a graph with a cycle ends. */
private fun PackageTreeNode.withResolvedVersions(
  installedVersions: Map<String, String>,
  copies: MutableMap<PackageTreeNode, PackageTreeNode>,
): PackageTreeNode {
  copies[this]?.let { return it }
  val copy = PackageTreeNode(name, mutableListOf(), group, installedVersions[PyPackageName.normalizePackageName(name.name)] ?: version)
  copies[this] = copy
  children.mapTo(copy.children) { it.withResolvedVersions(installedVersions, copies) }
  return copy
}
