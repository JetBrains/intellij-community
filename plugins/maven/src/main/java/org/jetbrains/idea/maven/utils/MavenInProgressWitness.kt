// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.utils

import com.intellij.platform.backend.observation.MarkupBasedActivityInProgressWitness

internal class MavenInProgressWitness : MarkupBasedActivityInProgressWitness() {
  override val presentableName: String = "maven"
}