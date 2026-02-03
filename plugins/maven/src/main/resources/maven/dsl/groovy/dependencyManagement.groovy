// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package maven.dsl.groovy

class dependencyManagement {

  def dependencies
  /**
   * Dependencies specified here are not used until they
   * are referenced in a
   *             POM within the group. This allows the
   * specification of a "standard" version for a
   *             particular dependency.
   */
  void dependencies(Closure closure) {}
}
