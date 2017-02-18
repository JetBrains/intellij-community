/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
