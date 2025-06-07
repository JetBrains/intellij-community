// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project

import com.intellij.maven.testFramework.MavenTestCase
import com.intellij.openapi.command.impl.DummyProject
import com.intellij.ui.TextFieldWithHistory
import com.intellij.util.ReflectionUtil
import junit.framework.TestCase
import org.jetbrains.idea.maven.project.BundledMaven3.title
import org.jetbrains.idea.maven.server.MavenDistributionsCache
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class MavenEnvironmentFormTest : MavenTestCase() {
  @Test
  fun shouldNotShowDuplicatedBundledMavenHome() {
    val panel = MavenGeneralPanel()

    assertThat(panel) { t ->
      assertContainsElements(
        t!!.history,
        setOf(title)
      )
    }

    assertThat(panel) { t ->
      assertDoesntContain(
        t!!.history,
        MavenDistributionsCache.resolveEmbeddedMavenHome().mavenHome.toAbsolutePath().toString()
      )
    }
  }

  @Test
  fun shouldSetBundledMavenIfSetAbsolutePath() {
    val settings = MavenGeneralSettings()
    val panel = MavenGeneralPanel()
    settings.mavenHomeType = BundledMaven3
    panel.initializeFormData(settings, DummyProject.getInstance())
    assertThat(panel) { t: TextFieldWithHistory? ->
      TestCase.assertEquals("Absolute path to bundled maven should resolve to bundle", title, t!!.text)
    }
  }

  @Test
  fun shouldNotSetBundledMavenIfAnotherMavenSet() {
    val settings = MavenGeneralSettings()
    val panel = MavenGeneralPanel()
    settings.mavenHomeType = MavenInSpecificPath("/path/to/maven/home")
    panel.initializeFormData(settings, DummyProject.getInstance())
    assertThat(panel) { t: TextFieldWithHistory? -> TestCase.assertEquals("/path/to/maven/home", t!!.text) }
  }

  private fun assertThat(
    configurable: MavenGeneralPanel,
    checker: (TextFieldWithHistory?) -> Unit,
  ) {
    val form = getValue(MavenEnvironmentForm::class.java, configurable, "mavenPathsForm")
    val mavenHomeField = getValue(TextFieldWithHistory::class.java, form, "mavenHomeField")
    checker(mavenHomeField)
  }

  protected fun <T> getValue(fieldClass: Class<T>, o: Any?, name: String): T? {
    try {
      val field = ReflectionUtil.findAssignableField(o!!.javaClass, fieldClass, name)
      return ReflectionUtil.getFieldValue<T>(field, o)
    }
    catch (_: NoSuchFieldException) {
      fail("No such field $name in $o")
      return null
    }
  }
}