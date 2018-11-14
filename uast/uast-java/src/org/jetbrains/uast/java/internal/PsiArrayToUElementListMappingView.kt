// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.java.internal

import com.intellij.psi.PsiElement
import org.jetbrains.uast.UElement


internal class PsiArrayToUElementListMappingView<T : UElement, R : PsiElement>(private val sourceList: Array<R>,
                                                                               private val startIndex: Int,
                                                                               endIndex: Int,
                                                                               private val mapper: (R) -> T) : AbstractList<T>() {

  constructor(sourceList: Array<R>, mapper: (R) -> T) : this(sourceList, 0, sourceList.size, mapper)

  init {
    require(startIndex >= 0 && endIndex <= sourceList.size)
  }

  override val size: Int = endIndex - startIndex
  override fun get(index: Int): T = mapper(sourceList[startIndex + index])

  override fun contains(element: T): Boolean {
    val sourcePsi = element.sourcePsi ?: return super.contains(element)
    return sourceList.contains(sourcePsi)
  }

  override fun subList(fromIndex: Int, toIndex: Int): List<T> =
    PsiArrayToUElementListMappingView(sourceList, startIndex + fromIndex, startIndex + toIndex, mapper)
}