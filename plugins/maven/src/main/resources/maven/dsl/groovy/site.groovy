package maven.dsl.groovy

class site {
  String id

  /**
   * Human readable name of the deployment location.
   */
  String name

  /**
   *
   *
   *             The url of the location where website is
   * deployed, in the form <code>protocol://hostname/path</code>.
   *             <br /><b>Default value is</b>: parent value [+
   * path adjustment] + artifactId
   *
   *           .
   */
  String url
  /**
   * Set a unique identifier for a deployment location. This is
   * used to match the
   *             site to configuration in the
   * <code>settings.xml</code> file, for example.
   */
  void id(String id) {}

  /**
   * Set human readable name of the deployment location.
   */
  void name(String name) {}

  /**
   * Set the url of the location where website is deployed, in
   * the form <code>protocol://hostname/path</code>.
   *             <br /><b>Default value is</b>: parent value [+
   * path adjustment] + artifactId.
   */
  void url(String url) {}
}
