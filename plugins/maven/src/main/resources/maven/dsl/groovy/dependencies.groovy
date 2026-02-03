// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package maven.dsl.groovy

class dependencies {

  /**
   * Add dependency
   */
  def dependency

  /**
   * Add dependency using Map notation, e.g.
   * <pre>
   * dependency (groupId: 'junit', artifactId: 'junit', version: '4.12')
   * </pre>
   * and/or configure it using closure, e.g.
   * <pre>
   * dependency (groupId: 'junit', artifactId: 'junit', version: '4.12'){
   *   exclusions {
   *     exclusion groupId: 'org.hamcrest', artifactId: '*'
   *   }
   * }
   * </pre>
   */
  void dependency(Map attrs = [:], Closure closure) {}

  /**
   * Add dependency using string notation, e.g.
   * <pre>
   * dependency('junit:junit:4.12')
   * </pre>
   */
  void dependency(String dependencyNotation) {}
}
