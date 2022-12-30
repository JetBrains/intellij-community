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

package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management

import com.intellij.openapi.application.writeAction
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModule
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.OperationExecutor
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.ModuleOperationExecutor
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.PackageSearchOperation
import com.jetbrains.packagesearch.intellij.plugin.util.writeAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.launch

internal open class PackageManagementOperationExecutor(
    private val coroutineScope: CoroutineScope,
    private val executionContext: suspend (suspend () -> List<ModuleOperationExecutor.Result>) -> List<ModuleOperationExecutor.Result>,
    private val onOperationsSuccessful: (List<ProjectModule>) -> Unit,
    private val onOperationsFail: (List<ModuleOperationExecutor.Result.Failure>) -> Unit
) : OperationExecutor {

    private suspend fun execute(operations: List<PackageSearchOperation<*>>) {
        val operationExecutor = ModuleOperationExecutor()
        operationExecutor.addOperations(operations)
        val failures = executionContext { operationExecutor.execute() }
            .filterIsInstance<ModuleOperationExecutor.Result.Failure>()
        val successes = operations.map { it.projectModule } - failures.map { it.operation.projectModule }.toSet()

        if (failures.isNotEmpty()) onOperationsFail(failures)
        if (successes.isNotEmpty()) onOperationsSuccessful(successes)
    }

    override fun executeOperations(operations: Deferred<List<PackageSearchOperation<*>>>) {
        coroutineScope.launch { operations.await().takeIf { it.isNotEmpty() }?.let { execute(it) } }
    }

    override fun executeOperations(operations: List<PackageSearchOperation<*>>) {
        if (operations.isEmpty()) {
            return
        }

        coroutineScope.launch { execute(operations) }
    }
}
