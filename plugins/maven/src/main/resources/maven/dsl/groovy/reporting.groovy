package maven.dsl.groovy

class reporting {

  /**
   *             If true, then the default reports are not
   * included in the site generation.
   *             This includes the reports in the "Project Info"
   * menu. Note: While the type
   *             of this field is <code>String</code> for
   * technical reasons, the semantic type is actually
   *             <code>Boolean</code>. Default value is
   * <code>false</code>.
   *
   *
   */
  String excludeDefaults;

  /**
   *             Where to store all of the generated reports. The
   * default is
   *             <code>${project.build.directory}/site</code>.
   *
   *
   */
  String outputDirectory;

  /**
   * Field plugins.
   */
  List plugins;

  /**
   * Set if true, then the default reports are not included in
   * the site generation.
   *             This includes the reports in the "Project Info"
   * menu. Note: While the type
   *             of this field is <code>String</code> for
   * technical reasons, the semantic type is actually
   *             <code>Boolean</code>. Default value is
   * <code>false</code>.
   */
  void excludeDefaults(String excludeDefaults) {}
  /**
   * Set if true, then the default reports are not included in
   * the site generation.
   *             This includes the reports in the "Project Info"
   * menu. Note: While the type
   *             of this field is <code>String</code> for
   * technical reasons, the semantic type is actually
   *             <code>Boolean</code>. Default value is
   * <code>false</code>.
   */
  void excludeDefaults(boolean excludeDefaults) {}

  /**
   * Where to store all of the generated reports. The default
   * is
   *             <code>${project.build.directory}/site</code>.
   */
  void outputDirectory(String outputDirectory) {}

  /**
   * The reporting plugins to use and their configuration.
   */
  void plugins(Closure closure) {}
}
