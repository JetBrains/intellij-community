/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.execution

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenUtil

class MavenConfigurationProducer : LazyRunConfigurationProducer<MavenRunConfiguration>() {

  override fun getConfigurationFactory(): ConfigurationFactory {
    return MavenRunConfigurationType.getInstance().configurationFactories[0]
  }

  override fun setupConfigurationFromContext(configuration: MavenRunConfiguration,
                                             context: ConfigurationContext,
                                             sourceElement: Ref<PsiElement>): Boolean {
    val location = context.location ?: return false
    val file = location.virtualFile ?: return false
    if (!MavenUtil.isPomFile(location.project, file)) return false
    if (location !is MavenGoalLocation) return false
    if (context.module == null) return false
    val goals = location.goals
    val profiles = MavenProjectsManager.getInstance(location.getProject()).explicitProfiles
    configuration.name = context.module.name + goals.joinToString(separator = ",", prefix = "[", postfix = "]")

    configuration.runnerParameters = MavenRunnerParameters(true, file.parent.path, file.name, goals, profiles.enabledProfiles,
                                                           profiles.disabledProfiles)
    return true
  }

  override fun isConfigurationFromContext(configuration: MavenRunConfiguration,
                                          context: ConfigurationContext): Boolean {
    val location = context.location ?: return false
    val file = location.virtualFile ?: return false
    if (!MavenUtil.isPomFile(location.project, file)) return false
    if (location !is MavenGoalLocation) return false
    if (context.module == null) return false

    val tasks: List<String> = location.goals
    val taskNames: List<String> = configuration.runnerParameters.goals
    if (tasks.isEmpty() && taskNames.isEmpty()) {
      return true
    }

    return tasks.containsAll(taskNames) && !taskNames.isEmpty()
  }
}