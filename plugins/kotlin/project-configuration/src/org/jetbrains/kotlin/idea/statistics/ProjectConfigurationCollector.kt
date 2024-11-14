// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.statistics

import com.intellij.facet.ProjectFacetManager
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.config.LanguageFeature
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

internal class ProjectConfigurationCollector : ProjectUsagesCollector() {
    override fun getGroup() = GROUP

    override fun getMetrics(project: Project): Set<MetricEvent> {
        val metrics = mutableSetOf<MetricEvent>()
        val modulesWithFacet = ProjectFacetManager.getInstance(project).getModulesWithFacet(KotlinFacetType.TYPE_ID)

        if (modulesWithFacet.isNotEmpty()) {
            modulesWithFacet.forEach {
                val buildSystem = getBuildSystemType(it)
                val platform = getPlatform(it)
                val languageLevel = KotlinFacet.get(it)?.configuration?.settings?.languageLevel?.versionString
                val nonDefaultLanguageFeatures = getNonDefaultLanguageFeatures(it).toList()
                metrics.add(
                    buildEvent.metric(
                        systemField.with(buildSystem),
                        platformField.with(platform),
                        languageLevelField.with(languageLevel),
                        isMPPBuild.with(it.isMultiPlatformModule || it.isNewMultiPlatformModule),
                        pluginInfoField.with(KotlinIdePlugin.getPluginInfo()),
                        eventFlags.with(KotlinASStatisticsEventFlags.calculateAndPackEventsFlagsToLong(it)),
                        nonDefaultLanguageFeaturesField.with(nonDefaultLanguageFeatures)
                    )
                )
            }
        }

        return metrics
    }

    private fun getBuildSystemType(it: Module): String {
        val buildSystem = it.buildSystemType
        return when {
            buildSystem == BuildSystemType.JPS -> "JPS"
            buildSystem.toString().toLowerCase().contains("maven") -> "Maven"
            buildSystem.toString().toLowerCase().contains("gradle") -> "Gradle"
            else -> "unknown"
        }
    }

    private val GROUP = EventLogGroup("kotlin.project.configuration", 14)

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
