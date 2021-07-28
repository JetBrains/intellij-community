package com.jetbrains.packagesearch.intellij.plugin.fus

import com.intellij.buildsystem.model.unified.UnifiedDependencyRepository
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModule
import com.jetbrains.packagesearch.intellij.plugin.tryDoing

private const val FUS_ENABLED = false

// See the documentation at https://confluence.jetbrains.com/display/FUS/IntelliJ+Reporting+API
internal class PackageSearchEventsLogger : CounterUsagesCollector() {

    override fun getGroup() = GROUP

    override fun getVersion() = VERSION

    companion object {

        private const val VERSION = 3
        private val GROUP = EventLogGroup(FUSGroupIds.GROUP_ID, VERSION)

        // FIELDS
        private val buildSystemField = EventFields.StringValidatedByCustomRule(FUSGroupIds.BUILD_SYSTEM, FUSGroupIds.BUILD_SYSTEM)
        private val repositoryIdField = EventFields.StringValidatedByCustomRule(FUSGroupIds.REPOSITORY_ID, FUSGroupIds.REPOSITORY_ID)
        private val repositoryUrlField = EventFields.StringValidatedByCustomRule(FUSGroupIds.REPOSITORY_URL, FUSGroupIds.REPOSITORY_URL)
        private val packageIsInstalledField = EventFields.Boolean(FUSGroupIds.PACKAGE_IS_INSTALLED)
        internal val preferencesGradleScopeCountField = EventFields.Int(FUSGroupIds.PREFERENCES_GRADLE_SCOPES_COUNT)
        internal val preferencesUpdateScopesOnUsageField = EventFields.Boolean(FUSGroupIds.PREFERENCES_UPDATE_SCOPES_ON_USAGE)
        internal val preferencesDefaultGradleScopeChangedField = EventFields.Boolean(FUSGroupIds.PREFERENCES_DEFAULT_GRADLE_SCOPE_CHANGED)
        internal val preferencesDefaultMavenScopeChangedField = EventFields.Boolean(FUSGroupIds.PREFERENCES_DEFAULT_MAVEN_SCOPE_CHANGED)

        private val quickFixTypeField = EventFields.Enum(FUSGroupIds.QUICK_FIX_TYPE, FUSGroupIds.QuickFixTypes::class.java)
        private val quickFixFileTypeField = EventFields.StringValidatedByCustomRule(FUSGroupIds.FILE_TYPE, FUSGroupIds.FILE_TYPE)
        private val detailsLinkLabelField = EventFields.Enum(FUSGroupIds.DETAILS_LINK_LABEL, FUSGroupIds.DetailsLinkTypes::class.java)
        private val toggleTypeField = EventFields.Enum(FUSGroupIds.DETAILS_VISIBLE, FUSGroupIds.ToggleTypes::class.java)
        private val detailsVisibleField = EventFields.Boolean(FUSGroupIds.DETAILS_VISIBLE)
        private val searchRequestLengthField = EventFields.Int(FUSGroupIds.SEARCH_QUERY_LENGTH)

        // EVENTS
        private val packageInstalledEvent = GROUP.registerEvent(FUSGroupIds.PACKAGE_INSTALLED, buildSystemField)
        private val packageRemovedEvent = GROUP.registerEvent(FUSGroupIds.PACKAGE_REMOVED, buildSystemField)
        private val packageUpdatedEvent = GROUP.registerEvent(FUSGroupIds.PACKAGE_UPDATED, buildSystemField)
        private val repositoryAddedEvent = GROUP.registerEvent(FUSGroupIds.REPOSITORY_ADDED, repositoryIdField, repositoryUrlField)
        private val repositoryRemovedEvent = GROUP.registerEvent(FUSGroupIds.REPOSITORY_REMOVED, repositoryIdField, repositoryUrlField)
        private val preferencesChangedEvent = GROUP.registerVarargEvent(FUSGroupIds.PREFERENCES_CHANGED)
        private val preferencesResetEvent = GROUP.registerEvent(FUSGroupIds.PREFERENCES_RESET)
        private val packageSelectedEvent = GROUP.registerEvent(FUSGroupIds.PACKAGE_SELECTED, packageIsInstalledField)
        private val moduleSelectedEvent = GROUP.registerEvent(FUSGroupIds.MODULE_SELECTED, buildSystemField)
        private val runQuickFixEvent = GROUP.registerEvent(FUSGroupIds.RUN_QUICK_FIX, quickFixTypeField, quickFixFileTypeField)
        private val detailsLinkClickEvent = GROUP.registerEvent(FUSGroupIds.DETAILS_LINK_CLICK, detailsLinkLabelField)
        private val toggleDetailsEvent = GROUP.registerEvent(FUSGroupIds.TOGGLE, toggleTypeField, detailsVisibleField)
        private val searchRequestEvent = GROUP.registerEvent(FUSGroupIds.SEARCH_REQUEST, searchRequestLengthField)
        private val searchQueryClearEvent = GROUP.registerEvent(FUSGroupIds.SEARCH_QUERY_CLEAR)
        private val upgradeAllEvent = GROUP.registerEvent(FUSGroupIds.UPGRADE_ALL)

        fun logPackageInstalled(targetModule: ProjectModule) = ifLoggingEnabled {
            packageInstalledEvent.log(targetModule.buildSystemType.statisticsKey)
        }

        fun logPackageRemoved(targetModule: ProjectModule) = ifLoggingEnabled {
            packageRemovedEvent.log(targetModule.buildSystemType.statisticsKey)
        }

        fun logPackageUpdated(targetModule: ProjectModule) = ifLoggingEnabled {
            packageUpdatedEvent.log(targetModule.buildSystemType.statisticsKey)
        }

        fun logRepositoryAdded(model: UnifiedDependencyRepository) = ifLoggingEnabled {
            repositoryAddedEvent.log(model.id, model.url)
        }

        fun logRepositoryRemoved(model: UnifiedDependencyRepository) = ifLoggingEnabled {
            repositoryRemovedEvent.log(model.id, model.url)
        }

        fun logPreferencesChanged(vararg preferences: EventPair<*>) = ifLoggingEnabled {
            preferencesChangedEvent.log(*preferences)
        }

        fun logPreferencesReset() = ifLoggingEnabled {
            preferencesResetEvent.log()
        }

        fun logModuleSelected(targetModuleName: String?) = ifLoggingEnabled {
            moduleSelectedEvent.log(targetModuleName)
        }

        fun logRunQuickFix(type: FUSGroupIds.QuickFixTypes, fileType: String?) = ifLoggingEnabled {
            runQuickFixEvent.log(type, fileType)
        }

        fun logPackageSelected(isInstalled: Boolean) = ifLoggingEnabled {
            packageSelectedEvent.log(isInstalled)
        }

        fun logDetailsLinkClick(type: FUSGroupIds.DetailsLinkTypes) = ifLoggingEnabled {
            detailsLinkClickEvent.log(type)
        }

        fun logToggle(type: FUSGroupIds.ToggleTypes, state: Boolean) = ifLoggingEnabled {
            toggleDetailsEvent.log(type, state)
        }

        fun logSearchRequest(query: String) = ifLoggingEnabled {
            searchRequestEvent.log(query.length)
        }

        fun logSearchQueryClear() = ifLoggingEnabled {
            searchQueryClearEvent.log()
        }

        fun logUpgradeAll() {
            upgradeAllEvent.log()
        }

        private fun ifLoggingEnabled(action: () -> Unit) {
            if (FUS_ENABLED) tryDoing { action() }
        }
    }
}
