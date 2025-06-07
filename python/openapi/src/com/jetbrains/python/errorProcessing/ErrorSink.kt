// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.errorProcessing

import kotlinx.coroutines.flow.FlowCollector

/**
 * [emit] user-readable [PyError] errors here.
 *
 * This class should be used by the topmost classes, tightly coupled to the UI.
 * For the most business-logic and backend functions please return [PyResult] or [PyError].
 *
 * There will be a unified sink soon to show and log errors.
 * Currently, only [com.jetbrains.python.util.ShowingMessageErrorSync] is a well-known implementation.
 *
 * See [PyError]
 */
typealias ErrorSink = FlowCollector<PyError>