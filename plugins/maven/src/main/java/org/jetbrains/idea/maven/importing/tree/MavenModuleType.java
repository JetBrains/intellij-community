// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.tree;

public enum MavenModuleType {
  /**
   * Type for maven aggregator project (where packaging = 'pom')
   */
  AGGREGATOR,
  /**
   * Type for simple maven  project (where main and test merged into one module, maven project not splitting)
   */
  MAIN_TEST,
  /**
   * Synthetic module type for maven project for aggregating main and test modules.
   * Has two child modules: main and test. (maven project is split into main and test)
   */
  AGGREGATOR_MAIN_TEST,
  /**
   * Type for separate main module.
   */
  MAIN,
  /**
   * Type for separate test module.
   */
  TEST
}
