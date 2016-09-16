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
