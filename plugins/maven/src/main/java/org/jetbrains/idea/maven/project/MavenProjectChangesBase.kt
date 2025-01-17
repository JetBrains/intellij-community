// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface MavenProjectChangesBase {
  @ApiStatus.Internal
  object ALL : MavenProjectChangesBase {
    override fun hasChanges(): Boolean {
      return true
    }
  }

  @ApiStatus.Internal
  object NONE : MavenProjectChangesBase {
    override fun hasChanges(): Boolean {
      return false
    }
  }

  fun hasChanges(): Boolean
}
