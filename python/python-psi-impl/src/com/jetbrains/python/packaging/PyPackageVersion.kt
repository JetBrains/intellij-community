// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging

/**
 * Presents normalized version of python package or requirement as described in [PEP-440][https://www.python.org/dev/peps/pep-0440/#normalization].
 *
 * Instances of this class MUST be obtained from [PyPackageVersionNormalizer.normalize].
 */
data class PyPackageVersion internal constructor(val epoch: String? = null,
                                                 val release: String,
                                                 val pre: String? = null,
                                                 val post: String? = null,
                                                 val dev: String? = null,
                                                 val local: String? = null) {

  /**
   * String representation that follows spelling described in [PEP-440][https://www.python.org/dev/peps/pep-0440/#normalization]
   */
  val presentableText: String
    get() =
      sequenceOf(epochPresentable(), release, pre, postPresentable(), devPresentable(), localPresentable())
        .filterNotNull()
        .joinToString(separator = "") { it }

  private fun epochPresentable() = epoch?.let { "$it!" }
  private fun postPresentable() = post?.let { ".$it" }
  private fun devPresentable() = dev?.let { ".$it" }
  private fun localPresentable() = local?.let { "+$it" }
}