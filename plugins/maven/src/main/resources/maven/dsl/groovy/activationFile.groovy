package maven.dsl.groovy

class activationFile {

  /**
   * The name of the file that must be missing to activate the profile.
   */
  String missing

  /**
   * The name of the file that must exist to activate the profile.
   */
  String exists
  /**
   * The name of the file that must exist to activate the profile.
   */
  void exists(String exists) {}

  /**
   * The name of the file that must be missing to activate the profile.
   */
  void missing(String missing) {}
}
