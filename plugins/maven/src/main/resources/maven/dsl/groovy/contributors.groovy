// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package maven.dsl.groovy

class contributors {

  def contributor
  /**
   * Add contributor
   */
  void contributor(Map attrs = [:], Closure closure) {}

  /**
   * Add contributor
   */
  void contributor(Map attrs) {}
}
