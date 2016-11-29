package maven.dsl.groovy

class parent {
  /**
   * The group id of the parent project to inherit from.
   */
  String groupId

  /**
   * The artifact id of the parent project to inherit from.
   */
  String artifactId

  /**
   * The version of the parent project to inherit.
   */
  String version

  /**
   * The relative path of the parent <code>pom.xml</code> file within the check out.
   * If not specified, it defaults to <code>../pom.xml</code>.
   * Maven looks for the parent POM first in this location on the filesystem, then the local repository, and
   * lastly in the remote repo.
   * <code>relativePath</code> allows you to select a different location, for example when your structure is flat, or
   * deeper without an intermediate parent POM.
   * However, the group ID, artifact ID and version are still required, and must match the file in the location given or
   * it will revert to the repository for the POM.
   * This feature is only for enhancing the development in a local checkout of that project.
   * Set the value to an empty string in case you want to disable the feature and always resolve the parent POM from the repositories.
   */
  String relativePath = "../pom.xml"

  /**
   * Set the artifact id of the parent project to inherit from.
   */
  void artifactId(String artifactId) {}

  /**
   * Set the group id of the parent project to inherit from.
   */
  void groupId(String groupId) {}

  /**
   * Set the relative path of the parent <code>pom.xml</code>
   * file within the check out.
   *             If not specified, it defaults to
   * <code>../pom.xml</code>.
   *             Maven looks for the parent POM first in this
   * location on
   *             the filesystem, then the local repository, and
   * lastly in the remote repo.
   *             <code>relativePath</code> allows you to select a
   * different location,
   *             for example when your structure is flat, or
   * deeper without an intermediate parent POM.
   *             However, the group ID, artifact ID and version
   * are still required,
   *             and must match the file in the location given or
   * it will revert to the repository for the POM.
   *             This feature is only for enhancing the
   * development in a local checkout of that project.
   *             Set the value to an empty string in case you
   * want to disable the feature and always resolve
   *             the parent POM from the repositories.
   *
   * @param relativePath
   */
  void relativePath(String relativePath = "") {}

  /**
   * Set the version of the parent project to inherit.
   *
   * @param version
   */
  void version(String version) {}
}
