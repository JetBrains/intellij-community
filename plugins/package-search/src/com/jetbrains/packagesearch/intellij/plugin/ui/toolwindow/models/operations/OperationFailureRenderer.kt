package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations

import com.intellij.openapi.util.NlsSafe
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import org.jetbrains.annotations.Nls

internal class OperationFailureRenderer {

    @NlsSafe
    fun renderFailuresAsHtmlBulletList(failures: List<PackageSearchOperationFailure>) = buildString {
        append("<html><head></head><body><ul>")
        for (failure in failures) {
            append("<li>")
            append(renderFailure(failure))
            append("</li>")
        }
        append("</ul></body></html>")
    }

    @Nls
    fun renderFailure(failure: PackageSearchOperationFailure): String =
        when (failure.exception) {
            is OperationException.UnsupportedBuildSystem -> PackageSearchBundle.message(
                "packagesearch.operation.error.message.unsupportedBuildSystem",
                failure.operation.projectModule.name,
                renderVerbFor(failure.operation),
                renderObjectNameFor(failure.operation),
                failure.operation.projectModule.buildSystemType.name
            )
            is OperationException.InvalidPackage -> PackageSearchBundle.message(
                "packagesearch.operation.error.message.invalidPackage",
                failure.operation.projectModule.name,
                renderVerbFor(failure.operation),
                failure.exception.dependency.displayName
            )
        }

    @Nls
    private fun renderVerbFor(operation: PackageSearchOperation<*>) = when (operation) {
        is PackageSearchOperation.Package.Install -> PackageSearchBundle.message("packagesearch.operation.verb.install")
        is PackageSearchOperation.Package.Remove -> PackageSearchBundle.message("packagesearch.operation.verb.remove")
        is PackageSearchOperation.Package.ChangeInstalled -> PackageSearchBundle.message("packagesearch.operation.verb.change")
        is PackageSearchOperation.Repository.Install -> PackageSearchBundle.message("packagesearch.operation.verb.install")
        is PackageSearchOperation.Repository.Remove -> PackageSearchBundle.message("packagesearch.operation.verb.remove")
    }

    @Nls
    private fun renderObjectNameFor(operation: PackageSearchOperation<*>) = when (operation) {
        is PackageSearchOperation.Package -> PackageSearchBundle.message("packagesearch.operation.objectName.dependency")
        is PackageSearchOperation.Repository -> PackageSearchBundle.message("packagesearch.operation.objectName.repository")
    }
}
