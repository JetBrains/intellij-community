// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging

import com.jetbrains.python.packaging.requirement.PyRequirementVersion

/**
 * Presents normalized version of python package or requirement as described [here][https://www.python.org/dev/peps/pep-0440/#normalization].
 *
 * Instances of this class MUST be converted from [com.jetbrains.python.packaging.requirement.PyRequirementVersionNormalizer.normalize] result.
 */
data class PyPackageVersion(val epoch: String? = null,
                            val release: String,
                            val pre: String? = null,
                            val post: String? = null,
                            val dev: String? = null,
                            val local: String? = null) {

  override fun toString() = PyRequirementVersion(epoch, release, pre, post, dev, local).presentableText
}