package maven.dsl.groovy

class deploymentRepository {

  /**
   * Whether to assign snapshots a unique version comprised of
   * the timestamp and
   *             build number, or to use the same version each
   * time.
   */
  boolean uniqueVersion = true

  /**
   * Set whether to assign snapshots a unique version comprised
   * of the timestamp and
   *             build number, or to use the same version each
   * time.
   */
  void uniqueVersion(boolean uniqueVersion) {}
}
