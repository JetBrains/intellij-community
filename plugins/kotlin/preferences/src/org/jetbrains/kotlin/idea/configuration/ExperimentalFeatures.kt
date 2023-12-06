// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.configuration

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.components.JBCheckBox
import org.jdesktop.swingx.VerticalLayout
import org.jetbrains.kotlin.idea.base.plugin.KotlinBasePluginBundle
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import javax.swing.JCheckBox
import javax.swing.JPanel

object ExperimentalFeatures {
    val NewJ2k = RegistryExperimentalFeature(
        title = KotlinBasePluginBundle.message("configuration.feature.text.new.java.to.kotlin.converter"),
        registryKey = "kotlin.experimental.new.j2k",
        enabledByDefault = true
    )

    val allFeatures: List<ExperimentalFeature> = listOf(
        NewJ2k,
    ) + ExperimentalFeature.EP_NAME.extensionList
}

abstract class ExperimentalFeature {
    abstract val title: String
    abstract var isEnabled: Boolean
    open fun shouldBeShown(): Boolean = true
    open fun onFeatureStatusChanged(enabled: Boolean) {}

    companion object {
        internal var EP_NAME = ExtensionPointName<ExperimentalFeature>("org.jetbrains.kotlin.experimentalFeature")
    }
}

open class RegistryExperimentalFeature(
    override val title: String,
    private val registryKey: String,
    private val enabledByDefault: Boolean
) : ExperimentalFeature() {
    final override var isEnabled
        get() = Registry.`is`(registryKey, enabledByDefault)
        set(value) {
            Registry.get(registryKey).setValue(value)
        }
}

class ExperimentalFeaturesPanel : JPanel(VerticalLayout(5)) {
    private val featuresWithCheckboxes = ExperimentalFeatures.allFeatures.map { feature ->
        FeatureWithCheckbox(
            feature,
            JBCheckBox(feature.title, feature.isEnabled)
        )
    }

    init {
        featuresWithCheckboxes.forEach { (feature, checkBox) ->
            if (feature.shouldBeShown()) {
                add(checkBox)
            }
        }
    }

    private data class FeatureWithCheckbox(
        val feature: ExperimentalFeature,
        val checkbox: JCheckBox
    )

    fun isModified() = featuresWithCheckboxes.any { (feature, checkBox) ->
        feature.isEnabled != checkBox.isSelected
    }

    fun applySelectedChanges() {
        featuresWithCheckboxes.forEach { (feature, checkBox) ->
            if (feature.isEnabled != checkBox.isSelected) {
                feature.isEnabled = checkBox.isSelected
                feature.onFeatureStatusChanged(checkBox.isSelected)
            }
        }
    }

    companion object {
        fun createPanelIfShouldBeShown(): ExperimentalFeaturesPanel? {
            val kotlinVersion = KotlinPluginLayout.standaloneCompilerVersion
            val shouldBeShown = kotlinVersion.isPreRelease && kotlinVersion.kind !is IdeKotlinVersion.Kind.ReleaseCandidate

            return if (shouldBeShown) ExperimentalFeaturesPanel() else null
        }
    }
}
