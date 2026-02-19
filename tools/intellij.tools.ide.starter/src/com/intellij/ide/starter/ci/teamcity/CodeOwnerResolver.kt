package com.intellij.ide.starter.ci.teamcity

/**
 * Resolves code owner for the current test.
 * Default implementation is no-op; override in DI for actual resolution.
 */
interface CodeOwnerResolver {
  /**
   * Resolves the code owner for the current test method.
   * Returns null if no test is currently running or if owner cannot be determined.
   */
  fun getOwnerGroupName(): String?
}

/**
 * No-op implementation used when code owner resolution is not available.
 */
object NoOpCodeOwnerResolver : CodeOwnerResolver {
  override fun getOwnerGroupName(): String? = null
}
