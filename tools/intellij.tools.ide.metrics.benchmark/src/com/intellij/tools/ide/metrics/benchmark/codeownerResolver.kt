package com.intellij.tools.ide.metrics.benchmark

import com.intellij.codeowners.runtime.resolver.TestClassCodeOwnerResolverImpl

internal val codeOwnerResolver: TestClassCodeOwnerResolverImpl? by lazy {
  TestClassCodeOwnerResolverImpl()
}