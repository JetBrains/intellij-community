// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.pycharm

import com.intellij.util.SystemProperties
import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.BuildContext

/**
 * @author Aleksey.Rostovskiy
 */
@CompileStatic
class PyCharmBuildOptions {
  /**
   * Build step of generating indices and stubs. Pass it to {@link org.jetbrains.intellij.build.BuildOptions#buildStepsToSkip} to skip
   */
  static final String GENERATE_INDICES_AND_STUBS_STEP = 'pycharm_indices_and_stubs_step'

  /**
   * Pass 'true' to this system property to skip universal stubs generation and use prebuilt ones.
   */
  static final boolean usePrebuiltStubs = SystemProperties.getBooleanProperty("intellij.build.pycharm.use.prebuilt.stubs", false)

  /**
   * Pass 'false' to skip bundling <a href="http://google.comhttps://plugins.jetbrains.com/plugin/12174-datalore">Datalore plugin</a>
   * to PyCharm Professional
   */
  static final boolean bundleDatalorePlugin = SystemProperties.getBooleanProperty("intellij.build.pycharm.bundle.datalore.plugin", true)

  /**
   * Path to a zip file containing generated universal stubs.
   */
  static final String prebuiltStubsArchive = System.getProperty("intellij.build.pycharm.prebuilt.stubs.archive")

  static File getFolderForIndicesAndStubs(BuildContext context) {
    return new File(context.paths.buildOutputRoot, "index")
  }

  static File getTemporaryFolderForUnzip(BuildContext context) {
    return new File(context.paths.temp, "unzips")
  }

  /**
   * Build PyCharm with Anaconda plugin distributions together with regular PyCharm
   */
  static final String buildWithAnacondaEdition = SystemProperties.getBooleanProperty("intellij.build.pycharm.anaconda.edition", false)

  /**
   * Miniconda installer folder name
   * The same folder should be specified at com.jetbrains.python.PythonMinicondaLocator#getMinicondaInstallerFolder()
   * */
  static final String minicondaInstallerFolderName = "minicondaInstaller"
}
