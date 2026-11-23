// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.intellij.idea.TestFor
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityStateListener
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.jetbrains.python.run.PyCommonOptionsForm
import com.intellij.python.junit5Tests.framework.env.pyMockInterpreterFixture
import com.intellij.python.junit5Tests.framework.env.pyMockInterpreterWithoutModuleFixture
import com.intellij.python.junit5Tests.framework.pyProjectFixture
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.common.PyInterpreterItem
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.pathInProjectFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.util.ui.UIUtil
import com.jetbrains.python.run.PyCommonOptionsFormData
import com.jetbrains.python.sdk.pyProjectInterpretersIfReady
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JComboBox
import kotlin.io.path.Path
import kotlin.time.Duration.Companion.milliseconds

/**
 * [PyCommonOptionsForm] lists the interpreters of the current snapshots of the Python projects and of the registry. It
 * starts no modal progress when it is built and when it reads the list again.
 */
@TestApplication
internal class PyCommonOptionsFormTest {
  private val projectFixture = projectFixture()
  private val pyProjectFixture = projectFixture.pyProjectFixture()
  private val interpreterFixture = pyProjectFixture.pyMockInterpreterFixture(tempPathFixture())

  // No Python project of the test owns this interpreter, so no snapshot of the form holds it.
  private val otherInterpreterFixture = projectFixture.pyMockInterpreterWithoutModuleFixture(tempPathFixture(), tempPathFixture())

  @Test
  @TestFor(issues = ["PY-92937"])
  fun `the form lists a registered interpreter without a modal progress`(@TestDisposable disposable: Disposable): Unit =
    timeoutRunBlocking {
      val project = projectFixture.get()
      val module = pyProjectFixture.get().residesOnModule
      val interpreter = interpreterFixture.get()
      // The snapshot of the Python projects follows the module asynchronously. The form reads it and does not wait.
      while (interpreter !in pyProjectInterpretersIfReady(project, null)) {
        delay(50.milliseconds)
      }

      val modalEntries = AtomicInteger()
      val modalityListener = object : ModalityStateListener {
        override fun beforeModalityStateChanged(entering: Boolean, modalEntity: Any) {
          if (entering) modalEntries.incrementAndGet()
        }
      }
      ApplicationManager.getApplication().messageBus.connect(disposable).subscribe(ModalityStateListener.TOPIC, modalityListener)
      withContext(Dispatchers.EDT) {
        val form = PyCommonOptionsForm(formData(project, listOf(module)))
        assertThat(form.offers(interpreter)).isTrue()
        form.updateSdkList(true)
        assertThat(form.offers(interpreter)).isTrue()
      }

      assertThat(modalEntries.get()).isZero()
    }

  @Test
  @TestFor(issues = ["PY-92937"])
  fun `the form keeps a saved interpreter that the snapshot does not hold`(): Unit = timeoutRunBlocking {
    val project = projectFixture.get()
    val module = pyProjectFixture.get().residesOnModule
    val interpreter = otherInterpreterFixture.get()
    withContext(Dispatchers.EDT) {
      val form = PyCommonOptionsForm(formData(project, listOf(module)))
      assertThat(form.offers(interpreter)).isFalse()
      form.pythonInterpreter = interpreter
      form.isUseModuleSdk = false
      assertThat(form.isUseModuleSdk).isFalse()
      assertThat(form.pythonInterpreter).isEqualTo(interpreter)
    }
  }

  @Test
  @TestFor(issues = ["PY-92937"])
  fun `the row of a saved interpreter goes with a module change and with a null interpreter`(): Unit = timeoutRunBlocking {
    val project = projectFixture.get()
    val module = pyProjectFixture.get().residesOnModule
    val interpreter = otherInterpreterFixture.get()
    withContext(Dispatchers.EDT) {
      val form = PyCommonOptionsForm(formData(project, listOf(module)))
      form.pythonInterpreter = interpreter
      assertThat(form.offers(interpreter)).isTrue()
      // The module combo reads the list again after a module change.
      form.updateSdkList(true)
      assertThat(form.offers(interpreter)).isFalse()

      form.pythonInterpreter = interpreter
      assertThat(form.offers(interpreter)).isTrue()
      form.pythonInterpreter = null
      assertThat(form.offers(interpreter)).isFalse()
    }
  }

  /** Whether the interpreter combo of the form holds a row for [interpreter]. */
  private fun PyCommonOptionsForm.offers(interpreter: PythonInterpreter): Boolean =
    UIUtil.findComponentsOfType(mainPanel, JComboBox::class.java).any { combo ->
      (0 until combo.itemCount).any { (combo.getItemAt(it) as? PyInterpreterItem)?.ref == interpreter.ref }
    }
}

/**
 * A reset of [PyCommonOptionsForm] in a project with two modules sets the interpreter, then the module. The module
 * combo then reads a new snapshot, and the form keeps the saved interpreter.
 */
@TestApplication
internal class PyCommonOptionsFormResetTest {
  private val projectFixture = projectFixture()
  private val firstFixture = projectFixture.pyProjectFixture(projectFixture.pathInProjectFixture(Path("first")))
  private val secondFixture = projectFixture.pyProjectFixture(projectFixture.pathInProjectFixture(Path("second")))

  // No Python project of the test owns this interpreter, so no snapshot of the form holds it.
  private val otherInterpreterFixture = projectFixture.pyMockInterpreterWithoutModuleFixture(tempPathFixture(), tempPathFixture())

  @Test
  @TestFor(issues = ["PY-92937"])
  fun `a reset that selects another module keeps a saved interpreter that the snapshot does not hold`(): Unit = timeoutRunBlocking {
    val project = projectFixture.get()
    val first = firstFixture.get().residesOnModule
    val second = secondFixture.get().residesOnModule
    val interpreter = otherInterpreterFixture.get()
    withContext(Dispatchers.EDT) {
      val form = PyCommonOptionsForm(formData(project, listOf(first, second)))
      // The order of `AbstractPythonRunConfiguration.copyParams`.
      form.pythonInterpreter = interpreter
      form.setModule(second)
      form.isUseModuleSdk = false
      assertThat(form.isUseModuleSdk).isFalse()
      assertThat(form.pythonInterpreter).isEqualTo(interpreter)
    }
  }
}

private fun formData(project: Project, modules: List<Module>): PyCommonOptionsFormData = object : PyCommonOptionsFormData {
  override fun getProject(): Project = project
  override fun getValidModules(): List<Module> = modules
  override fun showConfigureInterpretersLink(): Boolean = false
}
