// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard.wizard.ui


import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.tools.projectWizard.core.Context
import org.jetbrains.kotlin.tools.projectWizard.core.Reader
import org.jetbrains.kotlin.tools.projectWizard.core.SettingsWriter
import org.jetbrains.kotlin.tools.projectWizard.core.Writer
import org.jetbrains.kotlin.tools.projectWizard.core.entity.ValidationResult
import org.jetbrains.kotlin.tools.projectWizard.core.entity.settings.SettingReference
import org.jetbrains.kotlin.tools.projectWizard.core.entity.settings.SettingType
import javax.swing.JComponent

abstract class Component : Displayable, ErrorNavigatable, Disposable {
    private val subComponents = mutableListOf<Component>()

    override fun dispose() {}

    open fun onInit() {
        subComponents.forEach(Component::onInit)
    }

    protected fun <C : Component> C.asSubComponent(): C {
        this@Component.registerSubComponent(this@asSubComponent)
        return this
    }

    protected fun registerSubComponent(subComponent: Component) {
        subComponents += subComponent
        Disposer.register(this, subComponent)
    }

    protected fun clearSubComponents() {
        subComponents.clear()
    }

    override fun navigateTo(error: ValidationResult.ValidationError) {
        subComponents.forEach { it.navigateTo(error) }
    }
}

abstract class DynamicComponent(private val context: Context) : Component() {
    private var isInitialized: Boolean = false

    override fun onInit() {
        super.onInit()
        isInitialized = true
    }

    var <V : Any, T : SettingType<V>> SettingReference<V, T>.value: V?
        get() = read { notRequiredSettingValue() }
        set(value) = modify {
            value?.let { setValue(it) }
        }

    init {
        write {
            eventManager.addSettingUpdaterEventListener { reference ->
                if (isInitialized) ApplicationManager.getApplication().invokeLater {
                    onValueUpdated(reference)
                }
            }
        }
    }

    protected fun <T> read(reader: Reader.() -> T): T =
        context.read(reader)

    protected fun <T> write(writer: Writer.() -> T): T =
        context.write(writer)

    protected fun <T> modify(modifier: SettingsWriter.() -> T): T =
        context.writeSettings(modifier)

    open fun onValueUpdated(reference: SettingReference<*, *>?) {}
}

abstract class TitledComponent(context: Context) : DynamicComponent(context) {
    open val alignment: TitleComponentAlignment get() = TitleComponentAlignment.AlignAgainstMainComponent
    open val additionalComponentPadding: Int = 0
    open val maximumWidth: Int? = null
    abstract val title: String?
    open val tooltipText: String? = null
    open fun shouldBeShown(): Boolean = true
}

sealed class TitleComponentAlignment {
    object AlignAgainstMainComponent : TitleComponentAlignment()
    data class AlignAgainstSpecificComponent(val alignTarget: JComponent) : TitleComponentAlignment()
    data class AlignFormTopWithPadding(val padding: Int) : TitleComponentAlignment()
}

interface FocusableComponent {
    fun focusOn() {}
}

interface ErrorNavigatable {
    fun navigateTo(error: ValidationResult.ValidationError)
}