// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging

import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.packaging.requirement.PyRequirementVersion
import com.jetbrains.python.packaging.requirement.PyRequirementVersionNormalizer
import one.util.streamex.StreamEx

class PyPackageVersionComparatorTest : PyTestCase() {

  fun testEpoch() {
    check(PyPackageVersion("0", "1.2.3"),
          PyPackageVersion("0", "1.2.3"),
          PyPackageVersion("1", "1.2.3"))
  }

  fun testSameLengthRelease() {
    check(PyPackageVersion(release = "1.2"),
          PyPackageVersion(release = "1.2"),
          PyPackageVersion(release = "1.3"))

    check(PyPackageVersion(release = "1.2.3"),
          PyPackageVersion(release = "1.2.3"),
          PyPackageVersion(release = "1.2.4"))
  }

  fun testDifferentLengthRelease() {
    check(PyPackageVersion(release = "1.2"),
          PyPackageVersion(release = "1.2.0"),
          PyPackageVersion(release = "1.2.1"))

    check(PyPackageVersion(release = "1.2.3"),
          PyPackageVersion(release = "1.2.3.0"),
          PyPackageVersion(release = "1.2.3.1"))
  }

  fun testPost() {
    check(PyPackageVersion(release = "1.2", post = "post1"),
          PyPackageVersion(release = "1.2", post = "post1"),
          PyPackageVersion(release = "1.2", post = "post2"))
  }

  fun testPre() {
    check(PyPackageVersion(release = "1.2", pre = "a1"),
          PyPackageVersion(release = "1.2", pre = "a1"),
          PyPackageVersion(release = "1.2", pre = "a2"))
  }

  fun testDev() {
    check(PyPackageVersion(release = "1.2", dev = "dev1"),
          PyPackageVersion(release = "1.2", dev = "dev1"),
          PyPackageVersion(release = "1.2", dev = "dev2"))
  }

  fun testLocal() {
    check(PyPackageVersion(release = "1.2", local = "abc"),
          PyPackageVersion(release = "1.2", local = "abc"),
          PyPackageVersion(release = "1.2", local = "def"))
  }

  fun testSameReleaseOrder() {
    checkOrder(
      listOf(
        normalize("1.0.dev1"),
        normalize("1.0.a1.dev1"),
        normalize("1.0.a1"),
        normalize("1.0.a1.post1"),
        normalize("1.0.b1.dev1"),
        normalize("1.0.b1"),
        normalize("1.0.b1.post1"),
        normalize("1.0.c1.dev1"),
        normalize("1.0.c1"),
        normalize("1.0.c1.post1"),
        PyPackageVersion(release = "1.0"),
        normalize("1.0.post1.dev1"),
        normalize("1.0.post1")
      )
    )
  }

  fun testDifferentReleaseOrder() {
    checkOrder(
      listOf(
        normalize("1.0.dev1"),
        normalize("1.0.a1.dev1"),
        normalize("1.0.a1"),
        normalize("1.0.a1.post1"),
        normalize("1.0.b1.dev1"),
        normalize("1.0.b1"),
        normalize("1.0.b1.post1"),
        normalize("1.0.c1.dev1"),
        normalize("1.0.c1"),
        normalize("1.0.c1.post1"),
        PyPackageVersion(release = "1.0"),
        normalize("1.0.post1.dev1"),
        normalize("1.0.post1"),

        normalize("2.0.dev1"),
        normalize("2.0.a1.dev1"),
        normalize("2.0.a1"),
        normalize("2.0.a1.post1"),
        normalize("2.0.b1.dev1"),
        normalize("2.0.b1"),
        normalize("2.0.b1.post1"),
        normalize("2.0.c1.dev1"),
        normalize("2.0.c1"),
        normalize("2.0.c1.post1"),
        PyPackageVersion(release = "2.0"),
        normalize("2.0.post1.dev1"),
        normalize("2.0.post1")
      )
    )
  }

  fun testCompatible() {
    val pkg = PyPackageVersion(release = "1.*")

    listOf(
      normalize("1.0.dev1"),
      normalize("1.0.a1.dev1"),
      normalize("1.0.a1"),
      normalize("1.0.a1.post1"),
      normalize("1.0.b1.dev1"),
      normalize("1.0.b1"),
      normalize("1.0.b1.post1"),
      normalize("1.0.c1.dev1"),
      normalize("1.0.c1"),
      normalize("1.0.c1.post1"),
      PyPackageVersion(release = "1.0"),
      normalize("1.0.post1.dev1"),
      normalize("1.0.post1")
    )
      .forEach { check(it, pkg, true) }
  }
  
  private fun normalize(version: String) = PyRequirementVersionNormalizer.normalize(version)!!.toPkgVersion()

  private fun PyRequirementVersion.toPkgVersion() = PyPackageVersion(epoch, release, pre, post, dev, local)

  private fun check(less: PyPackageVersion, equal: PyPackageVersion, greater: PyPackageVersion) {
    check(less, equal, true)
    check(less, greater)
  }

  private fun checkOrder(pkgs: List<PyPackageVersion>) {
    for (pkg in pkgs) {
      StreamEx.of(pkgs).dropWhile { it != pkg }.skip(1).forEach { check(pkg, it) }
    }
  }

  private fun check(pkg1: PyPackageVersion, pkg2: PyPackageVersion, equal: Boolean = false) {
    val message = "pkg1: $pkg1, pkg2: $pkg2"

    if (equal) {
      assertFalse(message, pkg1 === pkg2)

      assertTrue(message, PyPackageVersionComparator.compare(pkg1, pkg2) == 0)
      assertTrue(message, PyPackageVersionComparator.STR_COMPARATOR.compare(pkg1.toString(), pkg2.toString()) == 0)

      assertTrue(message, PyPackageVersionComparator.compare(pkg2, pkg1) == 0)
      assertTrue(message, PyPackageVersionComparator.STR_COMPARATOR.compare(pkg2.toString(), pkg1.toString()) == 0)
    }
    else {
      assertTrue(message, PyPackageVersionComparator.compare(pkg1, pkg2) < 0)
      assertTrue(message, PyPackageVersionComparator.STR_COMPARATOR.compare(pkg1.toString(), pkg2.toString()) < 0)

      assertTrue(message, PyPackageVersionComparator.compare(pkg2, pkg1) > 0)
      assertTrue(message, PyPackageVersionComparator.STR_COMPARATOR.compare(pkg2.toString(), pkg1.toString()) > 0)
    }
  }
}