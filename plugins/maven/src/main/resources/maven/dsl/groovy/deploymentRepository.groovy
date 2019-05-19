// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
