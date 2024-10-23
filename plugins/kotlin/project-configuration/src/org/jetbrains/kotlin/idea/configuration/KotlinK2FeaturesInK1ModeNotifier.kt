// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.configuration

import com.intellij.diagnostic.VMOptions
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.options.ConfigurableWithId
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.projectConfiguration.KotlinProjectConfigurationBundle
import org.jetbrains.kotlin.psi.KtFile
import java.util.*
import java.util.function.Function
import javax.swing.JComponent

private val ExperimentalFeaturesWithK2OnlySupport = EnumSet.of<LanguageFeature>(
    LanguageFeature.BreakContinueInInlineLambdas,
    LanguageFeature.MultiDollarInterpolation,
    LanguageFeature.WhenGuards,
)

private const val KOTLIN_K2_FEATURES_IN_K1_MODE_NOTIFICATION_DISABLED = "kotlin.k2.features.in.k1.mode.notification.disabled"

class KotlinK2FeaturesInK1ModeNotifier : EditorNotificationProvider {
    override fun collectNotificationData(
        project: Project,
        file: VirtualFile
    ): Function<in FileEditor, out JComponent?>? {
        if (PropertiesComponent.getInstance().getBoolean(KOTLIN_K2_FEATURES_IN_K1_MODE_NOTIFICATION_DISABLED)) return null
        val unsupportedFeatures = collectUnsupportedFeatures(project, file)
        if (unsupportedFeatures.isEmpty()) return null
        return Function { fileEditor: FileEditor -> createNotificationPanel(project, fileEditor, unsupportedFeatures) }
    }

    private fun collectUnsupportedFeatures(project: Project, file: VirtualFile): List<LanguageFeature> {
        if (!file.nameSequence.endsWith(KotlinFileType.DOT_DEFAULT_EXTENSION)) return emptyList()
        val ktFile = PsiManager.getInstance(project).findFile(file) as? KtFile ?: return emptyList()

        return ExperimentalFeaturesWithK2OnlySupport.filter { feature ->
            ktFile.languageVersionSettings.supportsFeature(feature)
        }
    }

    private fun createNotificationPanel(
        project: Project,
        fileEditor: FileEditor,
        unsupportedFeatures: List<LanguageFeature>,
    ): JComponent {
        val featureNames = unsupportedFeatures.joinToString { "'${it.presentableName}'" }

        return EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Warning).apply {
            text = KotlinProjectConfigurationBundle.message("k1.mode.does.not.support.features.0", featureNames)
            if (VMOptions.canWriteOptions()) {
                createActionLabel(KotlinProjectConfigurationBundle.message("enable.k2.mode")) {
                    ShowSettingsUtil.getInstance().showSettingsDialog(project, { configurable ->
                        // KotlinLanguageConfiguration#ID, hardcoded because of a circular dependency
                        (configurable as? ConfigurableWithId)?.id == "preferences.language.Kotlin"
                    }, null)
                }
            }
            createActionLabel(KotlinProjectConfigurationBundle.message("k1.does.not.support.features.ignore")) {
                PropertiesComponent.getInstance().setValue(KOTLIN_K2_FEATURES_IN_K1_MODE_NOTIFICATION_DISABLED, true)
                EditorNotifications.getInstance(project).updateNotifications(fileEditor.file)
            }
        }
    }
}
