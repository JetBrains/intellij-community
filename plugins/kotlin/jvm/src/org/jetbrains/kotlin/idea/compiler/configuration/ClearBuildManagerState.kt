// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.compiler.configuration

import com.intellij.compiler.server.BuildManager
import com.intellij.openapi.project.Project

class ClearBuildManagerState : ClearBuildStateExtension() {
    override fun clearState(project: Project) {
        BuildManager.getInstance().clearState(project);
    }
}