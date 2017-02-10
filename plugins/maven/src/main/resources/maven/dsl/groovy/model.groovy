package maven.dsl.groovy

class model {

  /**
   * Declares to which version of project descriptor this POM
   * conforms.
   */
  String modelVersion

  /**
   * The location of the parent project, if one exists. Values
   * from the parent
   *             project will be the default for this project if
   * they are left unspecified. The location
   *             is given as a group ID, artifact ID and version.
   */
  def parent

  /**
   *
   *
   *             A universally unique identifier for a project.
   * It is normal to
   *             use a fully-qualified package name to
   * distinguish it from other
   *             projects with a similar name (eg.
   * <code>org.apache.maven</code>).
   *
   *
   */
  String groupId

  /**
   * The identifier for this artifact that is unique within the
   * group given by the
   *             group ID. An artifact is something that is
   * either produced or used by a project.
   *             Examples of artifacts produced by Maven for a
   * project include: JARs, source and binary
   *             distributions, and WARs.
   */
  String artifactId

  /**
   * The current version of the artifact produced by this project.
   */
  String version

  /**
   *
   *
   *             The type of artifact this project produces, for
   * example <code>jar</code>
   *               <code>war</code>
   *               <code>ear</code>
   *               <code>pom</code>.
   *             Plugins can create their own packaging, and
   *             therefore their own packaging types,
   *             so this list does not contain all possible
   * types.
   *
   *
   */
  String packaging = "jar"

  /**
   * The full name of the project.
   */
  String name

  /**
   * A detailed description of the project, used by Maven
   * whenever it needs to
   *             describe the project, such as on the web site.
   * While this element can be specified as
   *             CDATA to enable the use of HTML tags within the
   * description, it is discouraged to allow
   *             plain text representation. If you need to modify
   * the index page of the generated web
   *             site, you are able to specify your own instead
   * of adjusting this text.
   */
  String description

  /**
   *
   *
   *             The URL to the project's homepage.
   *             <br /><b>Default value is</b>: parent value [+
   * path adjustment] + artifactId
   *
   *           .
   */
  String url

  /**
   * The year of the project's inception, specified with 4
   * digits. This value is
   *             used when generating copyright notices as well
   * as being informational.
   */
  String inceptionYear

  /**
   * This element describes various attributes of the
   * organization to which the
   *             project belongs. These attributes are utilized
   * when documentation is created (for
   *             copyright notices and links).
   */
  def organization

  /**
   * Field licenses.
   */
  List licenses

  /**
   * Field developers.
   */
  List developers

  /**
   * Field contributors.
   */
  List contributors

  /**
   * Field mailingLists.
   */
  List mailingLists

  /**
   * Describes the prerequisites in the build environment for
   * this project.
   */
  def prerequisites

  /**
   * Specification for the SCM used by the project, such as CVS,
   * Subversion, etc.
   */
  def scm

  /**
   * The project's issue management system information.
   */
  def issueManagement

  /**
   * The project's continuous integration information.
   */
  def ciManagement

  /**
   * Information required to build the project.
   */
  def build

  /**
   * Field profiles.
   */
  List profiles

  /**
   * Field modelEncoding.
   */
  String modelEncoding = "UTF-8"

  /**
   * Set the identifier for this artifact that is unique within
   * the group given by the
   *             group ID. An artifact is something that is
   * either produced or used by a project.
   *             Examples of artifacts produced by Maven for a
   * project include: JARs, source and binary
   *             distributions, and WARs.
   */
  void artifactId(String artifactId) {}

  /**
   * Set information required to build the project.
   */
  void build(Closure closure) {}

  /**
   * Set the project's continuous integration information.
   */
  void ciManagement(Closure closure) {}

  /**
   * Set describes the contributors to a project that are not yet
   * committers.
   */
  void contributors(Closure closure) {}

  /**
   * Set a detailed description of the project, used by Maven
   * whenever it needs to
   *             describe the project, such as on the web site.
   * While this element can be specified as
   *             CDATA to enable the use of HTML tags within the
   * description, it is discouraged to allow
   *             plain text representation. If you need to modify
   * the index page of the generated web
   *             site, you are able to specify your own instead
   * of adjusting this text.
   */
  void description(String description) {}

  /**
   * Set describes the committers of a project.
   */
  void developers(Closure closure) {}

  /**
   * Set a universally unique identifier for a project. It is
   * normal to
   *             use a fully-qualified package name to
   * distinguish it from other
   *             projects with a similar name (eg.
   * <code>org.apache.maven</code>).
   */
  void groupId(String groupId) {}

  /**
   * Set the year of the project's inception, specified with 4
   * digits. This value is
   *             used when generating copyright notices as well
   * as being informational.
   */
  void inceptionYear(String inceptionYear) {}

  /**
   * Set the project's issue management system information.
   */
  void issueManagement(Closure closure) {}

  /**
   * Set this element describes all of the licenses for this
   * project.
   *             Each license is described by a
   * <code>license</code> element, which
   *             is then described by additional elements.
   *             Projects should only list the license(s) that
   * applies to the project
   *             and not the licenses that apply to dependencies.
   *             If multiple licenses are listed, it is assumed
   * that the user can select
   *             any of them, not that they must accept all.
   */
  void licenses(Closure closure) {}

  /**
   * Set contains information about a project's mailing lists.
   */
  void mailingLists(Closure closure) {}

  /**
   * Set the modelEncoding field.
   */
  void modelEncoding(String modelEncoding) {}

  /**
   * Set declares to which version of project descriptor this POM
   * conforms.
   */
  void modelVersion(String modelVersion) {}

  /**
   * Set the full name of the project.
   */
  void name(String name) {}

  /**
   * Set this element describes various attributes of the
   * organization to which the
   *             project belongs. These attributes are utilized
   * when documentation is created (for
   *             copyright notices and links).
   */
  void organization(Closure closure) {}

  /**
   * Set the type of artifact this project produces, for example
   * <code>jar</code>
   *               <code>war</code>
   *               <code>ear</code>
   *               <code>pom</code>.
   *             Plugins can create their own packaging, and
   *             therefore their own packaging types,
   *             so this list does not contain all possible
   * types.
   */
  void packaging(String packaging) {}

  /**
   * Set the location of the parent project, if one exists.
   * Values from the parent
   *             project will be the default for this project if
   * they are left unspecified. The location
   *             is given as a group ID, artifact ID and version.
   */
  void parent(Map attrs = [:], Closure closure) {}

  /**
   * Set describes the prerequisites in the build environment for
   * this project.
   */
  void prerequisites(Closure closure) {}

  /**
   * Set a listing of project-local build profiles which will
   * modify the build process
   *             when activated.
   */
  void profiles(Closure closure) {}

  /**
   * Set specification for the SCM used by the project, such as
   * CVS, Subversion, etc.
   */
  void scm(Closure closure) {}

  /**
   * Set the URL to the project's homepage.
   *             <br /><b>Default value is</b>: parent value [+
   * path adjustment] + artifactId.
   */
  void url(String url) {}

  /**
   * Set the current version of the artifact produced by this
   * project.
   */
  void version(String version) {}
}
