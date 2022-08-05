// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing;

public enum MavenModuleType {
  /**
   * Maven aggregator project (packaging = 'pom')
   */
  AGGREGATOR,
  /**
   * Regular maven project imported as a single module (with main and test folders and dependencies)
   */
  SINGLE_MODULE,
  /**
   * Maven project imported into several modules, with separate main and test folders dependencies.
   * The compound module has a content root, but neither sources, not dependencies.
   */
  COMPOUND_MODULE,
  /**
   * Module containing only main folders and dependencies
   */
  MAIN_ONLY,
  /**
   * Module containing only test folders and dependencies
   */
  TEST_ONLY
}
