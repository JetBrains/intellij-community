package com.intellij.turboComplete.ranking

import com.intellij.platform.ml.impl.turboComplete.KindVariety

interface KindSorterProvider {
  val kindVariety: KindVariety

  fun createSorter(): KindRelevanceSorter
}