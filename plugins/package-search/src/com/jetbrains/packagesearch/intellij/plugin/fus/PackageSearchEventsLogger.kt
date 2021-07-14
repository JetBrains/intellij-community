package com.jetbrains.packagesearch.intellij.plugin.fus

import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.buildsystem.model.unified.UnifiedDependencyRepository
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventId3
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModule
import com.jetbrains.packagesearch.intellij.plugin.tryDoing
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.TargetModules

private const val FUS_ENABLED = false

// See the documentation at https://confluence.jetbrains.com/display/FUS/IntelliJ+Reporting+API
internal class PackageSearchEventsLogger : CounterUsagesCollector() {

    override fun getGroup() = GROUP

    override fun getVersion() = VERSION

    companion object {

        private const val VERSION = 2
        private val GROUP = EventLogGroup(FUSGroupIds.GROUP_ID, VERSION)

        // FIELDS
        private val coordinatesField = EventFields.StringValidatedByCustomRule(FUSGroupIds.COORDINATES, FUSGroupIds.COORDINATES)
        private val scopeField = EventFields.StringValidatedByCustomRule(FUSGroupIds.SCOPE, FUSGroupIds.SCOPE)
        private val buildSystemField = EventFields.StringValidatedByCustomRule(FUSGroupIds.BUILD_SYSTEM, FUSGroupIds.BUILD_SYSTEM)
        private val repositoryIdField = EventFields.StringValidatedByCustomRule(FUSGroupIds.REPOSITORY_ID, FUSGroupIds.REPOSITORY_ID)
        private val repositoryUrlField = EventFields.StringValidatedByCustomRule(FUSGroupIds.REPOSITORY_URL, FUSGroupIds.REPOSITORY_URL)
        private val packageIsInstalledField = EventFields.Boolean(FUSGroupIds.PACKAGE_IS_INSTALLED)
        internal val preferencesGradleScopesField =
            EventFields.StringValidatedByCustomRule(FUSGroupIds.PREFERENCES_GRADLE_SCOPES, FUSGroupIds.PREFERENCES_GRADLE_SCOPES)
        internal val preferencesUpdateScopesOnUsageField =
            EventFields.StringValidatedByCustomRule(FUSGroupIds.PREFERENCES_UPDATE_SCOPES_ON_USAGE, FUSGroupIds.PREFERENCES_UPDATE_SCOPES_ON_USAGE)
        internal val preferencesDefaultGradleScopeField =
            EventFields.StringValidatedByCustomRule(FUSGroupIds.PREFERENCES_DEFAULT_GRADLE_SCOPE, FUSGroupIds.PREFERENCES_DEFAULT_GRADLE_SCOPE)
        internal val preferencesDefaultMavenScopeField =
            EventFields.StringValidatedByCustomRule(FUSGroupIds.PREFERENCES_DEFAULT_MAVEN_SCOPE, FUSGroupIds.PREFERENCES_DEFAULT_MAVEN_SCOPE)
        private val targetModuleNameField = EventFields.String(
            FUSGroupIds.TARGET_MODULE_NAME,
            listOfNotNull(
                TargetModules.None::class.simpleName,
                TargetModules.One::class.simpleName,
                TargetModules.All::class.simpleName,
            )
        )
        private val quickFixTypeField = EventFields.Enum(FUSGroupIds.QUICK_FIX_TYPE, FUSGroupIds.QuickFixTypes::class.java)
        private val quickFixFileTypeField = EventFields.StringValidatedByCustomRule(FUSGroupIds.FILE_TYPE, FUSGroupIds.FILE_TYPE)
        private val detailsLinkLabelField = EventFields.Enum(FUSGroupIds.DETAILS_LINK_LABEL, FUSGroupIds.DetailsLinkTypes::class.java)
        private val detailsLinkUrlField = EventFields.StringValidatedByCustomRule(FUSGroupIds.DETAILS_LINK_URL, FUSGroupIds.DETAILS_LINK_URL)
        private val toggleTypeField = EventFields.Enum(FUSGroupIds.DETAILS_VISIBLE, FUSGroupIds.ToggleTypes::class.java)
        private val valueField = EventFields.Boolean(FUSGroupIds.DETAILS_VISIBLE)
        private val searchRequestField = EventFields.StringValidatedByCustomRule(FUSGroupIds.SEARCH_QUERY, FUSGroupIds.SEARCH_QUERY)

        // EVENTS
        private val packageInstalledEvent = GROUP.registerEvent(FUSGroupIds.PACKAGE_INSTALLED, coordinatesField, scopeField, buildSystemField)
        private val packageRemovedEvent = GROUP.registerEvent(FUSGroupIds.PACKAGE_REMOVED, coordinatesField, scopeField, buildSystemField)
        private val packageUpdatedEvent = GROUP.registerEvent(FUSGroupIds.PACKAGE_UPDATED, coordinatesField, scopeField, buildSystemField)
        private val repositoryAddedEvent = GROUP.registerEvent(FUSGroupIds.REPOSITORY_ADDED, repositoryIdField, repositoryUrlField)
        private val repositoryRemovedEvent = GROUP.registerEvent(FUSGroupIds.REPOSITORY_REMOVED, repositoryIdField, repositoryUrlField)
        private val preferencesChangedEvent = GROUP.registerVarargEvent(FUSGroupIds.PREFERENCES_CHANGED)
        private val preferencesResetEvent = GROUP.registerEvent(FUSGroupIds.PREFERENCES_RESET)
        private val packageSelectedEvent = GROUP.registerEvent(FUSGroupIds.PACKAGE_SELECTED, packageIsInstalledField)
        private val moduleSelectedEvent = GROUP.registerEvent(FUSGroupIds.MODULE_SELECTED, targetModuleNameField)
        private val runQuickFixEvent = GROUP.registerEvent(FUSGroupIds.RUN_QUICK_FIX, quickFixTypeField, quickFixFileTypeField)
        private val detailsLinkClickEvent = GROUP.registerEvent(FUSGroupIds.DETAILS_LINK_CLICK, detailsLinkLabelField, detailsLinkUrlField)
        private val toggleEvent = GROUP.registerEvent(FUSGroupIds.TOGGLE, toggleTypeField, valueField)
        private val searchRequestEvent = GROUP.registerEvent(FUSGroupIds.SEARCH_REQUEST, searchRequestField)
        private val searchQueryClearEvent = GROUP.registerEvent(FUSGroupIds.SEARCH_QUERY_CLEAR)
        private val upgradeAllEvent = GROUP.registerEvent(FUSGroupIds.UPGRADE_ALL)

        private fun EventId3<String?, String?, String?>.logPackage(dependency: UnifiedDependency, targetModule: ProjectModule) {
            tryDoing {
                val coordinates = dependency.coordinates.displayName
                val scope = dependency.scope
                val buildSystem = targetModule.buildSystemType.name
                log(coordinates, scope, buildSystem)
            }
        }

        fun logPackageInstalled(dependency: UnifiedDependency, targetModule: ProjectModule) = ifLoggingEnabled {
            packageInstalledEvent.logPackage(dependency, targetModule)
        }

        fun logPackageRemoved(dependency: UnifiedDependency, targetModule: ProjectModule) = ifLoggingEnabled {
            packageRemovedEvent.logPackage(dependency, targetModule)
        }

        fun logPackageUpdated(dependency: UnifiedDependency, targetModule: ProjectModule) = ifLoggingEnabled {
            packageUpdatedEvent.logPackage(dependency, targetModule)
        }

        fun logRepositoryAdded(model: UnifiedDependencyRepository) = ifLoggingEnabled {
            repositoryAddedEvent.log(model.id, model.url)
        }

        fun logRepositoryRemoved(model: UnifiedDependencyRepository) = ifLoggingEnabled {
            repositoryRemovedEvent.log(model.id, model.url)
        }

        fun logPreferencesChanged(vararg preferences: EventPair<String?>) = ifLoggingEnabled {
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

        fun logDetailsLinkClick(type: FUSGroupIds.DetailsLinkTypes, url: String) = ifLoggingEnabled {
            detailsLinkClickEvent.log(type, url)
        }

        fun logToggle(type: FUSGroupIds.ToggleTypes, state: Boolean) = ifLoggingEnabled {
            toggleEvent.log(type, state)
        }

        fun logSearchRequest(query: String) = ifLoggingEnabled {
            searchRequestEvent.log(query)
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
