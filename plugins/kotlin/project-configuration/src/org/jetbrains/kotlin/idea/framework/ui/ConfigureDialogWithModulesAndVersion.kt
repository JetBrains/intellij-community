// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.framework.ui

import com.google.common.io.Closeables
import com.google.gson.JsonParser
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.observable.util.transform
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.io.HttpRequests
import com.intellij.util.net.HttpConfigurable
import com.intellij.util.text.VersionComparatorUtil
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout.standaloneCompilerVersion
import org.jetbrains.kotlin.idea.configuration.*
import org.jetbrains.kotlin.idea.projectConfiguration.KotlinProjectConfigurationBundle.message
import org.jetbrains.kotlin.idea.statistics.KotlinJ2KOnboardingFUSCollector
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.TimeUnit
import javax.swing.JComponent

class ConfigureDialogWithModulesAndVersion(
    project: Project,
    configurator: KotlinProjectConfigurator,
    excludedModules: Collection<Module>,
    minimumVersion: String
) : DialogWrapper(project) {
    private val chooseModulePanel = ChooseModulePanel(project, configurator, excludedModules)
    private val kotlinVersionChooser =
        KotlinVersionChooser(project, minimumVersion, disposable, ModalityState.stateForComponent(window))

    private val listOfKotlinVersionsAndModulesText = AtomicProperty("")
    private val jvmModulesTargetingUnsupportedJvm: Map<String, List<String>>

    private val rootModuleVersion: String?

    private val rootModule = getRootModule(project)

    val kotlinVersion: String?
        get() = kotlinVersionChooser.kotlinVersion

    val modulesToConfigure: List<Module>
        get() = chooseModulePanel.modulesToConfigure

    val versionsAndModules: Map<String, Map<String, Module>>

    val modulesAndJvmTargets: Map<ModuleName, TargetJvm>

    init {
        KotlinJ2KOnboardingFUSCollector.logShowConfigureKtWindow(project)
        title = message("configure.kotlin.title", configurator.presentableText)
        val compatibility = checkModuleJvmTargetCompatibility(
            chooseModulePanel.modules, IdeKotlinVersion.get(DEFAULT_KOTLIN_VERSION)
        )
        jvmModulesTargetingUnsupportedJvm = compatibility.modulesByIncompatibleJvmTarget
        modulesAndJvmTargets = compatibility.moduleJvmTargets

        val kotlinVersions = getKotlinVersionsAndModules(project, configurator)
        versionsAndModules = kotlinVersions.first
        rootModuleVersion = kotlinVersions.second

        isOKActionEnabled = false
        kotlinVersionChooser.runAfterVersionsLoaded {
            isOKActionEnabled = true
        }
        kotlinVersionChooser.runAfterVersionSelected {
            showWarningIfThereAreDifferentKotlinVersions()
        }
        chooseModulePanel.notifyOnChange {
            showWarningIfThereAreDifferentKotlinVersions()
        }

        init()
    }

    override fun createCenterPanel(): JComponent {
        val kotlinVersionWarningPredicate = listOfKotlinVersionsAndModulesText.transform { it.isNotEmpty() }

        // Make the dialog resize itself after the warning is displayed/hidden
        var oldWarningVisible = false
        kotlinVersionWarningPredicate.afterChange {
            if (oldWarningVisible == it) return@afterChange
            oldWarningVisible = it
            ApplicationManager.getApplication().invokeLater {
                if (!isShowing) return@invokeLater
                validate()
                pack()
            }
        }

        return panel {
            row {
                cell(chooseModulePanel.createPanel()).align(AlignX.FILL)
            }
            row {
                cell(kotlinVersionChooser.createPanel()).align(AlignX.FILL)
            }
            getUnsupportedJvmTargetWarning(jvmModulesTargetingUnsupportedJvm)?.let { unsupportedJvmTargetText ->
                separator()
                row {
                    @Suppress("HardCodedStringLiteral") // string constructed from non-hardcoded strings
                    text(unsupportedJvmTargetText)
                }
            }
            separator().visibleIf(kotlinVersionWarningPredicate)
            row {
                @Suppress("HardCodedStringLiteral") // string constructed from non-hardcoded strings
                text(listOfKotlinVersionsAndModulesText.get())
                    .bindText(listOfKotlinVersionsAndModulesText)
                    .visibleIf(kotlinVersionWarningPredicate)
                    .resizableColumn()
            }
        }
    }

    private fun createMessageAboutDifferentKotlinVersions(currentlySelectedKotlinVersion: String): String {
        val message = StringBuilder()
        message.append(message("configure.kotlin.root.should.contain.same.version"))
        message.append(message("configure.kotlin.currently.there.are.versions"))

        versionsAndModules.keys
            .filter { it != currentlySelectedKotlinVersion }
            .sorted()
            .forEach {
                val modules = versionsAndModules[it] ?: return@forEach
                val modulesNames = modules.keys
                val modulesEnumeration = java.lang.StringBuilder()
                if (modulesNames.size > MODULES_TO_DISPLAY_SIZE) {
                    modulesEnumeration.append(modulesNames.take(MODULES_TO_DISPLAY_SIZE).sorted().joinToString())
                    modulesEnumeration.append(
                        message(
                            "configure.kotlin.version.and.modules.and.more", modulesNames.size - MODULES_TO_DISPLAY_SIZE
                        )
                    )
                } else {
                    modulesEnumeration.append(modulesNames.sorted().joinToString())
                }
                message.append(message("configure.kotlin.version.and.modules", it, modulesEnumeration.toString()))
            }

        message.append(message("configure.kotlin.choose.another.kotlin.version"))
        return message.toString()
    }

    private fun createMessageThatTopLevelAndModulesShouldHaveSameVersion(): String {
        return message("configure.kotlin.root.contains.another.kotlin", rootModuleVersion ?: "") +
                message("configure.kotlin.root.should.contain.same.version") +
                message("configure.kotlin.choose.the.same.kotlin.version", rootModuleVersion ?: "")
    }

    private fun showWarningIfThereAreDifferentKotlinVersions() {
        val selectedKotlinVersion = kotlinVersion
        val modulesToConfigure = chooseModulePanel.modulesToConfigure
        if (versionsAndModules.isEmpty() || selectedKotlinVersion == null || modulesToConfigure.isEmpty()) {
            listOfKotlinVersionsAndModulesText.set("")
            return
        }
        val text = if (modulesToConfigure.contains(rootModule) &&
            (versionsAndModules.size != 1 || !versionsAndModules.containsKey(selectedKotlinVersion))
        ) {
            createMessageAboutDifferentKotlinVersions(selectedKotlinVersion)
        } else if (rootModuleVersion != null && IdeKotlinVersion.get(rootModuleVersion) != IdeKotlinVersion.get(selectedKotlinVersion)) {
            createMessageThatTopLevelAndModulesShouldHaveSameVersion()
        } else {
            ""
        }
        listOfKotlinVersionsAndModulesText.set(text)
    }

    private fun getUnsupportedJvmTargetWarning(jvmModulesTargetingUnsupportedJvm: Map<String, List<String>>): String? {
        if (jvmModulesTargetingUnsupportedJvm.isEmpty()) {
            return null
        }
        val message = java.lang.StringBuilder()
        message.append(message("configurator.kotlin.jvm.targets.unsupported", "1.8"))
        jvmModulesTargetingUnsupportedJvm.keys
            .sorted()
            .forEach { jvmTargetVersion: String ->
                val modulesWithThisTargetVersion = jvmModulesTargetingUnsupportedJvm[jvmTargetVersion] ?: return@forEach
                val modulesEnumeration = StringBuilder()
                if (modulesWithThisTargetVersion.size > MODULES_TO_DISPLAY_SIZE) {
                    modulesEnumeration.append(modulesWithThisTargetVersion.take(MODULES_TO_DISPLAY_SIZE).sorted().joinToString())
                    modulesEnumeration.append(
                        message(
                            "configure.kotlin.jvm.target.in.modules.and.more",
                            modulesWithThisTargetVersion.size - MODULES_TO_DISPLAY_SIZE
                        )
                    )
                } else {
                    modulesEnumeration.append(modulesWithThisTargetVersion.sorted().joinToString())
                }
                message.append(message("configurator.kotlin.jvm.target.in.modules", jvmTargetVersion, modulesEnumeration.toString()))
            }
        message.append(message("configurator.kotlin.jvm.target.bump.manually.learn.more"))
        return message.toString()
    }

    companion object {

        private val LOG = Logger.getInstance(ConfigureDialogWithModulesAndVersion::class.java)

        private const val MODULES_TO_DISPLAY_SIZE = 2

        internal const val DEFAULT_KOTLIN_VERSION = "1.9.22"

        @Throws(IOException::class)
        @JvmStatic
        fun loadVersions(minimumVersion: String?): Collection<String> {
            val versions: MutableList<String> = ArrayList()
            val kotlinCompilerVersion = standaloneCompilerVersion
            val kotlinArtifactVersion = kotlinCompilerVersion.artifactVersion
            val repositoryDescription = getRepositoryForVersion(kotlinCompilerVersion)
            if (repositoryDescription?.bintrayUrl != null) {
                val eapConnection =
                    HttpConfigurable.getInstance().openHttpConnection(repositoryDescription.bintrayUrl + kotlinArtifactVersion)
                try {
                    val timeout = TimeUnit.SECONDS.toMillis(30).toInt()
                    eapConnection.setConnectTimeout(timeout)
                    eapConnection.setReadTimeout(timeout)
                    if (eapConnection.getResponseCode() == 200) {
                        versions.add(kotlinArtifactVersion)
                    }
                } finally {
                    eapConnection.disconnect()
                }
            }
            val url = Registry.stringValue("repo.with.kotlin.versions.url")
            val urlConnection = HttpConfigurable.getInstance().openHttpConnection(url)
            try {
                val timeout = TimeUnit.SECONDS.toMillis(30).toInt()
                urlConnection.setConnectTimeout(timeout)
                urlConnection.setReadTimeout(timeout)
                urlConnection.connect()
                val streamReader = InputStreamReader(urlConnection.inputStream, StandardCharsets.UTF_8)
                try {
                    val rootElement = JsonParser.parseReader(streamReader)
                    val docsElements = rootElement.getAsJsonObject()["response"].getAsJsonObject()["docs"].getAsJsonArray()
                    for (element in docsElements) {
                        val versionNumber = element.getAsJsonObject()["v"].asString
                        if (VersionComparatorUtil.compare(minimumVersion, versionNumber) <= 0) {
                            versions.add(versionNumber)
                        }
                    }
                } finally {
                    Closeables.closeQuietly(streamReader)
                }
            } catch (e: HttpRequests.HttpStatusException) {
                LOG.warn("Cannot load data from ${url} (statusCode=${e.statusCode})", e)
                throw e
            } catch (e: Exception) {
                LOG.warn("Error parsing Kotlin versions JSON data: ${e} (URL=${url})", e)
                throw e
            } finally {
                urlConnection.disconnect()
            }
            Collections.sort(versions, VersionComparatorUtil.COMPARATOR.reversed())

            // Handle the case when the new version has just been released and the Maven search index hasn't been updated yet
            if (kotlinCompilerVersion.isRelease && !versions.contains(kotlinArtifactVersion)) {
                versions.add(0, kotlinArtifactVersion)
            }
            return versions.mapNotNull {
                val ideVersion = IdeKotlinVersion.parse(it).getOrNull() ?: return@mapNotNull null
                ideVersion.rawVersion.takeIf { ideVersion.isRelease && !it.contains("-") }
            }
        }
    }
}