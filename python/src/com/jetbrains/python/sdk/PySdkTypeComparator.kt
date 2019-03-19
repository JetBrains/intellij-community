// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus
import java.util.*
import kotlin.Comparator

@ApiStatus.Experimental
interface PySdkTypeComparator : Comparator<PySdkTypeComparator.PySdkType> {

  override fun compare(type1: PySdkType, type2: PySdkType): Int

  enum class PySdkType {
    VirtualEnv, CondaEnv, PipEnv, SystemWide, Other
  }

  companion object {
    private val EP_NAME = ExtensionPointName.create<PySdkTypeComparator>("Pythonid.pythonSdkTypeComparator")

    private val COMPARATOR by lazy {
      EP_NAME.extensions()
        .map { it as Comparator<PySdkType> }
        .reduce(Comparator { _, _ -> 0 }) { a, b -> a.thenComparing(b) }
    }

    fun <T> MutableList<T>.sortBySdkTypes(key: (T) -> PySdkType): MutableList<T> = also {
      this.sortWith(Comparator { o1, o2 -> COMPARATOR.compare(key(o1), key(o2)) })
    }
  }
}