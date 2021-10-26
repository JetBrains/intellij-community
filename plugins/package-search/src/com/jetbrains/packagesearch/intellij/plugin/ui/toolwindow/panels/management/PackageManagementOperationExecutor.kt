package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management

import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModule
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.OperationExecutor
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.ModuleOperationExecutor
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.PackageSearchOperation
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.PackageSearchOperationFailure
import com.jetbrains.packagesearch.intellij.plugin.util.AppUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import org.apache.commons.collections.CollectionUtils

internal open class PackageManagementOperationExecutor(
    private val coroutineScope: CoroutineScope,
    private val onOperationsSuccessful: (List<ProjectModule>) -> Unit,
    private val onOperationsFail: (FailureType, List<PackageSearchOperationFailure>) -> Unit
) : OperationExecutor {

    private val operationExecutor = ModuleOperationExecutor()

    private suspend fun execute(operations: List<PackageSearchOperation<*>>) {
        val failures = operations.asFlow()
            .mapNotNull { operationExecutor.doOperation(it) }
            .flowOn(Dispatchers.AppUI)
            .toList()

        val successes = operations.map { it.projectModule } difference failures.map { it.operation.projectModule }

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

private inline infix fun <reified E> Collection<E>.difference(other: List<E>) =
    CollectionUtils.removeAll(this, other).filterIsInstance<E>()
