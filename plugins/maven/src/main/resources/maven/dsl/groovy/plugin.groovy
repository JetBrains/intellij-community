// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package maven.dsl.groovy

class plugin {
  /**
   * The group ID of the plugin in the repository.
   */
  String groupId = "org.apache.maven.plugins";

  /**
   * The artifact ID of the plugin in the repository.
   */
  String artifactId;

  /**
   * The version (or valid range of versions) of the plugin to be used.
   */
  String version;

  /**
   * Whether to load Maven extensions (such as packaging and type handlers) from this plugin.
   * For performance reasons, this should only be enabled when necessary. Note: While the type of this field is <code>String</code> for
   * technical reasons, the semantic type is actually <code>Boolean</code>.
   * Default value is <code>false</code>.
   */
  String extensions;

  /**
   * <b>Deprecated</b>. Unused by Maven.
   */
  Object goals

  /**
   * <b>Deprecated</b>. Unused by Maven.
   */
  void goals(Object goals) {}

  /**
   * Set the artifact ID of the plugin in the repository.
   */
  void artifactId(String artifactId) {}

  /**
   * Set additional dependencies that this project needs to
   * introduce to the plugin's classloader.
   */
  void dependencies(Closure closure) {}

  /**
   * Set multiple specifications of a set of goals to execute
   * during the build lifecycle, each having (possibly) a different configuration.
   */
  void executions(Closure closure) {}

  /**
   * Set whether to load Maven extensions (such as packaging and
   * type handlers) from this plugin. For performance reasons, this
   * should only be enabled when necessary. Note: While the type of this field is <code>String</code> for
   * technical reasons, the semantic type is actually <code>Boolean</code>.
   * Default value is <code>false</code>.
   */
  void extensions(String extensions) {}

  /**
   * Set the group ID of the plugin in the repository.
   */
  void groupId(String groupId) {}

  /**
   * Set the version (or valid range of versions) of the plugin to be used.
   */
  void version(String version) {}

  void extensions(boolean extensions) {}
}
