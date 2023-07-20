package com.intellij.turboComplete

data class SortingExecutorPreferences(
  val policyForMostRelevant: MostRelevantKindPolicy,
  val policyForNoneKind: NoneKindPolicy,
  val executeMostRelevantWhenPassed: Int,
) {
  enum class MostRelevantKindPolicy {
    BUFFER,
    PASS_TO_RESULT,
  }

  enum class NoneKindPolicy {
    BUFFER,
    PASS_TO_RESULT,
    CREATE_NONE_KIND,
  }
}