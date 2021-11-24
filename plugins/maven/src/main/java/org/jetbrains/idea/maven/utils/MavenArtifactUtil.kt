// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.utils

import org.jetbrains.idea.maven.model.MavenArtifact
import java.nio.file.Files

fun MavenArtifact.resolved(): Boolean {
  return isResolved { f ->
    Files.exists(f.toPath())
  }
}