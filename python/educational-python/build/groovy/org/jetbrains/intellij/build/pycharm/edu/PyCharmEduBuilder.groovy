package org.jetbrains.intellij.build.pycharm.edu

import org.codehaus.gant.GantBinding
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildTasks
import org.jetbrains.intellij.build.CommunityRepositoryModules
import org.jetbrains.intellij.build.JetBrainsBuildTools

/**
 * @author nik
 */
class PyCharmEduBuilder {
  private final GantBinding binding
  private final String home

  PyCharmEduBuilder(String home, GantBinding binding) {
    this.home = home
    this.binding = binding
  }

  def build() {
    def buildContext = BuildContext.createContext(binding.ant, binding.projectBuilder, binding.project, binding.global,
                                                  "$home/community", home, "$home/out/pycharmEDU", new PyCharmEduProperties(home),
                                                  JetBrainsBuildTools.create("$home/build/lib/jet-sign.jar"))
    def buildTasks = BuildTasks.create(buildContext)
    buildTasks.compileModulesAndBuildDistributions()
  }
}