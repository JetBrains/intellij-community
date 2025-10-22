package com.intellij.lambda.testFramework.testApi.utils

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds


/**
 * Default latency to be used in all the scriptingApi methods.
 * By convention to honor the latency setting test
 * methods performing ActionA should pump EDT for this duration before calling the actual ActionA.
 */
val defaultTestLatency: Duration = 300.milliseconds
