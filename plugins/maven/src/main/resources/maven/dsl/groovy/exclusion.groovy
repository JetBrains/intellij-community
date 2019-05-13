// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package maven.dsl.groovy

class exclusion {
/**
 * The artifact ID of the project to exclude.
 */
  private String artifactId;

  /**
   * The group ID of the project to exclude.
   */
  private String groupId;

/**
 * The artifact ID of the project to exclude.
 */
  void artifactId(String artifactId) {}

  /**
   * The group ID of the project to exclude.
   */
  void groupId(String groupId) {}
}
