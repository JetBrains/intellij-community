package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management

import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModule
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.OperationExecutor
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.ModuleOperationExecutor
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.PackageSearchOperation
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.PackageSearchOperationFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.launch

internal open class PackageManagementOperationExecutor(
    private val coroutineScope: CoroutineScope,
    private val onOperationsSuccessful: (List<ProjectModule>) -> Unit,
    private val onOperationsFail: (FailureType, List<PackageSearchOperationFailure>) -> Unit
) : OperationExecutor {

    private val operationExecutor = ModuleOperationExecutor()

    private suspend fun execute(operations: List<PackageSearchOperation<*>>) {
        val failures = operations.distinct().mapNotNull { operationExecutor.doOperation(it) }

        val successes = operations.map { it.projectModule } - failures.map { it.operation.projectModule }.toSet()

        if (failures.size == operations.size) {
            onOperationsSuccessful(successes)
            onOperationsFail(FailureType.SOME, failures)
        } else if (failures.isNotEmpty()) {
            onOperationsFail(FailureType.ALL, failures)
        } else onOperationsSuccessful(successes)
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

    enum class FailureType {
        SOME,
        ALL
    }
}
