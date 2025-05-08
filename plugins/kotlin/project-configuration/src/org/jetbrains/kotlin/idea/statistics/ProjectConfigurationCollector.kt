// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.statistics

import com.intellij.facet.ProjectFacetManager
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.facet.isKpmModule
import org.jetbrains.kotlin.idea.base.facet.isMultiPlatformModule
import org.jetbrains.kotlin.idea.base.facet.isNewMultiPlatformModule
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinIdePlugin
import org.jetbrains.kotlin.idea.configuration.BuildSystemType
import org.jetbrains.kotlin.idea.configuration.buildSystemType
import org.jetbrains.kotlin.idea.configuration.getNonDefaultLanguageFeatures
import org.jetbrains.kotlin.idea.configuration.getPlatform
import org.jetbrains.kotlin.idea.facet.KotlinFacet
import org.jetbrains.kotlin.idea.facet.KotlinFacetType
import org.jetbrains.kotlin.konan.target.KonanTarget
import java.util.*

internal class ProjectConfigurationCollector : ProjectUsagesCollector() {
    override fun getGroup(): EventLogGroup = GROUP

    override fun getMetrics(project: Project): Set<MetricEvent> {
        val metrics = mutableSetOf<MetricEvent>()
        val modulesLanguageDataInfo = runReadAction {
            ProjectFacetManager.getInstance(project).getModulesWithFacet(KotlinFacetType.TYPE_ID).map {
                ProgressManager.checkCanceled()

                val buildSystem = getBuildSystemType(it)
                val platform = getPlatform(it)
                val facetSettings = KotlinFacet.get(it)?.configuration?.settings
                val languageLevel = facetSettings?.languageLevel?.versionString
                val nonDefaultLanguageFeatures = getNonDefaultLanguageFeatures(it).toList()
                val mppBuild = facetSettings.isMultiPlatformModule || facetSettings.isNewMultiPlatformModule
                val kpmModule = it.isKpmModule

                Data(buildSystem, platform, languageLevel, nonDefaultLanguageFeatures, mppBuild, kpmModule)
            }
        }

        modulesLanguageDataInfo.forEach {
            metrics.add(
                buildEvent.metric(
                    systemField.with(it.buildSystem),
                    platformField.with(it.platform),
                    languageLevelField.with(it.languageLevel),
                    isMPPBuild.with(it.mppBuild),
                    pluginInfoField.with(KotlinIdePlugin.getPluginInfo()),
                    eventFlags.with(KotlinASStatisticsEventFlags.calculateAndPackEventsFlagsToLong(it.kpmModule)),
                    nonDefaultLanguageFeaturesField.with(it.nonDefaultLanguageFeatures)
                )
            )
        }

        return metrics
    }

    private data class Data(
        val buildSystem: String,
        val platform: String,
        val languageLevel: String?,
        val nonDefaultLanguageFeatures: List<LanguageFeature>,
        val mppBuild: Boolean,
        val kpmModule: Boolean
    )

    private fun getBuildSystemType(it: Module): String {
        val buildSystem = it.buildSystemType
        return when {
            buildSystem == BuildSystemType.JPS -> "JPS"
            buildSystem.toString().lowercase(Locale.getDefault()).contains("maven") -> "Maven"
            buildSystem.toString().lowercase(Locale.getDefault()).contains("gradle") -> "Gradle"
            else -> "unknown"
        }
    }

    private val GROUP = EventLogGroup("kotlin.project.configuration", 30)

    private val systemField = EventFields.String("system", listOf("JPS", "Maven", "Gradle", "unknown"))
    private val platformField = EventFields.String("platform", composePlatformFields())
    private val languageLevelField = EventFields.StringValidatedByRegexpReference("languageLevel", "version")
    private val isMPPBuild = EventFields.Boolean("isMPP")
    private val pluginInfoField = EventFields.PluginInfo

    private val eventFlags = EventFields.Long("eventFlags")
    private val nonDefaultLanguageFeaturesField = EventFields.EnumList<LanguageFeature>("nonDefaultLanguageFeatures") { it.name }
    private fun composePlatformFields(): List<String> {
        return listOf(
            listOf(
                "jvm",
                "jvm.android",
                "js",
                "wasm",
                "wasm.js",
                "wasm.wasi",
                "wasm.unknown",
                "common",
                "native.unknown",
                "unknown"
            ),
            KonanTarget.predefinedTargets.keys.map { "native.$it" }
        ).flatten()
    }

    private val buildEvent = GROUP.registerVarargEvent(
        "Build",
        systemField,
        platformField,
        isMPPBuild,
        languageLevelField,
        pluginInfoField,
        eventFlags,
        nonDefaultLanguageFeaturesField,
    )
}
