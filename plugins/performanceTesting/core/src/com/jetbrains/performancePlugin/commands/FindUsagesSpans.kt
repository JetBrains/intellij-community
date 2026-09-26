package com.jetbrains.performancePlugin

import org.jetbrains.annotations.NonNls

object FindUsagesSpans {
  const val BASE_NAME_BG: @NonNls String = "findUsagesInBackground"
  const val SPAN_NAME_BG: @NonNls String = BASE_NAME_BG
  const val FIRST_USAGE_SPAN_BACKGROUND: String = "${SPAN_NAME_BG}_firstUsage"

  const val BASE_NAME: @NonNls String = "findUsages"
  const val SPAN_NAME: @NonNls String = BASE_NAME
  const val PARENT_SPAN_NAME: @NonNls String = SPAN_NAME + "Parent"

  const val NAME_TW: String = "findUsagesInToolWindow"
  const val SPAN_NAME_TW: String = NAME_TW
}
