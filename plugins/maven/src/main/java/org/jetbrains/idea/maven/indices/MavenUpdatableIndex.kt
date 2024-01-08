// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.indices

import org.jetbrains.idea.maven.server.AddArtifactResponse
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException
import org.jetbrains.idea.maven.utils.MavenProgressIndicator
import java.io.File

interface MavenUpdatableIndex : MavenRepositoryIndex {
  @Throws(MavenProcessCanceledException::class)
  fun updateOrRepair(fullUpdate: Boolean, progress: MavenProgressIndicator, explicit: Boolean)

  fun tryAddArtifacts(artifactFiles: Collection<File>): List<AddArtifactResponse>
}
