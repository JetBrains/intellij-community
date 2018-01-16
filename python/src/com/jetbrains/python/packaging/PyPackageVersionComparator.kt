// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging

import com.intellij.webcore.packaging.PackageVersionComparator
import com.jetbrains.python.packaging.requirement.PyRequirementRelation
import com.jetbrains.python.packaging.requirement.PyRequirementVersion
import com.jetbrains.python.packaging.requirement.PyRequirementVersionNormalizer
import one.util.streamex.EntryStream
import one.util.streamex.StreamEx
import java.math.BigInteger
import java.util.stream.Stream

/**
 * Compares normalized [PyPackageVersions][PyPackageVersion].
 *
 * Based on [PEP-440][https://www.python.org/dev/peps/pep-0440/#summary-of-permitted-suffixes-and-relative-ordering].
 */
object PyPackageVersionComparator : Comparator<PyPackageVersion> {

  @JvmStatic
  val STR_COMPARATOR: Comparator<String> = Comparator { o1, o2 ->
    val normalized1 = PyRequirementVersionNormalizer.normalize(o1)?.toPkgVersion()
                      ?: return@Comparator PackageVersionComparator.VERSION_COMPARATOR.compare(o1, o2)

    val normalized2 = PyRequirementVersionNormalizer.normalize(o2)?.toPkgVersion()
                      ?: return@Comparator PackageVersionComparator.VERSION_COMPARATOR.compare(o1, o2)

    compare(normalized1, normalized2)
  }

  override fun compare(o1: PyPackageVersion, o2: PyPackageVersion): Int {
    val epochs = compareEpochs(o1, o2)
    if (epochs != 0) return epochs

    val releases = compareReleases(o1, o2)
    if (releases != PyRequirementRelation.EQ) {
      if (releases == PyRequirementRelation.LT) return -1
      if (releases == PyRequirementRelation.GT) return 1
      return 0
    }

    val pres = comparePres(o1, o2)
    if (pres != 0) return pres

    val posts = comparePosts(o1, o2)
    if (posts != 0) return posts

    val devs = compareDevs(o1, o2)
    if (devs != 0) return devs

    return compareLocals(o1, o2)
  }

  private fun PyRequirementVersion.toPkgVersion() = PyPackageVersion(epoch, release, pre, post, dev, local)

  /**
   * @see com.jetbrains.python.packaging.requirement.PyRequirementVersionNormalizer.normalizeEpoch
   */
  private fun compareEpochs(o1: PyPackageVersion, o2: PyPackageVersion) = compareAsInts(
    o1.epoch ?: "0", o2.epoch ?: "0")

  /**
   * @see com.jetbrains.python.packaging.requirement.PyRequirementVersionNormalizer.normalizeRelease
   */
  private fun compareReleases(o1: PyPackageVersion, o2: PyPackageVersion): PyRequirementRelation {
    for ((releasePart1, releasePart2) in zipLongest(o1.release.split('.'),
                                                    o2.release.split('.'), "0")) {
      if (releasePart1 == "*" || releasePart2 == "*") return PyRequirementRelation.COMPATIBLE

      val releaseParts = compareAsInts(releasePart1, releasePart2)
      if (releaseParts < 0) return PyRequirementRelation.LT
      if (releaseParts > 0) return PyRequirementRelation.GT
    }

    val devOnly1 = o1.dev != null && o1.pre == null && o1.post == null
    val devOnly2 = o2.dev != null && o2.pre == null && o2.post == null

    if (devOnly1 || devOnly2) {
      if (!devOnly2) return PyRequirementRelation.LT
      if (!devOnly1) return PyRequirementRelation.GT

      val devs = compareDevs(o1, o2)
      if (devs < 0) return PyRequirementRelation.LT
      if (devs > 0) return PyRequirementRelation.GT
    }

    return PyRequirementRelation.EQ
  }

  /**
   * @see com.jetbrains.python.packaging.requirement.PyRequirementVersionNormalizer.normalizePost
   */
  private fun comparePosts(o1: PyPackageVersion, o2: PyPackageVersion): Int {
    return compareAsInts(o1.post?.substring(4) ?: "-1",
                         o2.post?.substring(4) ?: "-1")
  }

  /**
   * @see com.jetbrains.python.packaging.requirement.PyRequirementVersionNormalizer.normalizePre
   */
  private fun comparePres(o1: PyPackageVersion, o2: PyPackageVersion): Int {
    val pre1 = o1.pre
    val pre2 = o2.pre

    if (pre1 == null && pre2 == null) return 0
    if (pre2 == null) return -1
    if (pre1 == null) return 1

    val preType1 = pre1.filter(Char::isLetter)
    val preType2 = pre2.filter(Char::isLetter)

    val preTypes = preType1.compareTo(preType2)
    if (preTypes != 0) return preTypes

    return pre1.substring(preType1.length).compareTo(pre2.substring(preType2.length))
  }

  /**
   * @see com.jetbrains.python.packaging.requirement.PyRequirementVersionNormalizer.normalizeDev
   */
  private fun compareDevs(o1: PyPackageVersion, o2: PyPackageVersion): Int {
    val dev1 = o1.dev
    val dev2 = o2.dev

    if (dev1 == null && dev2 == null) return 0
    if (dev2 == null) return -1
    if (dev1 == null) return 1

    return dev1.substring(3).compareTo(dev2.substring(3))
  }

  /**
   * @see com.jetbrains.python.packaging.requirement.PyRequirementVersionNormalizer.normalizeLocal
   */
  private fun compareLocals(o1: PyPackageVersion, o2: PyPackageVersion) = (o1.local ?: "").compareTo(o2.local ?: "")

  private fun compareAsInts(o1: String, o2: String) = BigInteger(o1).compareTo(BigInteger(o2))

  private fun <E> zipLongest(c1: Collection<E>, c2: Collection<E>, fillValue: E): EntryStream<E, E> {
    val maxSize = maxOf(c1.size, c2.size).toLong()

    return StreamEx
      .of(fillToLength(c1, fillValue, maxSize))
      .zipWith(fillToLength(c2, fillValue, maxSize))
  }

  private fun <E> fillToLength(c: Collection<E>, fillValue: E, length: Long): Stream<E> {
    return StreamEx
      .of(c)
      .append(StreamEx.constant(fillValue, length))
      .limit(length)
  }
}