// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.java.internal


internal class ArrayMappingListView<T, R>(private val sourceList: Array<R>, private val mapper: (R) -> T) : AbstractList<T>() {
  override val size: Int = sourceList.size
  override fun get(index: Int): T = mapper(sourceList[index])
}