package maven.dsl.groovy

class modelBase {

  /**
   * Field modules.
   */
  List<String> modules

  /**
   * Distribution information for a project that enables
   * deployment of the site
   *             and artifacts to remote web servers and
   * repositories respectively.
   */
  def distributionManagement

  /**
   * Field properties.
   */
  Properties properties

  /**
   * Default dependency information for projects that inherit
   * from this one. The
   *             dependencies in this section are not immediately
   * resolved. Instead, when a POM derived
   *             from this one declares a dependency described by
   * a matching groupId and artifactId, the
   *             version and other values from this section are
   * used for that dependency if they were not
   *             already specified.
   */
  def dependencyManagement

  /**
   * Field dependencies.
   */
  List dependencies

  /**
   * Field repositories.
   */
  List repositories

  /**
   * Field pluginRepositories.
   */
  List pluginRepositories

  /**
   *
   *
   *             <b>Deprecated</b>. Now ignored by Maven.
   *
   *
   */
  def reports

  /**
   *
   *
   *             This element includes the specification of
   * report plugins to use
   *             to generate the reports on the Maven-generated
   * site.
   *             These reports will be run when a user executes
   * <code>mvn site</code>.
   *             All of the reports will be included in the
   * navigation bar for browsing.
   *
   *
   */
  def reporting

  /**
   * This element describes all of the dependencies
   * associated with a
   *             project.
   *             These dependencies are used to construct a
   * classpath for your
   *             project during the build process. They are
   * automatically downloaded from the
   *             repositories defined in this project.
   *             See <a
   * href="http://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html">the
   *             dependency mechanism</a> for more information.
   */
  void dependencies(Closure closure) {}

  /**
   * Default dependency information for projects that inherit
   * from this one. The
   *             dependencies in this section are not immediately
   * resolved. Instead, when a POM derived
   *             from this one declares a dependency described by
   * a matching groupId and artifactId, the
   *             version and other values from this section are
   * used for that dependency if they were not
   *             already specified.
   */
  void dependencyManagement(Closure closure) {}

  /**
   * Distribution information for a project that enables
   * deployment of the site
   *             and artifacts to remote web servers and
   * repositories respectively.
   */
  void distributionManagement(Closure closure) {}

  /**
   * Modules (sometimes called subprojects) to build as a
   * part of this
   *             project. Each module listed is a relative path
   * to the directory containing the module.
   *             To be consistent with the way default urls are
   * calculated from parent, it is recommended
   *             to have module names match artifact ids.
   */
  void modules(Closure closure) {}

  /**
   * Modules (sometimes called subprojects) to build as a
   * part of this
   *             project. Each module listed is a relative path
   * to the directory containing the module.
   *             To be consistent with the way default urls are
   * calculated from parent, it is recommended
   *             to have module names match artifact ids.
   */
  void modules(List<String> modules) {}

  /**
   * Modules (sometimes called subprojects) to build as a
   * part of this
   *             project. Each module listed is a relative path
   * to the directory containing the module.
   *             To be consistent with the way default urls are
   * calculated from parent, it is recommended
   *             to have module names match artifact ids.
   */
  void modules(String... modules) {}

  /**
   * The lists of the remote repositories for discovering
   * plugins for builds and reports.
   */
  void pluginRepositories(Closure closure) {}

  /**
   * Set properties that can be used throughout the POM as a
   * substitution, and
   *             are used as filters in resources if enabled.
   *             The format is
   * <code>&ltname&gtvalue&lt/name&gt</code>.
   *
   * @param properties
   */
  void setProperties(Properties properties) {}

  /**
   * Set properties that can be used throughout the POM as a
   * substitution, and
   *             are used as filters in resources if enabled.
   *             The format is
   * <code>&ltname&gtvalue&lt/name&gt</code>.
   *
   * @param properties
   */
  void properties(Closure closure) {}

  /**
   * this element includes the specification of report
   * plugins to use
   *             to generate the reports on the Maven-generated
   * site.
   *             These reports will be run when a user executes
   * <code>mvn site</code>.
   *             All of the reports will be included in the
   * navigation bar for browsing.
   */
  void reporting(Closure closure) {}

  /**
   * Set <b>Deprecated</b>. Now ignored by Maven.
   */
  void reports(Closure closure) {}

  /**
   * Set the lists of the remote repositories for discovering
   * dependencies and
   *             extensions.
   */
  void repositories(Closure closure) {}
}
