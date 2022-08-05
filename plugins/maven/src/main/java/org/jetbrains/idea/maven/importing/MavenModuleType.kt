// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

enum class MavenModuleType(val containsCode: Boolean, val containsMain: Boolean, val containsTest: Boolean) {
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