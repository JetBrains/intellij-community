// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.pycharm

import com.intellij.util.SystemProperties
import groovy.transform.CompileStatic

/**
 * @author Aleksey.Rostovskiy
 */
@CompileStatic
class PyCharmBuildOptions {
  /**
   * Pass 'true' to this system property to skip universal stubs generation and use prebuilt ones.
   */
  static final boolean usePrebuiltStubs = SystemProperties.getBooleanProperty("intellij.build.pycharm.use.prebuilt.stubs", false)

  /**
   * Path to a zip file containing generated universal stubs.
   */
  static final String prebuiltStubsArchive = System.getProperty("intellij.build.pycharm.prebuilt.stubs.archive")
}
