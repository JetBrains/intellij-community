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

package com.jetbrains.packagesearch.intellij.plugin.extensibility

import com.intellij.ide.actions.cache.AsyncRecoveryResult
import com.intellij.ide.actions.cache.RecoveryAction
import com.intellij.ide.actions.cache.RecoveryScope
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.util.lifecycleScope
import com.jetbrains.packagesearch.intellij.plugin.util.packageSearchApplicationCaches
import com.jetbrains.packagesearch.intellij.plugin.util.packageSearchProjectCachesService
import kotlinx.coroutines.future.future

class DeletePackageSearchProjectCachesAction : RecoveryAction {

    override val performanceRate: Int
        get() = 2000
    override val presentableName: String
        get() = PackageSearchBundle.message("packagesearch.configuration.recovery.caches.global")
    override val actionKey: String
        get() = "pkgs-delete-project-caches"

    override fun perform(recoveryScope: RecoveryScope) =
        recoveryScope.project.lifecycleScope.future {
            packageSearchApplicationCaches.clear()
            AsyncRecoveryResult(recoveryScope, emptyList())
        }
}

class DeletePackageSearchCachesAction : RecoveryAction {

    override val performanceRate: Int
        get() = 4000
    override val presentableName: String
        get() = PackageSearchBundle.message("packagesearch.configuration.recovery.caches")
    override val actionKey: String
        get() = "pkgs-delete-app-caches"

    override fun perform(recoveryScope: RecoveryScope) =
        recoveryScope.project.lifecycleScope.future {
            recoveryScope.project.packageSearchProjectCachesService.clear()
            AsyncRecoveryResult(recoveryScope, emptyList())
        }

}
