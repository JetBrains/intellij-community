package com.intellij.ide.starter

const val IS_STARTER_PERFORMANCE_TEST_ENV = "intellij.is.starter.performance.test"

val isStarterPerformanceTest = System.getenv(IS_STARTER_PERFORMANCE_TEST_ENV).toBoolean()