// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.tensorFlow

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.psi.PsiElement
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.PyPsiPackageUtil
import com.jetbrains.python.packaging.PyPackage
import com.jetbrains.python.packaging.PyPackageManager
import com.jetbrains.python.packaging.pyRequirementVersionSpec
import com.jetbrains.python.packaging.requirement.PyRequirementRelation
import com.jetbrains.python.packaging.requirement.PyRequirementVersionSpec
import com.jetbrains.python.psi.resolve.PyQualifiedNameResolveContext
import com.jetbrains.python.psi.resolve.resolveQualifiedName

private val LAYOUT_PER_VERSION: List<Pair<VersionRange, Map<String, String>>> = listOf(
  VersionRange("2.16.1", null) to mapOf(
    // See https://github.com/tensorflow/tensorflow/commit/4be8c8d2d20c7297fd3fc6a036a21098b830c9bc
    "keras" to "keras._tf_keras.keras",
    // losses, metrics, optimizers, initializers should be covered by the content of 
    // typeshed/stubs/tensorflow/tensorflow/keras
    "initializers" to "keras._tf_keras.keras.initializers", 
    "losses" to "keras._tf_keras.keras.losses", 
    "metrics" to "keras._tf_keras.keras.metrics", 
    "optimizers" to "keras._tf_keras.keras.optimizers", 
    "security" to "tensorflow.security",
    "*" to "tensorflow._api.v2",
  ),
  VersionRange("2.6.0rc0", "2.16.1") to mapOf(
    "keras" to "keras.api._v2.keras",
    // losses, metrics, optimizers, initializers are not available as tensorflow submodules, only as its attributes
    // i.e. "from tensorflow import losses" is possible, but not "import tensorflow.losses".
    "estimator" to "tensorflow_estimator.python.estimator.api._v2.estimator",
    "summary" to "tensorboard.summary._tf.summary",
    "*" to "tensorflow._api.v2",
  ),
  VersionRange("2.0.0a0", "2.6.0rc0") to mapOf(
    "compiler" to "tensorflow_core.compiler",
    "core" to "tensorflow_core.core",
    "tools" to "tensorflow_core.tools",
    "python" to "tensorflow_core.python",
    "keras" to "tensorflow_core.python.keras.api._v2.keras",
    "estimator" to "tensorflow_estimator.python.estimator.api._v2.estimator",
    "initializers" to "tensorflow_core.python.keras.api._v2.keras.initializers",
    "losses" to "tensorflow_core.python.keras.api._v2.keras.losses",
    "metrics" to "tensorflow_core.python.keras.api._v2.keras.metrics",
    "optimizers" to "tensorflow_core.python.keras.api._v2.keras.optimizers",
    "summary" to "tensorboard.summary._tf.summary",
    "*" to "tensorflow_core._api.v2",
  ),
  VersionRange("1.15.0rc0", "2.0.0a0") to mapOf(
    "compiler" to "tensorflow_core.compiler",
    "core" to "tensorflow_core.core",
    "tools" to "tensorflow_core.tools",
    "python" to "tensorflow_core.python",
    "keras" to "tensorflow_core.python.keras.api._v1.keras",
    "estimator" to "tensorflow_estimator.python.estimator.api._v1.estimator",
    "*" to "tensorflow_core._api.v1",
  ),
  VersionRange(null, "1.15.0rc0") to mapOf(
    "keras" to "tensorflow.python.keras.api._v1.keras",
    "estimator" to "tensorflow_estimator.python.estimator.api._v1.estimator",
    "*" to "tensorflow._api.v1",
  ),
)

internal fun getTensorFlowPathConfig(sdk: Sdk?): Map<String, String> {
  val version = getTensorFlowPackage(sdk)?.version
  if (version == null) {
    return LAYOUT_PER_VERSION.first().second
  }
  return LAYOUT_PER_VERSION.first { (versionRange, _) -> version in versionRange }.second
}

internal fun takeFirstResolvedInTensorFlow(qualifiedName: String, context: PyQualifiedNameResolveContext): PsiElement? {
  return resolveQualifiedName(QualifiedName.fromDottedString(qualifiedName), context).firstOrNull()
}

private fun getTensorFlowPackage(sdk: Sdk?): PyPackage? {
  if (sdk == null) return null

  val pkgManager = PyPackageManager.getInstance(sdk)

  val packages = pkgManager.packages ?: return null
  return PyPsiPackageUtil.findPackage(packages, "tensorflow")
}

private data class VersionRange(val minIncluded: String?, val maxExcluded: String?) {
  private val versionSpecs: List<PyRequirementVersionSpec> = listOfNotNull(
    minIncluded?.let { pyRequirementVersionSpec(PyRequirementRelation.GTE, minIncluded) },
    maxExcluded?.let { pyRequirementVersionSpec(PyRequirementRelation.LT, maxExcluded) },
  )
  operator fun contains(version: String): Boolean = versionSpecs.all { it.matches(version) }
}
