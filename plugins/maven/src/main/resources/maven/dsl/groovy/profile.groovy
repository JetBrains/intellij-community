// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package maven.dsl.groovy

class profile {
  /**
   * The identifier of this build profile. This is used for
   * command line
   *             activation, and identifies profiles to be
   * merged.
   *
   */
  String id = "default";

  /**
   * The conditional logic which will automatically trigger the
   * inclusion of this profile.
   */
  def activation;

  /**
   * Information required to build the project.
   */
  def build;

  /**
   * The conditional logic which will automatically trigger
   * the inclusion of this
   *             profile.
   */
  void activation(Closure closure) {}

  /**
   * Information required to build the project.
   */
  void build(Closure closure) {}

  /**
   * The identifier of this build profile. This is used for
   * command line
   *             activation, and identifies profiles to be
   * merged.
   */
  void id(String id) {}
}
