@file:Suppress("EXPERIMENTAL_IS_NOT_ENABLED")

package com.jetbrains.packagesearch.intellij.plugin.lifecycle

import com.intellij.openapi.Disposable
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

internal class ProjectLifecycleHolderService : CoroutineScope, Disposable {

    override val coroutineContext = SupervisorJob() + CoroutineName(this::class.qualifiedName!!)

    override fun dispose() = cancel("Disposing ${this::class.qualifiedName}")
}
