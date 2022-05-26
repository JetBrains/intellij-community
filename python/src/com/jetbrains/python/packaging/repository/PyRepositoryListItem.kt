// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.repository

import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.ui.NamedConfigurable
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.util.ui.JBUI
import com.jetbrains.python.PyBundle.message
import org.jetbrains.annotations.ApiStatus
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel

@ApiStatus.Experimental
class PyRepositoryListItem(val repository: PyPackageRepository) : NamedConfigurable<PyPackageRepository>(true, null) {
  @NlsSafe
  private var currentName = repository.name!!
  private var password = repository.getPassword()

  private val propertyGraph = PropertyGraph()
  private val urlProperty = propertyGraph.graphProperty { repository.repositoryUrl ?: "" }
  private val loginProperty = propertyGraph.graphProperty { repository.login ?: "" }
  private val passwordProperty = propertyGraph.graphProperty { repository.getPassword() ?: "" }
  private val authorizationTypeProperty = propertyGraph.graphProperty { repository.authorizationType }

  override fun getDisplayName(): String {
    return currentName
  }

  override fun isModified(): Boolean {
    return currentName != repository.name
           || repository.repositoryUrl != urlProperty.get()
           || repository.authorizationType != authorizationTypeProperty.get()
           || password != passwordProperty.get()

  }

  override fun apply() {
    if (currentName != repository.name) {
      repository.clearCredentials()
    }

    repository.name = currentName
    val newUrl = urlProperty.get().trim()
    repository.repositoryUrl = if (newUrl.endsWith("/")) newUrl else "$newUrl/"

    repository.authorizationType = authorizationTypeProperty.get()
    if (repository.authorizationType == PyPackageRepositoryAuthenticationType.HTTP) {
      repository.login = loginProperty.get()
      repository.setPassword(passwordProperty.get())
    }
    else {
      repository.login = ""
      repository.clearCredentials()
    }
  }

  override fun setDisplayName(name: String) {
    currentName = name
  }

  override fun getEditableObject(): PyPackageRepository {
    return repository
  }

  @Suppress("DialogTitleCapitalization")
  override fun getBannerSlogan(): String {
    return displayName
  }

  override fun createOptionsPanel(): JComponent {
    val mainPanel = JPanel().apply {
      border = JBUI.Borders.empty(10)
      layout = BorderLayout()
    }

    val repositoryForm = panel {
      row(message("python.packaging.repository.form.url")) {
        cell(JBTextField(repository.repositoryUrl))
          .horizontalAlign(HorizontalAlign.FILL)
          .bindText(urlProperty)
      }
      row(message("python.packaging.repository.form.authorization")) {
        segmentedButton(PyPackageRepositoryAuthenticationType.values().toList(), PyPackageRepositoryAuthenticationType::text)
          .bind(authorizationTypeProperty)
      }
      val row1 = row(message("python.packaging.repository.form.login")) {
        cell(JBTextField(repository.login)).apply { component.preferredSize = Dimension(250, component.preferredSize.height) }
          .bindText(loginProperty)
      }.visible(repository.authorizationType != PyPackageRepositoryAuthenticationType.NONE)
      val row2 = row(message("python.packaging.repository.form.password")) {
        cell(JBPasswordField().apply { text = repository.getPassword() })
          .apply { component.preferredSize = Dimension(250, component.preferredSize.height) }
          .bindText(passwordProperty)
      }.visible(repository.authorizationType != PyPackageRepositoryAuthenticationType.NONE)
      authorizationTypeProperty.afterChange {
        val state = authorizationTypeProperty.get()
        row1.visible(state != PyPackageRepositoryAuthenticationType.NONE)
        row2.visible(state != PyPackageRepositoryAuthenticationType.NONE)
      }
    }

    mainPanel.add(repositoryForm, BorderLayout.CENTER)
    return mainPanel
  }
}