// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.services.shared

import java.util.*


object LanguageLevelComparator : Comparator<LanguageLevelHolder> {
  override fun compare(o1: LanguageLevelHolder, o2: LanguageLevelHolder): Int =
    // Backward: first python is the highest
    o1.languageLevel.compareTo(o2.languageLevel) * -1
}

object UiComparator : Comparator<UiHolder> {
  override fun compare(o1: UiHolder, o2: UiHolder): Int =
    Objects.compare(o1.ui, o2.ui, Comparator.nullsFirst(UICustomization::compareTo))
}

class LanguageLevelWithUiComparator<T> : Comparator<T> where T : LanguageLevelHolder, T : UiHolder {
  override fun compare(o1: T, o2: T): Int =
    LanguageLevelComparator.compare(o1, o2) * 10 + UiComparator.compare(o1, o2)
}