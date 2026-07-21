// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.repository

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.NamedConfigurable
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.packaging.toolwindow.PyPackageIcons
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel

@ApiStatus.Internal
internal class PyRepositoryListItem(
  val repository: PyPackageRepository,
  private val project: Project,
  private val isDefault: Boolean = false,
  private val getAllNames: () -> List<String> = { emptyList() },
) : NamedConfigurable<PyPackageRepository>(true, null) {
  @NlsSafe
  private var currentName = repository.name
  private var password: String? = null

  private val propertyGraph = PropertyGraph()
  private val urlProperty = propertyGraph.lazyProperty { repository.repositoryUrl }
  private val nameProperty = propertyGraph.lazyProperty { repository.name }
  private val loginProperty = propertyGraph.lazyProperty { repository.login ?: "" }
  private val passwordProperty = propertyGraph.lazyProperty { "" }
  private val authorizationTypeProperty = propertyGraph.lazyProperty { repository.authorizationType }
  private val enabledProperty = propertyGraph.lazyProperty { repository.enabled }
  private val isInvalidProperty = propertyGraph.lazyProperty { repository in service<PyPackageRepositories>().invalidRepositories }

  private val presenter = PyRepositoryListItemPresenter(isDefault, getAllNames)

  private var nameErrorRow: Row? = null
  private var urlErrorRow: Row? = null
  private var urlErrorLabel: javax.swing.JLabel? = null
  private var passwordLoadJob: Job? = null

  /**
   * Single source of truth for "PropertyGraph property ← [PyPackageRepository] field".
   * [reset] iterates these instead of hand-copying each field, so adding a new repository
   * field that should be reflected in the editor is one line — not two places to keep in sync.
   * Bindings whose [skipForDefault] is `true` are not touched when [isDefault] is set.
   */
  private data class FormBinding<T>(
    val property: GraphProperty<T>,
    val skipForDefault: Boolean,
    val extract: (PyPackageRepository) -> T,
  )

  private val formBindings: List<FormBinding<*>> = listOf(
    FormBinding(enabledProperty, skipForDefault = false) { it.enabled },
    FormBinding(isInvalidProperty, skipForDefault = false) {
      it in service<PyPackageRepositories>().invalidRepositories
    },
    FormBinding(nameProperty, skipForDefault = true) { it.name },
    FormBinding(urlProperty, skipForDefault = true) { it.repositoryUrl },
    FormBinding(authorizationTypeProperty, skipForDefault = true) { it.authorizationType },
    FormBinding(loginProperty, skipForDefault = true) { it.login ?: "" },
  )

  private fun <T> FormBinding<T>.applyFrom(repo: PyPackageRepository, isDefault: Boolean) {
    if (skipForDefault && isDefault) return
    property.set(extract(repo))
  }

  init {
    loadPasswordAsync()
  }

  private fun loadPasswordAsync() {
    // Skip credential fetch when the repository has no authentication: the password field is
    // hidden, and triggering `PropertyGraph.set` from a background coroutine has been observed
    // to fight with the checkbox binding above (see PY-89838 race investigation).
    if (!authorizationTypeProperty.get().requiresCredentials) return
    passwordLoadJob?.cancel()
    passwordLoadJob = project.service<PyPackagingToolWindowService>().serviceScope.launch {
      val stored = repository.getPassword()
      withContext(Dispatchers.EDT) {
        password = stored
        // Don't overwrite a user-typed value the binding has already pushed into the property.
        if (passwordProperty.get().isEmpty()) passwordProperty.set(stored)
      }
    }
  }

  override fun getDisplayName(): String = currentName

  override fun getIcon(expanded: Boolean): Icon =
    if (repository in service<PyPackageRepositories>().invalidRepositories) PyPackageIcons.RepositoryFailed
    else PyPackageIcons.Repository

  override fun isModified(): Boolean {
    if (repository.enabled != enabledProperty.get()) return true
    if (isDefault) return false
    return nameProperty.get() != repository.name
           || repository.repositoryUrl != urlProperty.get()
           || repository.authorizationType != authorizationTypeProperty.get()
           || repository.login != loginProperty.get()
           || (password ?: "") != passwordProperty.get()
  }

  override fun apply() {
    repository.enabled = enabledProperty.get()
    if (isDefault) return
    if (currentName != repository.name) repository.clearCredentials()
    repository.name = currentName
    val newUrl = urlProperty.get().trim()
    val normalizedUrl = if (newUrl.isEmpty() || newUrl.endsWith("/")) newUrl else "$newUrl/"
    repository.repositoryUrl = normalizedUrl
    if (urlProperty.get() != normalizedUrl) urlProperty.set(normalizedUrl)
    repository.authorizationType = authorizationTypeProperty.get()
    if (repository.authorizationType.requiresCredentials) {
      repository.login = loginProperty.get()
      repository.setPassword(passwordProperty.get())
      password = passwordProperty.get()
    }
    else {
      repository.login = ""
      repository.clearCredentials()
      password = ""
    }
  }

  override fun reset() {
    super.reset()
    formBindings.forEach { it.applyFrom(repository, isDefault) }
    if (!isDefault) {
      currentName = repository.name
      password = null
      passwordProperty.set("")
      loadPasswordAsync()
    }
  }

  override fun setDisplayName(name: String) {
    if (!isDefault) currentName = name
  }

  override fun getEditableObject(): PyPackageRepository = repository

  @Suppress("DialogTitleCapitalization")
  override fun getBannerSlogan(): String = displayName

  override fun createOptionsPanel(): JComponent {
    setNameFieldShown(false)
    return buildSettingsPanel()
  }

  private fun buildSettingsPanel(): JComponent {
    val mainPanel = JPanel().apply {
      border = JBUI.Borders.empty(10)
      layout = BorderLayout()
    }

    val repositoryForm = panel {
      nameProperty.afterChange { currentName = it }

      row {
        val cb = checkBox(message("python.packaging.repository.form.enabled"))
          .bindSelected(enabledProperty)
        cb.component.isEnabled = !isInvalidProperty.get()
        isInvalidProperty.afterChange { cb.component.isEnabled = !it }
      }

      row(message("python.packaging.repository.form.name")) {
        textField()
          .align(AlignX.FILL)
          .bindText(nameProperty)
          .enabled(!isDefault)
      }
      nameErrorRow = row {
        label(message("python.packaging.repository.duplicate.name.error"))
          .applyToComponent { foreground = com.intellij.ui.JBColor.RED }
      }.visible(presenter.computeViewState(nameProperty.get(), urlProperty.get()).nameError)

      row(message("python.packaging.repository.form.url")) {
        cell(JBTextField(repository.repositoryUrl))
          .align(AlignX.FILL)
          .bindText(urlProperty)
          .enabled(!isDefault)
      }
      urlErrorRow = row {
        label(message("python.packaging.repository.form.auth.failed.hint"))
          .applyToComponent {
            urlErrorLabel = this
            foreground = com.intellij.ui.JBColor.RED
          }
      }.visible(presenter.computeViewState(nameProperty.get(), urlProperty.get()).urlError)

      row(message("python.packaging.repository.form.authorization")) {
        segmentedButton(PyPackageRepositoryAuthenticationType.entries) { text = it.text }
          .bind(authorizationTypeProperty)
          .enabled(!isDefault)
      }
      val row1 = row(message("python.packaging.repository.form.login")) {
        cell(JBTextField(repository.login))
          .apply { component.preferredSize = Dimension(250, component.preferredSize.height) }
          .bindText(loginProperty)
          .enabled(!isDefault)
      }.visible(repository.authorizationType.requiresCredentials)
      val row2 = row(message("python.packaging.repository.form.password")) {
        passwordField()
          .apply { component.preferredSize = Dimension(250, component.preferredSize.height) }
          .bindText(passwordProperty)
          .enabled(!isDefault)
      }.visible(repository.authorizationType.requiresCredentials)
      authorizationTypeProperty.afterChange {
        val needsAuth = authorizationTypeProperty.get().requiresCredentials
        row1.visible(needsAuth)
        row2.visible(needsAuth)
        if (needsAuth && password == null) loadPasswordAsync()
      }
    }

    nameProperty.afterChange {
      nameErrorRow?.visible(presenter.computeViewState(nameProperty.get(), urlProperty.get()).nameError)
    }

    urlProperty.afterChange {
      urlErrorRow?.visible(presenter.computeViewState(nameProperty.get(), urlProperty.get()).urlError)
    }

    repositoryForm.border = JBUI.Borders.empty()
    mainPanel.add(repositoryForm, BorderLayout.CENTER)
    return mainPanel
  }

  internal fun hasErrors(): Boolean = presenter.hasErrors(nameProperty.get(), urlProperty.get())
}
