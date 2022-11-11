/*******************************************************************************
 * Copyright 2000-2022 JetBrains s.r.o. and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.jetbrains.packagesearch.intellij.plugin.gradle

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.util.concurrency.AppExecutorUtil
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel

@Service(Service.Level.PROJECT)
internal class PackageSearchGradleLifecycleScope : CoroutineScope, Disposable {

    private val coroutineDispatcher =
        AppExecutorUtil.getAppExecutorService().asCoroutineDispatcher()

    private val supervisor = SupervisorJob()

    override val coroutineContext = SupervisorJob() + CoroutineName(this::class.qualifiedName!!) + coroutineDispatcher

    override fun dispose() {
        supervisor.invokeOnCompletion { coroutineDispatcher.close() }
        cancel("Disposing ${this::class.simpleName}")
    }
}