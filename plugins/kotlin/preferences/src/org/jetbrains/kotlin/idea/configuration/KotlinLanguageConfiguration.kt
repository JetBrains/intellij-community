// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.configuration

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.PluginUpdateStatus
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.updateSettings.impl.UpdateSettings
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.KotlinPluginUpdater
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinIdePlugin
import org.jetbrains.kotlin.idea.configuration.ui.KotlinLanguageConfigurationForm
import org.jetbrains.kotlin.idea.preferences.KotlinPreferencesBundle
import javax.swing.JComponent

class KotlinLanguageConfiguration : SearchableConfigurable, Configurable.NoScroll {
    companion object {
        const val ID = "preferences.language.Kotlin"

        private fun saveSelectedChannel(channelOrdinal: Int) {
            val hosts = UpdateSettings.getInstance().storedPluginHosts
            hosts.removeIf {
                it.startsWith("https://plugins.jetbrains.com/plugins/") &&
                        (it.endsWith("/6954") || it.endsWith(KotlinIdePlugin.id.idString))
            }

            UpdateChannel.values().find { it.ordinal == channelOrdinal }?.let { eapChannel ->
                if (eapChannel != UpdateChannel.STABLE) {
                    hosts.add(eapChannel.url ?: error(KotlinPreferencesBundle.message("configuration.error.text.shouldn.t.add.null.urls.to.custom.repositories")))
                }
            }
        }

        enum class UpdateChannel(val url: String?, val title: String) {
            STABLE(null, KotlinPreferencesBundle.message("configuration.title.stable")),
            EAP(
                "https://plugins.jetbrains.com/plugins/eap/${KotlinIdePlugin.id.idString}",
                KotlinPreferencesBundle.message("configuration.title.early.access.preview.version")
            );

            fun isInHosts(): Boolean {
                if (this == STABLE) return false
                return url in UpdateSettings.getInstance().pluginHosts
            }
        }
    }

    private val form = KotlinLanguageConfigurationForm()
    private var update: PluginUpdateStatus.Update? = null

    private var savedChannel = -1

    private var versionForInstallation: String? = null

    private var installedVersion: String? = null
    @Nls
    private var installingStatus: String? = null

    override fun getId(): String = ID

    override fun getDisplayName(): String = KotlinPreferencesBundle.message("configuration.name.kotlin")

    override fun isModified() =
        form.experimentalFeaturesPanel.isModified()

    override fun apply() {
        // Selected channel is now saved automatically

        form.experimentalFeaturesPanel.applySelectedChanges()
    }

    private fun setInstalledVersion(@NlsSafe installedVersion: String?, @Nls installingStatus: String?) {
        this.installedVersion = installedVersion
        this.installingStatus = installingStatus
    }

    override fun createComponent(): JComponent? {
        form.updateCheckProgressIcon.suspend()
        form.updateCheckProgressIcon.setPaintPassiveIcon(false)

        form.reCheckButton.addActionListener {
            checkForUpdates()
        }

        form.installButton.isVisible = false
        form.installButton.addActionListener {
            update?.let {
                form.hideInstallButton()

                setInstalledVersion(it.pluginDescriptor.version, KotlinPreferencesBundle.message("configuration.status.text.installing"))

                form.installStatusLabel.text = installingStatus

                KotlinPluginUpdater.getInstance().installPluginUpdate(
                    it,
                    successCallback = {
                        setInstalledVersion(it.pluginDescriptor.version, IdeBundle.message("plugin.manager.installed.tooltip"))
                        if (versionForInstallation == it.pluginDescriptor.version) {
                            form.installStatusLabel.text = installingStatus
                        }
                    },
                    cancelCallback = {
                        if (versionForInstallation == it.pluginDescriptor.version) {
                            form.installStatusLabel.text = ""
                            form.showInstallButton()

                            setInstalledVersion(null, null)
                        }
                    },
                    errorCallback = {
                        if (versionForInstallation == it.pluginDescriptor.version) {
                            form.installStatusLabel.text = KotlinPreferencesBundle.message("configuration.status.text.installation.failed")
                            form.showInstallButton()
                            setInstalledVersion(null, null)
                        }
                    }
                )
            }
        }

        form.initChannels(UpdateChannel.values().map { it.title })

        savedChannel = UpdateChannel.values().find { it.isInHosts() }?.ordinal ?: 0
        form.channelCombo.selectedIndex = savedChannel

        form.channelCombo.addActionListener {
            val newChannel = form.channelCombo.selectedIndex
            if (newChannel != savedChannel) {
                savedChannel = newChannel
                checkForUpdates()
            }
        }

        checkForUpdates()

        return form.mainPanel
    }

    private fun checkForUpdates() {
        saveChannelSettings()
        form.updateCheckProgressIcon.resume()
        form.resetUpdateStatus()
        KotlinPluginUpdater.getInstance().runUpdateCheck { pluginUpdateStatus ->
            // Need this to show something is happening when check is very fast
            Thread.sleep(30)
            form.updateCheckProgressIcon.suspend()

            when (pluginUpdateStatus) {
                PluginUpdateStatus.LatestVersionInstalled -> {
                    form.setUpdateStatus(
                        KotlinPreferencesBundle.message("configuration.message.text.you.have.the.latest.version.of.the.plugin.installed"),
                        false
                    )
                }

                is PluginUpdateStatus.Update -> {
                    update = pluginUpdateStatus
                    versionForInstallation = update?.pluginDescriptor?.version
                    form.setUpdateStatus(
                        KotlinPreferencesBundle.message("configuration.message.text.a.new.version.is.available",
                            pluginUpdateStatus.pluginDescriptor.version
                        ),
                        true
                    )
                    if (installedVersion != null && installedVersion == versionForInstallation) {
                        // Installation of the plugin has been started or finished
                        form.hideInstallButton()
                        form.installStatusLabel.text = installingStatus
                    }
                }

                is PluginUpdateStatus.CheckFailed ->
                    form.setUpdateStatus(
                        KotlinPreferencesBundle.message("configuration.message.text.update.check.failed", pluginUpdateStatus.message),
                        false
                    )

                is PluginUpdateStatus.Unverified -> {
                    val version = pluginUpdateStatus.updateStatus.pluginDescriptor.version
                    val generalLine = KotlinPreferencesBundle.message("configuration.message.text.a.new.version.is.found",
                        version,
                        pluginUpdateStatus.verifierName
                    )
                    val reasonLine = pluginUpdateStatus.reason ?: ""
                    val message = "<html>$generalLine<br/>$reasonLine</html>"
                    form.setUpdateStatus(message, false)
                }
            }

            false  // do not auto-retry update check
        }
    }

    private fun saveChannelSettings() {
        saveSelectedChannel(form.channelCombo.selectedIndex)
    }
}

