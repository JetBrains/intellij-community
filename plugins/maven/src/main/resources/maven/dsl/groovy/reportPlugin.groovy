package maven.dsl.groovy

class reportPlugin {
    /**
   * The group ID of the reporting plugin in the repository.
   */
  String groupId = "org.apache.maven.plugins";

  /**
   * The artifact ID of the reporting plugin in the repository.
   */
  String artifactId;

  /**
   * The version of the reporting plugin to be used.
   */
  String version;

  /**
   * Field reportSets.
   */
  List reportSets;

  /**
   * The artifact ID of the reporting plugin in the
   * repository.
   */
  void artifactId(String artifactId) {}

  /**
   * The group ID of the reporting plugin in the repository.
   */
  void groupId(String groupId) {}

  /**
   * Multiple specifications of a set of reports, each having
   * (possibly) different
   *             configuration. This is the reporting parallel to
   * an <code>execution</code> in the build.
   */
  void reportSets(Closure closure) {}

  /**
   * The version of the reporting plugin to be used.
   */
  void version(String version) {}
}
