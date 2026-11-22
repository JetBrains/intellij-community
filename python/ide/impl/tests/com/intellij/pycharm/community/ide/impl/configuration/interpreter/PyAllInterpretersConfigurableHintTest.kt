// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.configuration.interpreter

import com.intellij.idea.TestFor
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.Component
import java.awt.Container
import javax.swing.JEditorPane

@TestApplication
@TestFor(issues = ["PY-94313"])
@Subsystems.Interpreters
@Layers.Functional
class PyAllInterpretersConfigurableHintTest {
  private val project get() = projectFixture.get()

  @Test
  fun `the page of the default project has no hint link`(): Unit = timeoutRunBlocking {
    val links = hintLinks(ProjectManager.getInstance().defaultProject)

    assertThat(links)
      .describedAs("the default project has no tool windows and no Workspace Structure page")
      .doesNotContain(HINT_LINK_WORKSPACE, HINT_LINK_PACKAGES)
  }

  @Test
  fun `the page of a project links the structure settings and the packages tool window`(): Unit = timeoutRunBlocking {
    val links = hintLinks(project)

    assertThat(links).containsExactly(HINT_LINK_WORKSPACE, HINT_LINK_PACKAGES)
  }

  /** The platform creates the component of a configurable on the EDT. */
  private suspend fun hintLinks(project: Project): List<String> = withContext(Dispatchers.EDT) {
    val configurable = PyAllInterpretersConfigurable(project)
    try {
      editorPanes(configurable.createComponent())
        .flatMap { pane -> HREF.findAll(pane.text).map { it.groupValues[1] }.toList() }
    }
    finally {
      configurable.disposeUIResources()
    }
  }

  private fun editorPanes(component: Component): List<JEditorPane> = buildList {
    if (component is JEditorPane) add(component)
    if (component is Container) component.components.forEach { addAll(editorPanes(it)) }
  }

  companion object {
    private val projectFixture = projectFixture()
    private val HREF = Regex("""href="([^"]*)"""")
    private const val HINT_LINK_WORKSPACE = "workspace"
    private const val HINT_LINK_PACKAGES = "packages"
  }
}
