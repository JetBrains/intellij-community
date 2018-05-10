// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging

import com.jetbrains.python.fixtures.PyTestCase
import one.util.streamex.StreamEx

class PyPackageVersionComparatorTest : PyTestCase() {

  fun testEpoch() {
    check(normalize("0!1.2.3"),
          normalize("0!1.2.3"),
          normalize("1!1.2.3"))
  }

  fun testSameLengthRelease() {
    check(normalize("1.2"),
          normalize("1.2"),
          normalize("1.3"))

    check(normalize("1.2.3"),
          normalize("1.2.3"),
          normalize("1.2.4"))
  }

  fun testDifferentLengthRelease() {
    check(normalize("1.2"),
          normalize("1.2.0"),
          normalize("1.2.1"))

    check(normalize("1.2.3"),
          normalize("1.2.3.0"),
          normalize("1.2.3.1"))
  }

  fun testPost() {
    check(normalize("1.2.post1"),
          normalize("1.2.post1"),
          normalize("1.2.post2"))
  }

  fun testPre() {
    check(normalize("1.2a1"),
          normalize("1.2a1"),
          normalize("1.2a2"))
  }

  fun testDev() {
    check(normalize("1.2.dev1"),
          normalize("1.2.dev1"),
          normalize("1.2.dev2"))
  }

  fun testLocal() {
    check(normalize("1.2+abc"),
          normalize("1.2+abc"),
          normalize("1.2+def"))
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
        normalize("1.0"),
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
        normalize("1.0"),
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
        normalize("2.0"),
        normalize("2.0.post1.dev1"),
        normalize("2.0.post1")
      )
    )
  }

  fun testCompatible() {
    val pkg = normalize("1.*")

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
      normalize("1.0"),
      normalize("1.0.post1.dev1"),
      normalize("1.0.post1")
    )
      .forEach { check(it, pkg, true) }
  }

  private fun normalize(version: String) = PyPackageVersionNormalizer.normalize(version)!!

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
      assertTrue(message, PyPackageVersionComparator.STR_COMPARATOR.compare(pkg1.presentableText, pkg2.presentableText) == 0)

      assertTrue(message, PyPackageVersionComparator.compare(pkg2, pkg1) == 0)
      assertTrue(message, PyPackageVersionComparator.STR_COMPARATOR.compare(pkg2.presentableText, pkg1.presentableText) == 0)
    }
    else {
      assertTrue(message, PyPackageVersionComparator.compare(pkg1, pkg2) < 0)
      assertTrue(message, PyPackageVersionComparator.STR_COMPARATOR.compare(pkg1.presentableText, pkg2.presentableText) < 0)

      assertTrue(message, PyPackageVersionComparator.compare(pkg2, pkg1) > 0)
      assertTrue(message, PyPackageVersionComparator.STR_COMPARATOR.compare(pkg2.presentableText, pkg1.presentableText) > 0)
    }
  }
}