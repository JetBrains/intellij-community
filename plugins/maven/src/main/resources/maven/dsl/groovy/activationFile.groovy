// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
