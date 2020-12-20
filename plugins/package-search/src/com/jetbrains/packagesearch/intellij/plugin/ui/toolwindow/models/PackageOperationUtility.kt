package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

class PackageOperationUtility(private val viewModel: PackageSearchToolWindowModel) {

    fun getApplyOperation(items: List<PackageOperationTarget>, targetVersion: String): PackageOperation? {
        if (items.isEmpty()) {
            return null
        }

        val applyOperations = items.mapNotNull {
            PackageOperation.resolve(
                it.packageSearchDependency.identifier,
                it.version,
                targetVersion
            )
        }
        if (applyOperations.isEmpty()) {
            return null
        }
        val firstOperation = applyOperations.first()
        if (applyOperations.all { it.title == firstOperation.title }) {
            return firstOperation
        }
        return null
    }

    fun getRemoveOperation(items: List<PackageOperationTarget>): PackageOperation? {
        if (items.isEmpty()) {
            return null
        }
        if (items.all { it.version.isNotEmpty() }) {
            // All projects should have any installed version
            return items.first().getRemoveOperation()
        }
        return null
    }

    fun doOperation(op: PackageOperation?, items: List<PackageOperationTarget>, targetVersion: String) {
        if (op == null || items.isEmpty()) return

        viewModel.executeOperations(items.map {
            ExecutablePackageOperation(op, it, targetVersion)
        })
    }
}
