// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.core.diagnostic.ex

/**
 * @author Vitaliy.Bibaev
 */
class TraceEvaluationException(message: String, traceExpression: String, cause: Throwable?) :
  TraceException(message, traceExpression, cause)
