// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.java.internal

import com.intellij.psi.PsiElement
import org.jetbrains.uast.UElement


internal class PsiArrayToUElementListMappingView<T : UElement, R : PsiElement> private constructor(
  private val sourceList: Array<R>,
  private val lazyCached: Lazy<List<Lazy<T>>>, // ideally it should be something like lazy-paging-list
  // which will provide an index-based access but will not allocate the whole `sourceList.size` elements until necessary
  private val startIndex: Int,
  private val endIndex: Int) : AbstractList<T>() {

  constructor(sourceList: Array<R>, mapper: (R) -> T) :
    this(sourceList, lazy { sourceList.map { lazy { mapper(it) } } }, 0, sourceList.size)

  init {
    require(startIndex >= 0 && endIndex <= sourceList.size)
  }

  override val size: Int = endIndex - startIndex
  override fun get(index: Int): T = lazyCached.value[startIndex + index].value

  override fun contains(element: T): Boolean {
    val sourcePsi = element.sourcePsi ?: return super.contains(element)

    for (i in startIndex until endIndex) {
      if (sourceList[i] == sourcePsi) return true
    }
    return false
  }

  override fun subList(fromIndex: Int, toIndex: Int): List<T> =
    PsiArrayToUElementListMappingView(sourceList, lazyCached, startIndex + fromIndex, startIndex + toIndex)
}