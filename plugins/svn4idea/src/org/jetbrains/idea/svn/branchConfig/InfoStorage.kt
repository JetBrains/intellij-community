// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.branchConfig

class InfoStorage<T>(value: T, infoReliability: InfoReliability) {
  var value: T = value
    private set
  var infoReliability: InfoReliability = infoReliability
    private set

  fun accept(infoStorage: InfoStorage<T>): Boolean {
    val override = infoStorage.infoReliability.shouldOverride(infoReliability)

    if (override) {
      value = infoStorage.value
      infoReliability = infoStorage.infoReliability
    }

    return override
  }
}

enum class InfoReliability {
  empty {
    override val overriddenBy: Array<InfoReliability> get() = arrayOf(defaultValues, setByUser)
  },
  defaultValues {
    override val overriddenBy: Array<InfoReliability> get() = arrayOf(setByUser)
  },
  setByUser {
    override val overriddenBy: Array<InfoReliability> get() = arrayOf(setByUser)
  };

  protected abstract val overriddenBy: Array<InfoReliability>

  fun shouldOverride(other: InfoReliability): Boolean = this in other.overriddenBy
}
