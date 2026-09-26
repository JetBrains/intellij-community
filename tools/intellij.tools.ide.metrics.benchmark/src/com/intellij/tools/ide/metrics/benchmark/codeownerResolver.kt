package com.intellij.tools.ide.metrics.benchmark

import com.intellij.codeowners.runtime.resolver.TestClassCodeOwnerResolverImpl
import com.intellij.openapi.diagnostic.fileLogger

private val LOG = fileLogger()

internal val codeOwners: TestClassCodeOwnerResolverImpl? by lazy {
  runCatching { TestClassCodeOwnerResolverImpl() }.onFailure { LOG.warn(it) }.getOrNull()
}