// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import org.jetbrains.annotations.ApiStatus.Internal

sealed interface MavenModuleType {
  /**
   * Module contains main, test or both folders and dependencies
   */
  val containsCode: Boolean

  /**
   * Module contains main folders and dependencies
   */
  val containsMain: Boolean

  /**
   * Module contains test folders and dependencies
   */
  val containsTest: Boolean
}

@Internal
enum class StandardMavenModuleType(override val containsCode: Boolean,
                                   override val containsMain: Boolean,
                                   override val containsTest: Boolean) : MavenModuleType {
  /**
   * Maven aggregator project (packaging = 'pom')
   */
  AGGREGATOR(containsCode = false, containsMain = false, containsTest = false),

  /**
   * Regular maven project imported as a single module (with main and test folders and dependencies)
   */
  SINGLE_MODULE(containsCode = true, containsMain = true, containsTest = true),

  /**
   * Maven project imported into several modules, with separate main and test folders dependencies.
   * The compound module has a content root, but neither sources, not dependencies.
   */
  COMPOUND_MODULE(containsCode = false, containsMain = false, containsTest = false),

  /**
   * Module containing only main folders and dependencies
   */
  MAIN_ONLY(containsCode = true, containsMain = true, containsTest = false),

  /**
   * Module containing only test folders and dependencies
   */
  TEST_ONLY(containsCode = true, containsMain = false, containsTest = true)
}