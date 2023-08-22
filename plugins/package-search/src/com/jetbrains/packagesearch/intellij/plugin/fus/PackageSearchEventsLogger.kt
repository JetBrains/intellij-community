/*******************************************************************************
 * Copyright 2000-2022 JetBrains s.r.o. and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.jetbrains.packagesearch.intellij.plugin.fus

import com.intellij.buildsystem.model.unified.UnifiedDependencyRepository
import com.intellij.externalSystem.ExternalDependencyModificator
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.BaseEventId
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments
import com.intellij.openapi.diagnostic.thisLogger
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.extensibility.PackageSearchModule
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageIdentifier
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageVersion
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.TargetModules
import com.jetbrains.packagesearch.intellij.plugin.util.logDebug
import org.jetbrains.idea.packagesearch.SortMetric
import org.jetbrains.idea.reposearch.statistics.TopPackageIdValidationRule

private const val FUS_ENABLED = true

class PackageSearchEventsLogger : CounterUsagesCollector() {

    override fun getGroup() = GROUP

    companion object {

        private const val VERSION = 12
        private val GROUP = EventLogGroup(FUSGroupIds.GROUP_ID, VERSION)

        // FIELDS
        private val buildSystemField = EventFields.Class(FUSGroupIds.MODULE_OPERATION_PROVIDER_CLASS)
        private val packageIdField = EventFields.StringValidatedByCustomRule(FUSGroupIds.PACKAGE_ID, TopPackageIdValidationRule::class.java)
        private val packageVersionField = EventFields.StringValidatedByRegexp(FUSGroupIds.PACKAGE_VERSION, regexpRef = "version")
        private val packageFromVersionField = EventFields.StringValidatedByRegexp(FUSGroupIds.PACKAGE_FROM_VERSION, regexpRef = "version")
        private val repositoryIdField = EventFields.Enum<FUSGroupIds.IndexedRepositories>(FUSGroupIds.REPOSITORY_ID)
        private val repositoryUrlField = EventFields.String(FUSGroupIds.REPOSITORY_URL, allowedValues = FUSGroupIds.indexedRepositoryUrls)
        private val repositoryUsesCustomUrlField = EventFields.Boolean(FUSGroupIds.REPOSITORY_USES_CUSTOM_URL)
        private val packageIsInstalledField = EventFields.Boolean(FUSGroupIds.PACKAGE_IS_INSTALLED)
        private val targetModulesField = EventFields.Enum<FUSGroupIds.TargetModulesType>(FUSGroupIds.TARGET_MODULES)
        private val targetModulesMixedBuildSystemsField = EventFields.Boolean(FUSGroupIds.TARGET_MODULES_MIXED_BUILD_SYSTEMS)

        val preferencesGradleScopeCountField = EventFields.Int(FUSGroupIds.PREFERENCES_GRADLE_SCOPES_COUNT)
        val preferencesUpdateScopesOnUsageField = EventFields.Boolean(FUSGroupIds.PREFERENCES_UPDATE_SCOPES_ON_USAGE)
        val preferencesDefaultGradleScopeChangedField = EventFields.Boolean(FUSGroupIds.PREFERENCES_DEFAULT_GRADLE_SCOPE_CHANGED)
        val preferencesDefaultMavenScopeChangedField = EventFields.Boolean(FUSGroupIds.PREFERENCES_DEFAULT_MAVEN_SCOPE_CHANGED)
        internal val preferencesAutoAddRepositoriesField = EventFields.Boolean(FUSGroupIds.PREFERENCES_AUTO_ADD_REPOSITORIES)

        private val detailsLinkLabelField = EventFields.Enum<FUSGroupIds.DetailsLinkTypes>(FUSGroupIds.DETAILS_LINK_LABEL)
        private val toggleTypeField = EventFields.Enum<FUSGroupIds.ToggleTypes>(FUSGroupIds.CHECKBOX_NAME)
        private val toggleValueField = EventFields.Boolean(FUSGroupIds.CHECKBOX_STATE)
        private val searchQueryLengthField = EventFields.Int(FUSGroupIds.SEARCH_QUERY_LENGTH)
        private val sortMetricField = EventFields.Enum<SortMetric>(FUSGroupIds.SORT_METRIC)

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
            eventField2 = toggleValueField,
        )
        private val searchRequestEvent = GROUP.registerEvent(
            eventId = FUSGroupIds.SEARCH_REQUEST,
            eventField1 = searchQueryLengthField
        )
        private val sortMetricEvent = GROUP.registerEvent(
            eventId = FUSGroupIds.SORT_METRIC_CHANGED,
            eventField1 = sortMetricField,
        )
        private val searchQueryClearEvent = GROUP.registerEvent(FUSGroupIds.SEARCH_QUERY_CLEAR)
        private val upgradeAllEvent = GROUP.registerEvent(FUSGroupIds.UPGRADE_ALL)

        fun logPackageInstalled(
            packageIdentifier: PackageIdentifier,
            packageVersion: PackageVersion,
            targetModule: PackageSearchModule
        ) = runSafelyIfEnabled(packageInstalledEvent) {
            val moduleOperationProvider = targetModule.getModificator()
            if (moduleOperationProvider != null) {
                log(packageIdentifier.rawValue, packageVersion.versionName, moduleOperationProvider::class.java)
            } else {
                logDebug { "Unable to log package installation for target module '${targetModule.name}': no operation provider available" }
            }
        }

        fun logPackageRemoved(
            packageIdentifier: PackageIdentifier,
            packageVersion: PackageVersion,
            targetModule: PackageSearchModule
        ) = runSafelyIfEnabled(packageRemovedEvent) {
            val moduleOperationProvider = targetModule.getModificator()
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
            targetModule: PackageSearchModule
        ) = runSafelyIfEnabled(packageUpdatedEvent) {
            val moduleOperationProvider = targetModule.getModificator()
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

        fun logSortMetric(metric: SortMetric) = runSafelyIfEnabled(sortMetricEvent) {
            log(metric)
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

private fun PackageSearchModule.getModificator() =
    ExternalDependencyModificator.EP_NAME.getExtensions(nativeModule.project)
        .find { it.supports(nativeModule) }
