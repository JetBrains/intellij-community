// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import org.jetbrains.kotlin.idea.ThreadTrackerPatcherForTeamCityTesting.patchThreadTracker
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode

class JvmPluginStartupActivity : StartupActivity {
    override fun runActivity(project: Project) {
        if (isUnitTestMode()) {
            patchThreadTracker()
        }
    }
}
