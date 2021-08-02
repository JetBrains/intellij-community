package com.jetbrains.packagesearch.intellij.plugin.fus

import com.intellij.buildsystem.model.unified.UnifiedDependencyRepository
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModule
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModuleOperationProvider
import com.jetbrains.packagesearch.intellij.plugin.tryDoing
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.TargetModules
import com.jetbrains.packagesearch.intellij.plugin.util.logDebug

private const val FUS_ENABLED = false

// See the documentation at https://confluence.jetbrains.com/display/FUS/IntelliJ+Reporting+API
internal class PackageSearchEventsLogger : CounterUsagesCollector() {

    override fun getGroup() = GROUP

    override fun getVersion() = VERSION

    companion object {

        private const val VERSION = 3
        private val GROUP = EventLogGroup(FUSGroupIds.GROUP_ID, VERSION)

        // FIELDS
        private val buildSystemField = EventFields.Class(FUSGroupIds.MODULE_OPERATION_PROVIDER_CLASS)
        private val repositoryIdField = EventFields.Enum<FUSGroupIds.IndexedRepositories>(FUSGroupIds.REPOSITORY_ID)
        private val repositoryUrlField = EventFields.String(FUSGroupIds.REPOSITORY_URL, FUSGroupIds.indexedRepositoryUrls)
        private val repositoryUsesCustomUrlField = EventFields.Boolean(FUSGroupIds.REPOSITORY_USES_CUSTOM_URL)
        private val packageIsInstalledField = EventFields.Boolean(FUSGroupIds.PACKAGE_IS_INSTALLED)
        private val targetModulesField = EventFields.Enum<FUSGroupIds.TargetModulesType>(FUSGroupIds.TARGET_MODULES)
        private val targetModulesCountField = EventFields.Int(FUSGroupIds.TARGET_MODULES_COUNT)
        private val targetModulesMixedBuildSystemsField = EventFields.Boolean(FUSGroupIds.TARGET_MODULES_MIXED_BUILD_SYSTEMS)

        internal val preferencesGradleScopeCountField = EventFields.Int(FUSGroupIds.PREFERENCES_GRADLE_SCOPES_COUNT)
        internal val preferencesUpdateScopesOnUsageField = EventFields.Boolean(FUSGroupIds.PREFERENCES_UPDATE_SCOPES_ON_USAGE)
        internal val preferencesDefaultGradleScopeChangedField = EventFields.Boolean(FUSGroupIds.PREFERENCES_DEFAULT_GRADLE_SCOPE_CHANGED)
        internal val preferencesDefaultMavenScopeChangedField = EventFields.Boolean(FUSGroupIds.PREFERENCES_DEFAULT_MAVEN_SCOPE_CHANGED)

        private val quickFixTypeField = EventFields.Enum<FUSGroupIds.QuickFixTypes>(FUSGroupIds.QUICK_FIX_TYPE)
        private val quickFixFileTypeField = EventFields.Enum<FUSGroupIds.QuickFixFileTypes>(FUSGroupIds.FILE_TYPE)
        private val detailsLinkLabelField = EventFields.Enum<FUSGroupIds.DetailsLinkTypes>(FUSGroupIds.DETAILS_LINK_LABEL)
        private val toggleTypeField = EventFields.Enum<FUSGroupIds.ToggleTypes>(FUSGroupIds.DETAILS_VISIBLE)
        private val detailsVisibleField = EventFields.Boolean(FUSGroupIds.DETAILS_VISIBLE)
        private val searchRequestLengthField = EventFields.Int(FUSGroupIds.SEARCH_QUERY_LENGTH)

        // EVENTS
        private val packageInstalledEvent = GROUP.registerEvent(FUSGroupIds.PACKAGE_INSTALLED, buildSystemField)
        private val packageRemovedEvent = GROUP.registerEvent(FUSGroupIds.PACKAGE_REMOVED, buildSystemField)
        private val packageUpdatedEvent = GROUP.registerEvent(FUSGroupIds.PACKAGE_UPDATED, buildSystemField)
        private val repositoryAddedEvent = GROUP.registerEvent(FUSGroupIds.REPOSITORY_ADDED, repositoryIdField, repositoryUrlField)
        private val repositoryRemovedEvent = GROUP.registerEvent(
            FUSGroupIds.REPOSITORY_REMOVED,
            repositoryIdField, repositoryUrlField, repositoryUsesCustomUrlField
        )
        private val preferencesChangedEvent = GROUP.registerVarargEvent(FUSGroupIds.PREFERENCES_CHANGED)
        private val preferencesResetEvent = GROUP.registerEvent(FUSGroupIds.PREFERENCES_RESET)
        private val packageSelectedEvent = GROUP.registerEvent(FUSGroupIds.PACKAGE_SELECTED, packageIsInstalledField)
        private val targetModulesSelectedEvent = GROUP.registerEvent(
            FUSGroupIds.TARGET_MODULES_SELECTED,
            targetModulesField, targetModulesCountField, targetModulesMixedBuildSystemsField
        )
        private val runQuickFixEvent = GROUP.registerEvent(FUSGroupIds.RUN_QUICK_FIX, quickFixTypeField, quickFixFileTypeField)
        private val detailsLinkClickEvent = GROUP.registerEvent(FUSGroupIds.DETAILS_LINK_CLICK, detailsLinkLabelField)
        private val toggleDetailsEvent = GROUP.registerEvent(FUSGroupIds.TOGGLE, toggleTypeField, detailsVisibleField)
        private val searchRequestEvent = GROUP.registerEvent(FUSGroupIds.SEARCH_REQUEST, searchRequestLengthField)
        private val searchQueryClearEvent = GROUP.registerEvent(FUSGroupIds.SEARCH_QUERY_CLEAR)
        private val upgradeAllEvent = GROUP.registerEvent(FUSGroupIds.UPGRADE_ALL)

        fun logPackageInstalled(targetModule: ProjectModule) = ifLoggingEnabled {
            val moduleOperationProvider = ProjectModuleOperationProvider.forProjectModuleType(targetModule.moduleType)
            if (moduleOperationProvider != null) {
                packageInstalledEvent.log(moduleOperationProvider::class.java)
            } else {
                logDebug { "Unable to log package installation for target module '${targetModule.name}': no operation provider available" }
            }
        }

        fun logPackageRemoved(targetModule: ProjectModule) = ifLoggingEnabled {
            val moduleOperationProvider = ProjectModuleOperationProvider.forProjectModuleType(targetModule.moduleType)
            if (moduleOperationProvider != null) {
                packageRemovedEvent.log(moduleOperationProvider::class.java)
            } else {
                logDebug { "Unable to log package removal for target module '${targetModule.name}': no operation provider available" }
            }
        }

        fun logPackageUpdated(targetModule: ProjectModule) = ifLoggingEnabled {
            val moduleOperationProvider = ProjectModuleOperationProvider.forProjectModuleType(targetModule.moduleType)
            if (moduleOperationProvider != null) {
                packageUpdatedEvent.log(moduleOperationProvider::class.java)
            } else {
                logDebug { "Unable to log package update for target module '${targetModule.name}': no operation provider available" }
            }
        }

        fun logRepositoryAdded(model: UnifiedDependencyRepository) = ifLoggingEnabled {
            repositoryAddedEvent.log(FUSGroupIds.IndexedRepositories.forId(model.id), FUSGroupIds.IndexedRepositories.validateUrl(model.url))
        }

        fun logRepositoryRemoved(model: UnifiedDependencyRepository) = ifLoggingEnabled {
            val repository = FUSGroupIds.IndexedRepositories.forId(model.id)
            val validatedUrl = FUSGroupIds.IndexedRepositories.validateUrl(model.url)
            val usesCustomUrl = repository != FUSGroupIds.IndexedRepositories.NONE &&
                repository != FUSGroupIds.IndexedRepositories.OTHER &&
                validatedUrl == null
            repositoryRemovedEvent.log(repository, validatedUrl, usesCustomUrl)
        }

        fun logPreferencesChanged(vararg preferences: EventPair<*>) = ifLoggingEnabled {
            preferencesChangedEvent.log(*preferences)
        }

        fun logPreferencesReset() = ifLoggingEnabled {
            preferencesResetEvent.log()
        }

        fun logTargetModuleSelected(targetModules: TargetModules) = ifLoggingEnabled {
            targetModulesSelectedEvent.log(FUSGroupIds.TargetModulesType.from(targetModules), targetModules.size, targetModules.isMixedBuildSystems)
        }

        fun logRunQuickFix(type: FUSGroupIds.QuickFixTypes, fileType: FUSGroupIds.QuickFixFileTypes) = ifLoggingEnabled {
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

        fun logUpgradeAll() = ifLoggingEnabled {
            upgradeAllEvent.log()
        }

        private fun ifLoggingEnabled(action: () -> Unit) {
            if (FUS_ENABLED) tryDoing { action() }
        }
    }
}
