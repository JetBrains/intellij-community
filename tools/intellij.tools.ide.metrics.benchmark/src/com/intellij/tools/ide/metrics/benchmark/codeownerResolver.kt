package com.intellij.tools.ide.metrics.benchmark

import com.intellij.codeowners.runtime.resolver.TestClassCodeOwnerResolverImpl
import com.intellij.openapi.diagnostic.fileLogger

private val LOG = fileLogger()

private object CodeOwners {
  val resolver: TestClassCodeOwnerResolverImpl? = runCatching { TestClassCodeOwnerResolverImpl() }
    .onFailure { if (it !is NoClassDefFoundError) LOG.warn(it) }
    .getOrNull()
}

/**
 * Returns the code owner group of [testClass], or `null` when it is unknown.
 * The published Maven artifacts hold no code owner module, so outside the monorepo the resolver class is absent.
 * The function then returns `null` and throws nothing.
 */
internal fun codeOwnerGroupName(testClass: Class<*>): String? =
  runCatching { CodeOwners.resolver?.getOwnerGroupName(testClass) }.getOrNull()
