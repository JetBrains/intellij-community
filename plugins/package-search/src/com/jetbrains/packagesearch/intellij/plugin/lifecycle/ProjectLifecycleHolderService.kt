@file:Suppress("EXPERIMENTAL_IS_NOT_ENABLED")

package com.jetbrains.packagesearch.intellij.plugin.lifecycle

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

internal class ProjectLifecycleHolderService(private val project: Project) : CoroutineScope, Disposable {

    override val coroutineContext = SupervisorJob() + CoroutineName("ProjectLifecycleScopeService")

    override fun dispose() {
        cancel("Disposing ProjectLifecycleScopeService")
    }

}
