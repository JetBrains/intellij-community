package com.jetbrains.packagesearch.intellij.plugin.fus

import com.intellij.buildsystem.model.unified.UnifiedDependencyRepository
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.BaseEventId
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments
import com.intellij.openapi.diagnostic.thisLogger
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModule
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModuleOperationProvider
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageIdentifier
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageVersion
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.TargetModules
import com.jetbrains.packagesearch.intellij.plugin.util.logDebug

private const val FUS_ENABLED = true

internal class PackageSearchEventsLogger : CounterUsagesCollector() {

    override fun getGroup() = GROUP

    override fun getVersion() = VERSION

    companion object {

        private const val VERSION = 10
        private val GROUP = EventLogGroup(FUSGroupIds.GROUP_ID, VERSION)

        // FIELDS
        private val buildSystemField = EventFields.Class(FUSGroupIds.MODULE_OPERATION_PROVIDER_CLASS)
        private val packageIdField = EventFields.StringValidatedByCustomRule(FUSGroupIds.PACKAGE_ID, customRuleId = FUSGroupIds.RULE_TOP_PACKAGE_ID)
        private val packageVersionField = EventFields.StringValidatedByRegexp(FUSGroupIds.PACKAGE_VERSION, regexpRef = "version")
        private val packageFromVersionField = EventFields.StringValidatedByRegexp(FUSGroupIds.PACKAGE_FROM_VERSION, regexpRef = "version")
        private val repositoryIdField = EventFields.Enum<FUSGroupIds.IndexedRepositories>(FUSGroupIds.REPOSITORY_ID)
        private val repositoryUrlField = EventFields.String(FUSGroupIds.REPOSITORY_URL, allowedValues = FUSGroupIds.indexedRepositoryUrls)
        private val repositoryUsesCustomUrlField = EventFields.Boolean(FUSGroupIds.REPOSITORY_USES_CUSTOM_URL)
        private val packageIsInstalledField = EventFields.Boolean(FUSGroupIds.PACKAGE_IS_INSTALLED)
        private val targetModulesField = EventFields.Enum<FUSGroupIds.TargetModulesType>(FUSGroupIds.TARGET_MODULES)
        private val targetModulesMixedBuildSystemsField = EventFields.Boolean(FUSGroupIds.TARGET_MODULES_MIXED_BUILD_SYSTEMS)

        internal val preferencesGradleScopeCountField = EventFields.Int(FUSGroupIds.PREFERENCES_GRADLE_SCOPES_COUNT)
        internal val preferencesUpdateScopesOnUsageField = EventFields.Boolean(FUSGroupIds.PREFERENCES_UPDATE_SCOPES_ON_USAGE)
        internal val preferencesDefaultGradleScopeChangedField = EventFields.Boolean(FUSGroupIds.PREFERENCES_DEFAULT_GRADLE_SCOPE_CHANGED)
        internal val preferencesDefaultMavenScopeChangedField = EventFields.Boolean(FUSGroupIds.PREFERENCES_DEFAULT_MAVEN_SCOPE_CHANGED)
        internal val preferencesAutoAddRepositoriesField = EventFields.Boolean(FUSGroupIds.PREFERENCES_AUTO_ADD_REPOSITORIES)

        private val detailsLinkLabelField = EventFields.Enum<FUSGroupIds.DetailsLinkTypes>(FUSGroupIds.DETAILS_LINK_LABEL)
        private val toggleTypeField = EventFields.Enum<FUSGroupIds.ToggleTypes>(FUSGroupIds.DETAILS_VISIBLE)
        private val detailsVisibleField = EventFields.Boolean(FUSGroupIds.DETAILS_VISIBLE)
        private val searchQueryLengthField = EventFields.Int(FUSGroupIds.SEARCH_QUERY_LENGTH)

        // EVENTS
        private val packageInstalledEvent = GROUP.registerEvent(
            eventId = FUSGroupIds.PACKAGE_INSTALLED,
            eventField1 = packageIdField,
            eventField2 = packageVersionField,
            eventField3 = buildSystemField
        )
        private val packageRemovedEvent = GROUP.registerEvent(
            eventId = FUSGroupIds.PACKAGE_REMOVED,
            eventField1 = packageIdField,
            eventField2 = packageVersionField,
            eventField3 = buildSystemField
        )
        private val packageUpdatedEvent = GROUP.registerVarargEvent(
            eventId = FUSGroupIds.PACKAGE_UPDATED,
            packageIdField, packageFromVersionField, packageVersionField, buildSystemField
        )
        private val repositoryAddedEvent = GROUP.registerEvent(
            eventId = FUSGroupIds.REPOSITORY_ADDED,
            eventField1 = repositoryIdField,
            eventField2 = repositoryUrlField
        )
        private val repositoryRemovedEvent = GROUP.registerEvent(
            eventId = FUSGroupIds.REPOSITORY_REMOVED,
            eventField1 = repositoryIdField,
            eventField2 = repositoryUrlField,
            eventField3 = repositoryUsesCustomUrlField
        )
        private val preferencesChangedEvent = GROUP.registerVarargEvent(
            eventId = FUSGroupIds.PREFERENCES_CHANGED,
            preferencesGradleScopeCountField,
            preferencesUpdateScopesOnUsageField,
            preferencesDefaultGradleScopeChangedField,
            preferencesDefaultMavenScopeChangedField,
            preferencesAutoAddRepositoriesField
        )
        private val preferencesRestoreDefaultsEvent = GROUP.registerEvent(FUSGroupIds.PREFERENCES_RESTORE_DEFAULTS)
        private val packageSelectedEvent = GROUP.registerEvent(eventId = FUSGroupIds.PACKAGE_SELECTED, packageIsInstalledField)
        private val targetModulesSelectedEvent = GROUP.registerEvent(
            eventId = FUSGroupIds.TARGET_MODULES_SELECTED,
            eventField1 = targetModulesField,
            eventField2 = targetModulesMixedBuildSystemsField
        )
        private val detailsLinkClickEvent = GROUP.registerEvent(
            eventId = FUSGroupIds.DETAILS_LINK_CLICK,
            eventField1 = detailsLinkLabelField
        )
        private val toggleDetailsEvent = GROUP.registerEvent(
            eventId = FUSGroupIds.TOGGLE,
            eventField1 = toggleTypeField,
            eventField2 = detailsVisibleField
        )
        private val searchRequestEvent = GROUP.registerEvent(
            eventId = FUSGroupIds.SEARCH_REQUEST,
            eventField1 = searchQueryLengthField
        )
        private val searchQueryClearEvent = GROUP.registerEvent(FUSGroupIds.SEARCH_QUERY_CLEAR)
        private val upgradeAllEvent = GROUP.registerEvent(FUSGroupIds.UPGRADE_ALL)

        fun logPackageInstalled(
            packageIdentifier: PackageIdentifier,
            packageVersion: PackageVersion,
            targetModule: ProjectModule
        ) =
            runSafelyIfEnabled(packageInstalledEvent) {
                val moduleOperationProvider = ProjectModuleOperationProvider.forProjectModuleType(targetModule.moduleType)
                if (moduleOperationProvider != null) {
                    log(packageIdentifier.rawValue, packageVersion.versionName, moduleOperationProvider::class.java)
                } else {
                    logDebug { "Unable to log package installation for target module '${targetModule.name}': no operation provider available" }
                }
            }

        fun logPackageRemoved(
            packageIdentifier: PackageIdentifier,
            packageVersion: PackageVersion,
            targetModule: ProjectModule
        ) = runSafelyIfEnabled(packageRemovedEvent) {
            val moduleOperationProvider = ProjectModuleOperationProvider.forProjectModuleType(targetModule.moduleType)
            if (moduleOperationProvider != null) {
                log(packageIdentifier.rawValue, packageVersion.versionName, moduleOperationProvider::class.java)
            } else {
                logDebug { "Unable to log package removal for target module '${targetModule.name}': no operation provider available" }
            }
        }

        fun logPackageUpdated(
            packageIdentifier: PackageIdentifier,
            packageFromVersion: PackageVersion,
            packageVersion: PackageVersion,
            targetModule: ProjectModule
        ) = runSafelyIfEnabled(packageUpdatedEvent) {
            val moduleOperationProvider = ProjectModuleOperationProvider.forProjectModuleType(targetModule.moduleType)
            if (moduleOperationProvider != null) {
                log(
                    packageIdField.with(packageIdentifier.rawValue),
                    packageFromVersionField.with(packageFromVersion.versionName),
                    packageVersionField.with(packageVersion.versionName),
                    buildSystemField.with(moduleOperationProvider::class.java)
                )
            } else {
                logDebug { "Unable to log package update for target module '${targetModule.name}': no operation provider available" }
            }
        }

        fun logRepositoryAdded(model: UnifiedDependencyRepository) = runSafelyIfEnabled(repositoryAddedEvent) {
            log(FUSGroupIds.IndexedRepositories.forId(model.id), FUSGroupIds.IndexedRepositories.validateUrl(model.url))
        }

        fun logRepositoryRemoved(model: UnifiedDependencyRepository) = runSafelyIfEnabled(repositoryRemovedEvent) {
            val repository = FUSGroupIds.IndexedRepositories.forId(model.id)
            val validatedUrl = FUSGroupIds.IndexedRepositories.validateUrl(model.url)
            val usesCustomUrl = repository != FUSGroupIds.IndexedRepositories.NONE &&
                repository != FUSGroupIds.IndexedRepositories.OTHER &&
                validatedUrl == null
            log(repository, validatedUrl, usesCustomUrl)
        }

        fun logPreferencesChanged(vararg preferences: EventPair<*>) = runSafelyIfEnabled(preferencesChangedEvent) {
            log(*preferences)
        }

        fun logPreferencesRestoreDefaults() = runSafelyIfEnabled(preferencesRestoreDefaultsEvent) {
            log()
        }

        fun logTargetModuleSelected(targetModules: TargetModules) = runSafelyIfEnabled(targetModulesSelectedEvent) {
            log(FUSGroupIds.TargetModulesType.from(targetModules), targetModules.isMixedBuildSystems)
        }

        fun logPackageSelected(isInstalled: Boolean) = runSafelyIfEnabled(packageSelectedEvent) {
            log(isInstalled)
        }

        fun logDetailsLinkClick(type: FUSGroupIds.DetailsLinkTypes) = runSafelyIfEnabled(detailsLinkClickEvent) {
            log(type)
        }

        fun logToggle(type: FUSGroupIds.ToggleTypes, state: Boolean) = runSafelyIfEnabled(toggleDetailsEvent) {
            log(type, state)
        }

        fun logSearchRequest(query: String) = runSafelyIfEnabled(searchRequestEvent) {
            log(query.length)
        }

        fun logSearchQueryClear() = runSafelyIfEnabled(searchQueryClearEvent) {
            log()
        }

        fun logUpgradeAll() = runSafelyIfEnabled(upgradeAllEvent) {
            log()
        }

        private fun <T : BaseEventId> runSafelyIfEnabled(event: T, action: T.() -> Unit) {
            if (FUS_ENABLED) {
                try {
                    event.action()
                } catch (e: RuntimeException) {
                    thisLogger().error(
                        PackageSearchBundle.message("packagesearch.logging.error", event.eventId),
                        RuntimeExceptionWithAttachments(
                            "Non-critical error while logging analytics event. This doesn't impact plugin functionality.",
                            e
                        )
                    )
                }
            }
        }
    }
}
