// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.junit5.framework.impl

import com.intellij.testFramework.junit5.fixture.LookupFixtureExtension.Companion.getLookupFixtureManager
import com.intellij.testFramework.junit5.fixture.LookupFixtureManager
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * Returns the [LookupFixtureManager] from the parent [ExtensionContext].
 *
 * Use from `BeforeEach`/`AfterEach` callbacks to reach class-level fixtures
 * (e.g. project, module, SDK) that were registered in `BeforeAll`, since
 * `Namespace.GLOBAL` stores are not inherited along the JUnit 5 context hierarchy.
 */
internal fun ExtensionContext.getParentLookupFixtureManager(): LookupFixtureManager {
  val parent = parent.orElseThrow {
    IllegalStateException(
      "Parent ExtensionContext is not available. " +
      "Make sure this is called from a BeforeEach/AfterEach callback."
    )
  }
  return parent.getLookupFixtureManager()
}
