// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.pycharm


import groovy.transform.CompileStatic

/**
 * @author Aleksey.Rostovskiy
 */
@CompileStatic
final class PyCharmBuildOptions {
  /** Build pydevd package step name */
  static final String PYDEVD_PACKAGE = "pydevd_package"
}
