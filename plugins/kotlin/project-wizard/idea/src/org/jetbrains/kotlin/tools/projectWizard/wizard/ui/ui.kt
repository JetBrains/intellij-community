// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.projectWizard.wizard.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.ui.ColorUtil
import com.intellij.ui.PopupHandler
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.tools.projectWizard.core.entity.ValidationResult
import org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators.*
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.ModuleSubType
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.ModuleType
import org.jetbrains.kotlin.tools.projectWizard.settings.DisplayableSettingItem
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.Module
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.ModuleKind
import java.awt.BorderLayout
import java.awt.Font
import java.awt.LayoutManager
import javax.swing.*
import javax.swing.border.Border
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

internal inline fun label(@NlsContexts.Label text: String, bold: Boolean = false, init: JBLabel.() -> Unit = {}) = JBLabel().apply {
    font = StartupUiUtil.labelFont.deriveFont(if (bold) Font.BOLD else Font.PLAIN)
    this.text = text
    init()
}

inline fun customPanel(layout: LayoutManager? = BorderLayout(), init: JPanel.() -> Unit = {}) = JPanel(layout).apply(init)

inline fun borderPanel(init: BorderLayoutPanel.() -> Unit = {}) = BorderLayoutPanel().apply(init)

fun textField(@Nls defaultValue: String?, onUpdated: (value: String) -> Unit) =
    JBTextField(defaultValue)
        .withOnUpdatedListener(onUpdated)

fun <F : JTextField> F.withOnUpdatedListener(onUpdated: (value: String) -> Unit) = apply {
    document.addDocumentListener(object : DocumentListener {
        override fun insertUpdate(e: DocumentEvent?) = onUpdated(this@apply.text)
        override fun removeUpdate(e: DocumentEvent?) = onUpdated(this@apply.text)
        override fun changedUpdate(e: DocumentEvent?) = onUpdated(this@apply.text)
    })
}

@NlsSafe
internal fun String.asHtml() = "<html><body>$this</body></html>"

@Nls
fun ValidationResult.ValidationError.asHtml() = when (messages.size) {
    0 -> ""
    1 -> messages.single()
    else -> {
        val errorList = messages.joinToString(separator = "") { "<li>${it}</li>" }
        "<ul>$errorList</ul>".asHtml()
    }
}

val ModuleType.icon: Icon
    get() = when (this) {
        ModuleType.jvm -> KotlinIcons.Wizard.JVM
        ModuleType.js -> KotlinIcons.Wizard.JS
        ModuleType.wasm -> KotlinIcons.Wizard.WEB
        ModuleType.native -> KotlinIcons.Wizard.NATIVE
        ModuleType.common -> KotlinIcons.SMALL_LOGO
        ModuleType.android -> KotlinIcons.Wizard.ANDROID
    }


val Module.icon: Icon
    get() = configurator.icon

val ModuleSubType.icon: Icon
    get() = when (this) {
        ModuleSubType.jvm -> KotlinIcons.Wizard.JVM
        ModuleSubType.js -> KotlinIcons.Wizard.JS
        ModuleSubType.wasm -> KotlinIcons.Wizard.WEB
        ModuleSubType.android, ModuleSubType.androidNativeArm32, ModuleSubType.androidNativeArm64 -> KotlinIcons.Wizard.ANDROID
        ModuleSubType.iosArm32, ModuleSubType.iosArm64, ModuleSubType.iosX64, ModuleSubType.iosSimulatorArm64,
        ModuleSubType.ios, ModuleSubType.iosCocoaPods -> KotlinIcons.Wizard.IOS
        ModuleSubType.linuxArm32Hfp, ModuleSubType.linuxMips32, ModuleSubType.linuxMipsel32, ModuleSubType.linuxX64 ->
            KotlinIcons.Wizard.LINUX
        ModuleSubType.macosX64 -> KotlinIcons.Wizard.MAC_OS
        ModuleSubType.mingwX64, ModuleSubType.mingwX86 -> KotlinIcons.Wizard.WINDOWS
        ModuleSubType.common -> KotlinIcons.SMALL_LOGO
    }

val ModuleKind.icon: Icon
    get() = when (this) {
        ModuleKind.multiplatform -> KotlinIcons.MPP
        ModuleKind.singlePlatformJsBrowser -> KotlinIcons.Wizard.JS
        ModuleKind.singlePlatformJsNode -> KotlinIcons.Wizard.NODE_JS
        ModuleKind.singlePlatformJvm -> KotlinIcons.Wizard.JVM
        ModuleKind.target -> AllIcons.Nodes.Module
        ModuleKind.singlePlatformAndroid -> KotlinIcons.Wizard.ANDROID
        ModuleKind.ios -> KotlinIcons.Wizard.IOS
    }

val ModuleConfigurator.icon: Icon
    get() = when (this) {
        is JsBrowserTargetConfigurator, MppLibJsBrowserTargetConfigurator -> KotlinIcons.Wizard.WEB
        is JsNodeTargetConfigurator -> KotlinIcons.Wizard.NODE_JS
        is IOSSinglePlatformModuleConfigurator -> KotlinIcons.Wizard.IOS
        is SimpleTargetConfigurator -> moduleSubType.icon
        is TargetConfigurator -> moduleType.icon
        else -> moduleKind.icon
    }

fun ToolbarDecorator.createPanelWithPopupHandler(popupTarget: JComponent) = createPanel().apply toolbarApply@{
    val actionGroup = DefaultActionGroup().apply {
        ToolbarDecorator.findAddButton(this@toolbarApply)?.let(this::add)
        ToolbarDecorator.findRemoveButton(this@toolbarApply)?.let(this::add)
    }
    PopupHandler.installPopupMenu(
        popupTarget,
        actionGroup,
        "ToolbarDecoratorPopup"
    )
}

fun <C : JComponent> C.addBorder(border: Border): C = apply {
    this.border = BorderFactory.createCompoundBorder(border, this.border)
}

fun <T> runWithProgressBar(@Nls title: String, action: () -> T): T =
    ProgressManager.getInstance().runProcessWithProgressSynchronously(
        ThrowableComputable { action() },
        title,
        true,
        null
    )

val DisplayableSettingItem.fullTextHtml
    get() = buildString {
        append(text)
        greyText?.let { grey ->
            append(" ")
            append("""<span style="color:${ColorUtil.toHtmlColor(SimpleTextAttributes.GRAYED_ATTRIBUTES.fgColor)};">""")
            append(grey)
            append("</span>")
        }
    }.asHtml()

object UIConstants {
    const val PADDING = 8
}