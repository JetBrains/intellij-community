// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package maven.dsl.groovy

class pluginConfiguration {
  /**
   * Default plugin information to be made available for
   * reference by projects derived from this one. This plugin configuration
   * will not be resolved or bound to the lifecycle unless referenced. Any local
   * configuration for a given plugin will override the plugin's entire definition here.
   */
  void pluginManagement(Closure closure) {}
}
