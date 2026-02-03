/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.project

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.WorkingDirectoryProvider
import org.jetbrains.annotations.SystemIndependent

class MavenWorkingDirectoryProvider : WorkingDirectoryProvider {
  override fun getWorkingDirectoryPath(module: Module): @SystemIndependent String? {
    return ReadAction.compute<String, RuntimeException> {
      if (module.isDisposed) return@compute null
      val manager = MavenProjectsManager.getInstance(module.project)
      if (!manager.isMavenizedModule(module)) return@compute null
      return@compute manager.findProject(module)?.directory
    }
  }
}