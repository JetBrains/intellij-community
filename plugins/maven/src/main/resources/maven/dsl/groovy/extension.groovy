// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package maven.dsl.groovy

class extension {
  /**
   * The group ID of the extension's artifact.
   */
  private String groupId;

  /**
   * The artifact ID of the extension.
   */
  private String artifactId;

  /**
   * The version of the extension.
   */
  private String version;

  /**
   * The artifact ID of the extension.
   */
  void artifactId(String artifactId) {}

  /**
   * The group ID of the extension's artifact.
   */
  void groupId(String groupId) {}

  /**
   * The version of the extension.
   */
  void version(String version) {}
}
