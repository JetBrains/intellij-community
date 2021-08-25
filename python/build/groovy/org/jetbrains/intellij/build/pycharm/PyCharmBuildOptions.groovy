// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.pycharm

import com.intellij.util.SystemProperties
import groovy.transform.CompileStatic

/**
 * @author Aleksey.Rostovskiy
 */
@CompileStatic
final class PyCharmBuildOptions {
  /** Build pydevd package step name */
  static final String PYDEVD_PACKAGE = "pydevd_package"

  /**
   * Pass 'false' to skip bundling <a href="http://google.comhttps://plugins.jetbrains.com/plugin/12174-datalore">Datalore plugin</a>
   * to PyCharm Professional
   */
  static final boolean bundleDatalorePlugin = SystemProperties.getBooleanProperty("intellij.build.pycharm.bundle.datalore.plugin", true)

  static final boolean buildSharedIndexes = SystemProperties.getBooleanProperty("intellij.build.pycharm.shared.indexes", true)
}
