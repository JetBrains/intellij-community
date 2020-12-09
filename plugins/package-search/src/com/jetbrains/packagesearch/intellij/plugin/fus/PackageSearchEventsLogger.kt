package com.jetbrains.packagesearch.intellij.plugin.fus

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.intellij.plugin.api.model.StandardV2Package
import com.jetbrains.packagesearch.intellij.plugin.extensibility.BuildSystemType
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModule
import com.jetbrains.packagesearch.intellij.plugin.tryDoing

// See the documentation at https://confluence.jetbrains.com/display/FUS/IntelliJ+Reporting+API
object PackageSearchEventsLogger {

    private val loggerProvider by lazy { PackageSearchEventsLoggerProviderFactory.create() }

    fun onToolWindowOpen(project: Project) = tryDoing {
        logDialogEvent(project = project, event = "open", version = "1")
    }

    fun onToolWindowClose(project: Project) = tryDoing {
        logDialogEvent(project = project, event = "close", version = "1", extras = *arrayOf("ok" to true))
    }

    // TODO no longer in use - deprecate in FUS whitelist?
    fun onSearchDialogCancel(project: Project) = tryDoing {
        logDialogEvent(project = project, event = "close-cancel", version = "1", extras = *arrayOf("ok" to false))
    }

    fun onSearchRequest(project: Project, query: String) = tryDoing {
        logDialogEvent(project = project, event = "request", version = "1", extras = *queryStats(query))
    }

    fun onProjectInfo(
        project: Project,
        ideaModules: Array<Module>,
        modules: List<ProjectModule>
    ) = tryDoing {
        val countPerBuildSystem: Array<Pair<String, Int>> = modules
            .groupBy { it.buildSystemType.statisticsKey }
            .map { it.key to it.value.size }
            .toTypedArray()

        logDialogEvent(
            project = project,
            event = "project-info",
            version = "1",
            extras = *arrayOf("ij" to ideaModules.size, *countPerBuildSystem)
        )
    }

    fun onSearchResponse(project: Project, query: String, items: List<StandardV2Package>) = tryDoing {
        val matchItems = "match-items" to items.size
        val matchGroups = "match-groups" to items.distinctBy { it.groupId.toLowerCase() }.size

        logDialogEvent(
            project = project,
            event = "response",
            version = "1",
            extras = *arrayOf(*queryStats(query), matchItems, matchGroups)
        )
    }

    fun onSearchFailed(project: Project, query: String) = tryDoing {
        logDialogEvent(
            project = project,
            event = "response-failed",
            version = "1",
            extras = *queryStats(query)
        )
    }

    fun onGetRepositoriesFailed(project: Project) = tryDoing {
        logDialogEvent(
            project = project,
            event = "response-failed",
            version = "1"
        )
    }

    private fun queryStats(query: String): Array<Pair<String, Any>> {
        val tokens = query.split(Regex("[\\s\\r\\n]+"))
        val wordsCount = tokens.size
        val querySize = query.length
        // TODO: include unique search request UUID

        return arrayOf(
            "query-tokens" to wordsCount,
            "query-size" to querySize
        )
    }

    fun onPackageInstallHit(
        project: Project,
        buildSystem: BuildSystemType,
        dependency: StandardV2Package?,
        items: List<StandardV2Package>
    ) = tryDoing {
        logDialogEvent(
            project = project,
            event = "installed",
            version = "1",
            extras = *arrayOf(
                "build-system" to buildSystem.statisticsKey,
                "hit-min-order" to (items.withIndex().firstOrNull { it.value === dependency }?.index ?: -1),
                "match-groups" to items.distinctBy { it.groupId.toLowerCase() }.size,
                "match-items" to items.size
            )
        )
    }

    private fun logDialogEvent(
        project: Project,
        event: String,
        @Suppress("SameParameterValue") version: String,
        vararg extras: Pair<String, Any>
    ) = tryDoing {
        loggerProvider.logEvent(
            project = project,
            groupId = FUSGroupIds.FUS_DIALOG_GROUP_ID,
            event = event,
            version = version,
            extras = extras
        )
    }
}
