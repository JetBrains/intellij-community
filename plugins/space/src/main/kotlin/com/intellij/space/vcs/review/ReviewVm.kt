// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review

import circlet.client.api.ProjectKey
import com.intellij.openapi.project.Project
import com.intellij.space.components.space
import libraries.coroutines.extra.Lifetime
import libraries.coroutines.extra.Lifetimed
import runtime.reactive.Property
import runtime.reactive.map

internal class ReviewVm(override val lifetime: Lifetime,
               project: Project,
               projectKey: ProjectKey) : Lifetimed {

  val isLoggedIn: Property<Boolean> = map(space.workspace) {
    it != null
  }
}
