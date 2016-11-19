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

import org.codehaus.gant.GantBinding
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildTasks
import org.jetbrains.intellij.build.JetBrainsBuildTools

/**
 * @author nik
 */
class PyCharmBuilder {
  private final GantBinding binding
  private final String home

  PyCharmBuilder(String home, GantBinding binding) {
    this.home = home
    this.binding = binding
  }

  def build() {
    def buildContext = BuildContext.createContext(binding.ant, binding.projectBuilder, binding.project, binding.global,
                                                  "$home/community", home, new PyCharmProperties(home),
                                                  JetBrainsBuildTools.create("$home/build/lib/jet-sign.jar"))
    def buildTasks = BuildTasks.create(buildContext)
    buildTasks.buildDistributions()
    buildContext.messages.block("Build Community Edition") {
      buildCommunityEdition(buildContext)
    }
  }

  private void buildCommunityEdition(BuildContext originalContext) {
    def buildContext = originalContext.createCopyForProduct(new PyCharmCommunityProperties("$home/community"),
                                                            "$home/community")
    def buildTasks = BuildTasks.create(buildContext)
    buildTasks.buildDistributions()
  }
}