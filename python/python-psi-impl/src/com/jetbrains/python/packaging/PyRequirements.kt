// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging

import com.intellij.openapi.util.text.StringUtil
import com.jetbrains.python.packaging.requirement.PyRequirementRelation
import com.jetbrains.python.packaging.requirement.PyRequirementVersionSpec

/**
 * This helper is not an API, consider using methods listed below.
 *
 * @see PyPackageManager.parseRequirement
 * @see PyPackageManager.parseRequirements
 *
 * @see PyRequirementParser.fromLine
 * @see PyRequirementParser.fromText
 * @see PyRequirementParser.fromFile
 */
fun pyRequirement(name: String): PyRequirement = PyRequirementImpl(name, emptyList(), listOf(name), "")

/**
 * This helper is not an API, consider using methods listed below.
 * If given version could not be normalized, then specified relation will be replaced with [PyRequirementRelation.STR_EQ].
 *
 * @see PyPackageManager.parseRequirement
 * @see PyPackageManager.parseRequirements
 *
 * @see PyRequirementParser.fromLine
 * @see PyRequirementParser.fromText
 * @see PyRequirementParser.fromFile
 *
 * @see pyRequirementVersionSpec
 */
fun pyRequirement(name: String, relation: PyRequirementRelation, version: String): PyRequirement {
  val versionSpec = pyRequirementVersionSpec(relation, version)
  return PyRequirementImpl(name, listOf(versionSpec), listOf(name + relation.presentableText + version), "")
}

/**
 * This method could be used to obtain [PyRequirementVersionSpec] instances with specified relation and version.
 * If given version could not be normalized, then specified relation will be replaced with [PyRequirementRelation.STR_EQ].
 *
 * @see PyPackageVersionNormalizer.normalize
 */
fun pyRequirementVersionSpec(relation: PyRequirementRelation, version: String): PyRequirementVersionSpec {
  if (relation == PyRequirementRelation.STR_EQ) return PyRequirementVersionSpecImpl(relation, null, version)

  return PyPackageVersionNormalizer
    .normalize(version)
    .let {
      if (it == null) PyRequirementVersionSpecImpl(PyRequirementRelation.STR_EQ, null, version) else pyRequirementVersionSpec(relation, it)
    }
}

/**
 * This method could be used to obtain [PyRequirementVersionSpec] instances with specified relation and version.
 *
 * @see PyPackageVersion
 * @see PyPackageVersionNormalizer.normalize
 */
fun pyRequirementVersionSpec(relation: PyRequirementRelation, version: PyPackageVersion): PyRequirementVersionSpec {
  return PyRequirementVersionSpecImpl(relation, version, version.presentableText)
}

/**
 * Instances of this class MUST be obtained from [pyRequirementVersionSpec].
 */
private data class PyRequirementVersionSpecImpl(private val relation: PyRequirementRelation,
                                                private val parsedVersion: PyPackageVersion?,
                                                private val version: String) : PyRequirementVersionSpec {

  override fun getRelation() = relation
  override fun getVersion() = version

  override fun matches(version: String): Boolean {
    val comparator = PyPackageVersionComparator.STR_COMPARATOR

    return when (relation) {
      PyRequirementRelation.LT -> comparator.compare(version, this.version) < 0
      PyRequirementRelation.LTE -> comparator.compare(version, this.version) <= 0
      PyRequirementRelation.GT -> comparator.compare(version, this.version) > 0
      PyRequirementRelation.GTE -> comparator.compare(version, this.version) >= 0
      PyRequirementRelation.EQ -> {
        parsedVersion!!

        val publicAndLocalVersions = splitIntoPublicAndLocalVersions(parsedVersion)
        val otherPublicAndLocalVersions = splitIntoPublicAndLocalVersions(version)
        val publicVersionsAreSame = comparator.compare(otherPublicAndLocalVersions.first, publicAndLocalVersions.first) == 0

        return publicVersionsAreSame &&
               (publicAndLocalVersions.second.isEmpty() || otherPublicAndLocalVersions.second == publicAndLocalVersions.second)
      }
      PyRequirementRelation.NE -> comparator.compare(version, this.version) != 0
      PyRequirementRelation.COMPATIBLE -> {
        parsedVersion!!

        return pyRequirementVersionSpec(PyRequirementRelation.GTE,
                                        parsedVersion).matches(version) &&
               pyRequirementVersionSpec(PyRequirementRelation.EQ,
                                        toEqPartOfCompatibleRelation(parsedVersion)).matches(version)
      }
      PyRequirementRelation.STR_EQ -> version == this.version
      else -> false
    }
  }

  private fun splitIntoPublicAndLocalVersions(version: PyPackageVersion): Pair<String, String> {
    return version.copy(local = null).presentableText to StringUtil.notNullize(version.local)
  }

  private fun splitIntoPublicAndLocalVersions(version: String): Pair<String, String> {
    val publicAndLocalVersions = version.split('+', limit = 2)

    val publicVersion = publicAndLocalVersions[0]
    val localVersion = if (publicAndLocalVersions.size == 1) "" else publicAndLocalVersions[1]

    return publicVersion to localVersion
  }

  private fun toEqPartOfCompatibleRelation(version: PyPackageVersion): PyPackageVersion {
    val release = version.release
    val lastPoint = release.lastIndexOf('.')

    return if (lastPoint == -1) version
    else PyPackageVersion(version.epoch, release.substring(0, lastPoint + 1) + "*", null, null, null, null)
  }
}