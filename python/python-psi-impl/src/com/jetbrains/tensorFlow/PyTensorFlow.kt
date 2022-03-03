// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.tensorFlow

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.psi.PsiElement
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.PyPsiPackageUtil
import com.jetbrains.python.packaging.PyPackage
import com.jetbrains.python.packaging.PyPackageManager
import com.jetbrains.python.packaging.pyRequirementVersionSpec
import com.jetbrains.python.packaging.requirement.PyRequirementRelation
import com.jetbrains.python.psi.resolve.PyQualifiedNameResolveContext
import com.jetbrains.python.psi.resolve.resolveQualifiedName

// TensorFlow submodules and subpackages are appended in runtime and have original location in other places.
// Here are path configurations:

// common
private const val ESTIMATOR: String = "tensorflow_estimator.python.estimator.api._v[__VERSION__].estimator"

// before 1.15.0rc0 and 2.0.0rc0
private const val OTHERS_OLD: String = "tensorflow._api.v[__VERSION__]"

private val MODULE_TO_PATH_OLD: Map<String, String> = mapOf(
  "keras" to "tensorflow.python.keras.api._v[__VERSION__].keras",
  "estimator" to ESTIMATOR
)

// after 1.15.0rc0 and 2.0.0rc0
private const val OTHERS_NEW: String = "tensorflow_core._api.v[__VERSION__]"

private val MODULE_TO_PATH_NEW: Map<String, String> = mapOf(
  "compiler" to "tensorflow_core.compiler",
  "core" to "tensorflow_core.core",
  "tools" to "tensorflow_core.tools",
  "python" to "tensorflow_core.python",

  "keras" to "tensorflow_core.python.keras.api._v[__VERSION__].keras",

  "estimator" to ESTIMATOR
)

// after 2.0.0rc0
private val MODULE_TO_PATH_2_NEW: Map<String, String> = mapOf(
  "initializers" to "tensorflow_core.python.keras.api._v2.keras.initializers",
  "losses" to "tensorflow_core.python.keras.api._v2.keras.losses",
  "metrics" to "tensorflow_core.python.keras.api._v2.keras.metrics",
  "optimizers" to "tensorflow_core.python.keras.api._v2.keras.optimizers",

  "summary" to "tensorboard.summary._tf.summary"
)

internal fun getTensorFlowPathConfig(sdk: Sdk?): Pair<Map<String, String>, String> {
  val defaultVersion = "2"
  val pkg = getTensorFlowPackage(sdk) ?: return getTensorFlowPathConfig(defaultVersion, false)

  val v1WithOldConfig = listOf(pyRequirementVersionSpec(PyRequirementRelation.LT, "1.15.0rc0"))
  val v1WithNewConfig = listOf(
    pyRequirementVersionSpec(PyRequirementRelation.GTE, "1.15.0rc0"),
    pyRequirementVersionSpec(PyRequirementRelation.LT, "2.0.0a0")
  )

  val version = pkg.version
  return when {
    v1WithOldConfig.all { it.matches(version) } -> getTensorFlowPathConfig("1", true)
    v1WithNewConfig.all { it.matches(version) } -> getTensorFlowPathConfig("1", false)
    else -> getTensorFlowPathConfig(version.substringBefore('.', defaultVersion), false)
  }
}

internal fun takeFirstResolvedInTensorFlow(qualifiedName: String, context: PyQualifiedNameResolveContext): PsiElement? {
  return resolveQualifiedName(QualifiedName.fromDottedString(qualifiedName), context).firstOrNull()
}

private fun getTensorFlowPackage(sdk: Sdk?): PyPackage? {
  if (sdk == null) return null

  val unitTestMode = ApplicationManager.getApplication().isUnitTestMode
  val pkgManager = PyPackageManager.getInstance(sdk)

  val packages = if (unitTestMode) pkgManager.refreshAndGetPackages(false) else pkgManager.packages ?: return null
  return PyPsiPackageUtil.findPackage(packages, "tensorflow")
}

private fun getTensorFlowPathConfig(version: String, old: Boolean): Pair<Map<String, String>, String> {
  val moduleToPath = if (old) MODULE_TO_PATH_OLD else MODULE_TO_PATH_NEW
  val othersPath = if (old) OTHERS_OLD else OTHERS_NEW

  val preprocessedModuleToPath = moduleToPath.mapValuesTo(LinkedHashMap()) { (_, path) -> path.replaceFirst("[__VERSION__]", version) }

  if (version == "2") {
    preprocessedModuleToPath += MODULE_TO_PATH_2_NEW
  }

  return preprocessedModuleToPath to othersPath.replaceFirst("[__VERSION__]", version)
}
