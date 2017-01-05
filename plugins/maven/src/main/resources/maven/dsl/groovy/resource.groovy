package maven.dsl.groovy

class resource {
  /**
   * Field includes.
   */
  List<String> includes;

  /**
   * Field excludes.
   */
  List<String> excludes;

  /**
   * List of patterns to exclude, e.g.
   * <code>**&#47;*.xml</code>
   */
  void excludes(String... excludes) {}

  /**
   * List of patterns to exclude, e.g.
   * <code>**&#47;*.xml</code>
   */
  void excludes(List<String> excludes) {}

  /**
   * List of patterns to include, e.g.
   * <code>**&#47;*.xml</code>.
   */
  void includes(List<String> includes) {}

  /**
   * List of patterns to include, e.g.
   * <code>**&#47;*.xml</code>.
   */
  void includes(String... includes) {}

  /**
   * Describe the directory where the resources are stored. The
   * path is relative
   *             to the POM.
   */
  String directory;

  /**
   * Describe the directory where the resources are stored.
   * The path is relative
   *             to the POM.
   */
  void directory(String directory) {}

  /**
   *
   *
   *             Describe the resource target path. The path is
   * relative to the target/classes
   *             directory (i.e.
   * <code>${project.build.outputDirectory}</code>).
   *             For example, if you want that resource to appear
   * in a specific package
   *             (<code>org.apache.maven.messages</code>), you
   * must specify this
   *             element with this value:
   * <code>org/apache/maven/messages</code>.
   *             This is not required if you simply put the
   * resources in that directory
   *             structure at the source, however.
   *
   *
   */
  String targetPath;

  /**
   *
   *
   *             Whether resources are filtered to replace tokens
   * with parameterised values or not.
   *             The values are taken from the
   * <code>properties</code> element and from the
   *             properties in the files listed in the
   * <code>filters</code> element. Note: While the type
   *             of this field is <code>String</code> for
   * technical reasons, the semantic type is actually
   *             <code>Boolean</code>. Default value is
   * <code>false</code>.
   *
   *
   */
  String filtering;

  /**
   * Whether resources are filtered to replace tokens with
   * parameterised values or not.
   *             The values are taken from the
   * <code>properties</code> element and from the
   *             properties in the files listed in the
   * <code>filters</code> element. Note: While the type
   *             of this field is <code>String</code> for
   * technical reasons, the semantic type is actually
   *             <code>Boolean</code>. Default value is
   * <code>false</code>.
   */
  void filtering(String filtering) {}

  /**
   * Whether resources are filtered to replace tokens with
   * parameterised values or not.
   *             The values are taken from the
   * <code>properties</code> element and from the
   *             properties in the files listed in the
   * <code>filters</code> element. Note: While the type
   *             of this field is <code>String</code> for
   * technical reasons, the semantic type is actually
   *             <code>Boolean</code>. Default value is
   * <code>false</code>.
   */
  void filtering(boolean filtering) {}

  /**
   * Describe the resource target path. The path is relative
   * to the target/classes
   *             directory (i.e.
   * <code>${project.build.outputDirectory}</code>).
   *             For example, if you want that resource to appear
   * in a specific package
   *             (<code>org.apache.maven.messages</code>), you
   * must specify this
   *             element with this value:
   * <code>org/apache/maven/messages</code>.
   *             This is not required if you simply put the
   * resources in that directory
   *             structure at the source, however.
   */
  void targetPath(String targetPath) {}
}
