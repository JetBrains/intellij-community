// Copyright 2000-2017 JetBrains s.r.o.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.jetbrains.python.packaging.requirement

@Deprecated(message = "Use com.jetbrains.python.packaging.PyRequirement instead. This class will be removed in 2018.2.")
data class PyRequirementVersion(val epoch: String? = null,
                                val release: String,
                                val pre: String? = null,
                                val post: String? = null,
                                val dev: String? = null,
                                val local: String? = null) {

  companion object {
    @JvmStatic
    fun release(release: String): PyRequirementVersion = PyRequirementVersion(release = release)
  }

  val presentableText: String
    get() =
    sequenceOf(epochPresentable(), release, pre, postPresentable(), devPresentable(), localPresentable())
      .filterNotNull()
      .joinToString(separator = "") { it }

  private fun epochPresentable() = if (epoch == null) null else "$epoch!"
  private fun postPresentable() = if (post == null) null else ".$post"
  private fun devPresentable() = if (dev == null) null else ".$dev"
  private fun localPresentable() = if (local == null) null else "+$local"
}