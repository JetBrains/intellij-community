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
package org.jetbrains.idea.maven.project

import org.jetbrains.idea.maven.model.MavenExplicitProfiles
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.model.MavenModel
import org.jetbrains.idea.maven.model.MavenProjectProblem
import org.jetbrains.idea.maven.server.NativeMavenProjectHolder
class MavenProjectReaderResult(@JvmField val mavenModel: MavenModel,
                               @JvmField val dependencyHash: String?,
                               @JvmField val nativeModelMap: Map<String, String?>,
                               @JvmField val activatedProfiles: MavenExplicitProfiles,
                               val nativeMavenProject: NativeMavenProjectHolder?,
                               @JvmField val readingProblems: MutableCollection<MavenProjectProblem>,
                               @JvmField val unresolvedArtifactIds: MutableSet<MavenId>,
                               val unresolvedProblems: Collection<MavenProjectProblem>) {
  constructor(mavenModel: MavenModel,
              nativeModelMap: Map<String, String?>,
              activatedProfiles: MavenExplicitProfiles,
              nativeMavenProject: NativeMavenProjectHolder?,
              readingProblems: MutableCollection<MavenProjectProblem>,
              unresolvedArtifactIds: MutableSet<MavenId>) :
    this(mavenModel,
         null,
         nativeModelMap,
         activatedProfiles,
         nativeMavenProject,
         readingProblems,
         unresolvedArtifactIds,
         emptyList<MavenProjectProblem>())
}
