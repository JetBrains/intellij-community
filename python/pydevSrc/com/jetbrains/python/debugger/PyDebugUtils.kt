// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger

interface AbstractPolicy


fun interface PolicyListener {
  fun valuesPolicyUpdated()
}


enum class ValuesPolicy : AbstractPolicy {
  SYNC,
  ASYNC,
  ON_DEMAND
}

enum class QuotingPolicy : AbstractPolicy {
  SINGLE,
  DOUBLE,
  NONE
}

fun getQuotingString(policy: QuotingPolicy, value: String): String =
  when (policy) {
    QuotingPolicy.SINGLE -> value
    QuotingPolicy.DOUBLE -> value.replace("'", "\"")
    QuotingPolicy.NONE -> value.replace("'", "")
  }