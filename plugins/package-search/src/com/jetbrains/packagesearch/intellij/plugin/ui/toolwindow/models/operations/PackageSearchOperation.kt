package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations

import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.buildsystem.model.unified.UnifiedDependencyRepository
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModule
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageScope
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageVersion

internal sealed class PackageSearchOperation<T> {

    abstract val model: T
    abstract val projectModule: ProjectModule

    sealed class Package : PackageSearchOperation<UnifiedDependency>() {

        override abstract val model: UnifiedDependency

        data class Install(
            override val model: UnifiedDependency,
            override val projectModule: ProjectModule,
            val newVersion: PackageVersion,
            val newScope: PackageScope
        ) : Package() {

            override fun toString() =
                "Package.Install(model='${model.displayName}', projectModule='${projectModule.getFullName()}', " +
                    "version='$newVersion', scope='$newScope')"
        }

        data class Remove(
            override val model: UnifiedDependency,
            override val projectModule: ProjectModule,
            val currentVersion: PackageVersion,
            val currentScope: PackageScope
        ) : Package() {

            override fun toString() =
                "Package.Remove(model='${model.displayName}', projectModule='${projectModule.getFullName()}', " +
                    "currentVersion='$currentVersion', scope='$currentScope')"
        }

        data class ChangeInstalled(
            override val model: UnifiedDependency,
            override val projectModule: ProjectModule,
            val currentVersion: PackageVersion,
            val currentScope: PackageScope,
            val newVersion: PackageVersion,
            val newScope: PackageScope
        ) : Package() {

            override fun toString() =
                "Package.ChangeInstalled(model='${model.displayName}', projectModule='${projectModule.getFullName()}', " +
                    "currentVersion='$currentVersion', currentScope='$currentScope', " +
                    "newVersion='$newVersion', newScope='$newScope')"
        }
    }

    sealed class Repository : PackageSearchOperation<UnifiedDependencyRepository>() {

        override abstract val model: UnifiedDependencyRepository

        data class Install(
            override val model: UnifiedDependencyRepository,
            override val projectModule: ProjectModule
        ) : Repository() {

            override fun toString() = "Repository.Install(model='${model.displayName}', projectModule='${projectModule.getFullName()}')"
        }

        data class Remove(
            override val model: UnifiedDependencyRepository,
            override val projectModule: ProjectModule
        ) : Repository() {

            override fun toString() = "Repository.Remove(model='${model.displayName}', projectModule='${projectModule.getFullName()}')"
        }
    }
}
