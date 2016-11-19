/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package groovy.org.jetbrains.intellij.build.pycharm

import groovy.io.FileType
import org.apache.tools.ant.util.StringUtils
import org.jetbrains.intellij.build.BuildContext

/**
 * @author nik
 */
class DebuggerEggBuilder {
  private final AntBuilder ant
  private final String tempPath
  private final String projectHome
  private final String fullBuildNumber

  DebuggerEggBuilder(BuildContext buildContext) {
    ant = buildContext.ant
    tempPath = buildContext.paths.temp
    projectHome = buildContext.paths.projectHome
    fullBuildNumber = buildContext.fullBuildNumber
  }

  static DebuggerEggBuilder create(AntBuilder ant, String tempPath, String projectHome, String fullBuildNumber) {
    return new DebuggerEggBuilder(ant, tempPath, projectHome, fullBuildNumber)
  }

  DebuggerEggBuilder(AntBuilder ant, String tempPath, String projectHome, String fullBuildNumber) {
    this.ant = ant
    this.tempPath = tempPath
    this.projectHome = projectHome
    this.fullBuildNumber = fullBuildNumber
  }

  void buildDebuggerEggs(String targetDirectory) {
    buildEgg("pycharm-debug", targetDirectory, [])

    buildEgg("pycharm-debug-py3k", targetDirectory, [
      "jyimportsTipper.py",
      "pydevconsole_code_for_ironpython.py",
      "pydevd_exec.py"
    ])
  }

  private void buildEgg(String eggName, String targetDirectory, List<String> additionalFilesToExclude) {
    def eggDir = "$tempPath/debug-eggs/$eggName"

    ant.delete(dir: eggDir)
    ant.mkdir(dir: eggDir)

    ant.copy(todir: "$eggDir") {
      fileset(dir: "$projectHome/community/python/helpers/pydev") {
        include(name: "**/*.py")
        exclude(name: "**/setup.py")
        additionalFilesToExclude.each {
          exclude(name: "**/$it")
        }
      }
    }

    ant.copy(todir: "$eggDir/EGG-INFO") {
      fileset(dir: "$projectHome/python/resources/debugger-egg/EGG-INFO")
    }
    ant.replace(file: "$eggDir/EGG-INFO/PKG-INFO") {
      replacefilter(token: "@@BUILD_NUMBER@@", value: fullBuildNumber)
    }
    ant.replace(file: "$eggDir/_pydevd_bundle/pydevd_comm.py") {
      replacefilter(token: "@@BUILD_NUMBER@@", value: fullBuildNumber)
    }

    ant.echo(file: "$eggDir/EGG-INFO/top_level.txt", message: listTopLevelModules(eggDir).join(StringUtils.LINE_SEP))

    ant.zip(destfile: "$targetDirectory/${eggName}.egg") {
      fileset(dir: eggDir)
    }
  }

  private static List<String> listTopLevelModules(String root) {
    def list = []
    def filesToIgnore = ["setup.py", "setup_cython.py", "runfiles.py"]
    new File(root).eachFile(FileType.FILES) { file ->
      if (file.name.endsWith(".py") && !(file.name in filesToIgnore)) {
        list << file.name - ".py"
      }
    }
    return list
  }
}