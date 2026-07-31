// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging

import com.intellij.python.requirements.PyPackageVersion

@Deprecated(
  "This object has been moved to the com.intellij.python.requirements package.",
  ReplaceWith("com.intellij.python.requirements.PyPackageVersionComparator",
              imports = ["com.intellij.python.requirements.PyPackageVersionComparator"])
)
object PyPackageVersionComparator : Comparator<PyPackageVersion> {
  @JvmStatic
  val STR_COMPARATOR: Comparator<String> = com.intellij.python.requirements.PyPackageVersionComparator.STR_COMPARATOR

  override fun compare(o1: PyPackageVersion, o2: PyPackageVersion): Int =
    com.intellij.python.requirements.PyPackageVersionComparator.compare(o1, o2)
}