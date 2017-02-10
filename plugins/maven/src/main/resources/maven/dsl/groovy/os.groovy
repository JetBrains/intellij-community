package maven.dsl.groovy

class os {
  /**
   *             The name of the operating system to be used to
   * activate the profile. This must be an exact match
   *             of the <code>${os.name}</code> Java property,
   * such as <code>Windows XP</code>.
   */
  String name

  /**
   *
   *
   *             The general family of the OS to be used to
   * activate the profile, such as
   *             <code>windows</code> or <code>unix</code>.
   *
   *
   */
  String family

  /**
   * The architecture of the operating system to be used to
   * activate the profile.
   */
  String arch

  /**
   * The version of the operating system to be used to activate
   * the profile.
   */
  String version

  /**
   * Set the architecture of the operating system to be used to
   * activate the profile.
   */
  void arch(String arch) {}

  /**
   * Set the general family of the OS to be used to activate the
   * profile, such as <code>windows</code> or <code>unix</code>.
   */
  void family(String family) {}

  /**
   * Set the name of the operating system to be used to activate the profile.
   * This must be an exact match of the <code>${os.name}</code> Java property,
   * such as <code>Windows XP</code>.
   */
  void name(String name) {}

  /**
   * Set the version of the operating system to be used to
   * activate the profile.
   */
  void version(String version) {}
}
