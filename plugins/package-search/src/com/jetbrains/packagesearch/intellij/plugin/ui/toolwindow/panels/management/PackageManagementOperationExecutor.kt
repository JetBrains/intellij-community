package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management

import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.OperationExecutor
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.ModuleOperationExecutor
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.PackageSearchOperation
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.PackageSearchOperationFailure
import com.jetbrains.packagesearch.intellij.plugin.util.AppUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch

internal open class PackageManagementOperationExecutor(
    private val coroutineScope: CoroutineScope,
    private val onOperationsSuccessful: () -> Unit,
    private val onOperationsFail: (FailureType, List<PackageSearchOperationFailure>) -> Unit
) : OperationExecutor {

    private val operationExecutor = ModuleOperationExecutor()

    override fun executeOperations(operations: List<PackageSearchOperation<*>>) {
        if (operations.isEmpty()) {
            return
        }

        coroutineScope.launch {
            val failures = operations.asFlow()
                .mapNotNull { operationExecutor.doOperation(it) }
                .flowOn(Dispatchers.AppUI)
                .toList()

            if (failures.size == operations.size) {
                onOperationsSuccessful()
                onOperationsFail(FailureType.SOME, failures)
            } else if (failures.isNotEmpty()) {
                onOperationsFail(FailureType.ALL, failures)
            } else onOperationsSuccessful()

        }
    }

    enum class FailureType {
        SOME,
        ALL
    }
}
