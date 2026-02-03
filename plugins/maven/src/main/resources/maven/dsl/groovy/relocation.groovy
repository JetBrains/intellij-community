// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package maven.dsl.groovy

class relocation {

  /**
   * The group ID the artifact has moved to.
   */
  String groupId

  /**
   * The new artifact ID of the artifact.
   */
  String artifactId

  /**
   * The new version of the artifact.
   */
  String version

  /**
   * An additional message to show the user about the move, such
   * as the reason.
   */
  String message
  /**
   * Set the new artifact ID of the artifact.
   */
  void artifactId(String artifactId) {}

  /**
   * Set the group ID the artifact has moved to.
   */
  void groupId(String groupId) {}

  /**
   * Set an additional message to show the user about the move,
   * such as the reason.
   */
  void message(String message) {}

  /**
   * Set the new version of the artifact.
   */
  void version(String version) {}
}
