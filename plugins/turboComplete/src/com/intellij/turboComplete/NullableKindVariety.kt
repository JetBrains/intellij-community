package com.intellij.turboComplete

import com.intellij.platform.ml.impl.turboComplete.KindVariety

enum class NullableKindName {
  NONE_KIND
}

class NullableKindVariety(private val baseKindVariety: KindVariety) : KindVariety by baseKindVariety {

  companion object {
    fun KindVariety.withNullableKind() = NullableKindVariety(this)
  }
}