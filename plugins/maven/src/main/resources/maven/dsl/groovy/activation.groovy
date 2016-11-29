package maven.dsl.groovy

class activation {
  /**
   * If set to true, this profile will be active unless another
   * profile in this
   *             pom is activated using the command line -P
   * option or by one of that profile's
   *             activators.
   */
  boolean activeByDefault = false

  /**
   *
   *
   *             Specifies that this profile will be activated
   * when a matching JDK is detected.
   *             For example, <code>1.4</code> only activates on
   * JDKs versioned 1.4,
   *             while <code>!1.4</code> matches any JDK that is
   * not version 1.4. Ranges are supported too:
   *             <code>[1.5,)</code> activates when the JDK is
   * 1.5 minimum.
   *
   *
   */
  String jdk

  /**
   * Specifies that this profile will be activated when matching
   * operating system
   *             attributes are detected.
   */
  def os

  /**
   * Specifies that this profile will be activated when this
   * system property is
   *             specified.
   */
  def property

  /**
   * Specifies that this profile will be activated based on
   * existence of a file.
   */
  def file

  /**
   * Set if set to true, this profile will be active unless
   * another profile in this
   *             pom is activated using the command line -P
   * option or by one of that profile's
   *             activators.
   */
  void activeByDefault(boolean activeByDefault) {}

  /**
   * Set specifies that this profile will be activated based on
   * existence of a file.
   */
  void file(Closure closure) {}

  /**
   * Set specifies that this profile will be activated when a
   * matching JDK is detected.
   *             For example, <code>1.4</code> only activates on
   * JDKs versioned 1.4,
   *             while <code>!1.4</code> matches any JDK that is
   * not version 1.4. Ranges are supported too:
   *             <code>[1.5,)</code> activates when the JDK is
   * 1.5 minimum.
   */
  void jdk(String jdk) {}

  /**
   * Set specifies that this profile will be activated when
   * matching operating system
   *             attributes are detected.
   */
  void os(Closure closure) {}

  /**
   * Set specifies that this profile will be activated when this
   * system property is
   *             specified.
   */
  void property(Closure closure) {}
}
