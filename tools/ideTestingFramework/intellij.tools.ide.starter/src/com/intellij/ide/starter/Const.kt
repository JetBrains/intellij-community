package com.intellij.ide.starter

const val IS_STARTER_PERFORMANCE_TEST_ENV = "IS_STARTER_PERFORMANCE_TEST"

val isStarterPerformanceTest = System.getenv(IS_STARTER_PERFORMANCE_TEST_ENV).toBoolean()