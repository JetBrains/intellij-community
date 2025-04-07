// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run

// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import com.intellij.execution.ExecutionException
import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.RunConfigurationWithSuppressedDefaultRunAction
import com.intellij.facet.impl.invalid.FacetIgnorer
import com.intellij.facet.impl.invalid.InvalidFacet
import com.intellij.icons.AllIcons
import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.WriteExternalException
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.jetbrains.python.PyBundle
import org.jdom.Element
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.Icon
import javax.swing.JComponent

private class PythonLockedRunConfigurationEditor : SettingsEditor<PythonLockedRunConfiguration>() {

  override fun resetEditorFrom(s: PythonLockedRunConfiguration) {}

  override fun applyEditorTo(s: PythonLockedRunConfiguration) {}

  protected override fun createEditor(): JComponent = panel {
    row {
      label(PyBundle.message("python.run.configuration.is.not.editable.in.this.mode")).align(AlignX.CENTER)
    }
  }
}

private class PythonLockedRunConfiguration(val configProject: Project, val configFactory: ConfigurationFactory)
  : RunConfiguration, WithoutOwnBeforeRunSteps, RunConfigurationWithSuppressedDefaultRunAction {

  var theName: String? = null
  var configElement: Element? = null

  override fun getFactory(): ConfigurationFactory? {
    return configFactory
  }

  override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration?> {
    return PythonLockedRunConfigurationEditor()
  }

  override fun getProject(): Project? {
    return configProject
  }

  override fun clone(): RunConfiguration {
    return PythonLockedRunConfiguration(configProject, configFactory)
  }

  override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState? {
    throw ExecutionException(PyBundle.message("python.run.configuration.is.not.runnable.in.this.mode"))
  }

  override fun checkConfiguration() {
    throw RuntimeConfigurationWarning(PyBundle.message("python.run.configuration.is.not.runnable.in.this.mode"))
  }

  override fun setName(value: String) {
    theName = value
  }

  override fun getName(): @NlsSafe String {
    return tryGetName() ?: "LockedConfiguration${nameCounter.getAndIncrement()}"
  }

  override fun getIcon(): Icon? {
    return configFactory.icon
  }

  override fun readExternal(element: Element) {
    configElement = JDOMUtil.internElement(element)
  }

  @Throws(WriteExternalException::class)
  override fun writeExternal(element: Element) {
    val data = configElement ?: return

    for (a in data.getAttributes()) {
      element.setAttribute(a.name, a.value)
    }

    for (child in data.children) {
      element.addContent(child.clone())
    }
  }

  private fun tryGetName(): @NlsSafe String? {
    val name = theName
    if (name != null) {
      return name
    }

    val data = configElement ?: return null
    return data.getAttributeValue("name")
  }

  companion object {
    var nameCounter = AtomicInteger(1)
  }
}

private class PythonLockedRunConfigurationFactory(type: ConfigurationType)
  : ConfigurationFactory(type) {
  override fun getId(): String {
    return name
  }

  override fun createTemplateConfiguration(project: Project): RunConfiguration {
    return PythonLockedRunConfiguration(project, this)
  }
}

private open class PythonLockedRunConfigurationTypeBase(val theId: String, @Nls val name: String)
  : ConfigurationType {
  private val factory: ConfigurationFactory = PythonLockedRunConfigurationFactory(this)

  init {
    // Do not enable "lock" configs if Python plugin enabled
    if (PluginManager.getInstance().findEnabledPlugin(PluginId.getId("Pythonid")) != null) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override fun getDisplayName(): @Nls(capitalization = Nls.Capitalization.Title) String {
    return name
  }

  override fun getConfigurationTypeDescription(): @Nls(capitalization = Nls.Capitalization.Sentence) String? {
    return name
  }

  override fun getIcon(): Icon? {
    return AllIcons.Ultimate.PycharmLock
  }

  override fun getId(): @NonNls String {
    return theId
  }

  override fun getConfigurationFactories(): Array<out ConfigurationFactory?>? {
    return arrayOf(factory)
  }

  override fun isDumbAware(): Boolean {
    return true
  }

  override fun isManaged(): Boolean {
    return false
  }
}


private class DjangoServerLockedRunConfigurationType : PythonLockedRunConfigurationTypeBase("Python.DjangoServer", PyBundle.message("python.run.configuration.django.name"))
private class FlaskServerLockedRunConfigurationType : PythonLockedRunConfigurationTypeBase("Python.FlaskServer", PyBundle.message("flask.name"))
private class DbtRunLockedConfigurationType : PythonLockedRunConfigurationTypeBase("DbtRunConfiguration", "dbt")
private class FastAPILockedRunConfigurationType : PythonLockedRunConfigurationTypeBase("Python.FastAPI", "FastAPI")

private class DjangoFacetIgnorer : FacetIgnorer {
  override fun isIgnored(facet: InvalidFacet): Boolean = facet.name == "Django"
}