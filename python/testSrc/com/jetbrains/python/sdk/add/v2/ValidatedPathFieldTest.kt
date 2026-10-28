// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.openapi.application.EDT
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.ui.validation.DialogValidationRequestor
import com.intellij.platform.eel.provider.localEel
import com.intellij.python.pytools.backend.Version
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.UIUtil
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.errorProcessing.MessageError
import com.jetbrains.python.sdk.add.v2.PathHolderAndValidationResult.Error
import com.jetbrains.python.sdk.add.v2.PathHolderAndValidationResult.ErrorWithPath
import com.jetbrains.python.sdk.add.v2.PathHolderAndValidationResult.Success
import kotlinx.coroutines.Dispatchers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.awt.Component
import java.nio.file.Path
import javax.swing.JComponent
import javax.swing.JLabel

private const val MISSING_TOOL_TEXT = "No tool executable found"

@TestApplication
@Subsystems.Interpreters
@Layers.Functional
internal class ValidatedPathFieldTest {

  // PY-91785, PY-92987
  @ParameterizedTest
  @ValueSource(booleans = [true, false])
  fun `missing tool shows the warning`(canBeEdited: Boolean): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    val section = ToolSection(canBeEdited)

    section.setVerdict(Error(MessageError("not detected")))

    assertThat(section.isWarningVisible()).isTrue()
  }

  // PY-91785
  @Test
  fun `read-only field is hidden when the tool is missing`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    val section = ToolSection(canBeEdited = false)

    section.setVerdict(Error(MessageError("not detected")))

    assertThat(section.isFieldVisible()).isFalse()
  }

  @ParameterizedTest
  @ValueSource(booleans = [true, false])
  fun `found tool shows the field and no warning`(canBeEdited: Boolean): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    val section = ToolSection(canBeEdited)

    section.setVerdict(Success(Version("1.0"), TOOL_PATH))

    assertThat(section.isFieldVisible()).isTrue()
    assertThat(section.isWarningVisible()).isFalse()
  }

  @Test
  fun `read-only field stays when the version probe of a found tool fails`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    val section = ToolSection(canBeEdited = false)

    section.setVerdict(ErrorWithPath(MessageError("bad version"), TOOL_PATH))

    assertThat(section.isFieldVisible()).isTrue()
    assertThat(section.isWarningVisible()).isFalse()
  }

  private companion object {
    val TOOL_PATH = PathHolder.Eel(Path.of("/opt/tool/bin/tool"))
  }
}

/**
 * The tool path section of the Add Interpreter dialog, as [validatablePathField] builds it.
 */
private class ToolSection(canBeEdited: Boolean) {
  private val validator = FakeToolValidator()
  private lateinit var field: ValidatedPathField<Version, PathHolder.Eel, ValidatedPath.Executable<PathHolder.Eel>>
  private val panel = panel {
    field = validatablePathField(
      fileSystem = EelFileSystem(localEel),
      pathValidator = validator,
      validationRequestor = DialogValidationRequestor { _, _ -> },
      labelText = "Path to tool:",
      missingExecutableText = MISSING_TOOL_TEXT,
      canBeEdited = canBeEdited,
    )
  }

  fun setVerdict(verdict: PathHolderAndValidationResult<Version, PathHolder.Eel>) {
    validator.backProperty.set(ValidatedPath.Executable(verdict))
  }

  fun isFieldVisible(): Boolean = field.isVisibleIn(panel)

  fun isWarningVisible(): Boolean =
    UIUtil.findComponentsOfType(panel, JLabel::class.java).single { it.text.orEmpty().contains(MISSING_TOOL_TEXT) }.isVisibleIn(panel)

  private fun Component.isVisibleIn(root: JComponent): Boolean =
    generateSequence(this) { it.parent }.takeWhile { it !== root }.all { it.isVisible }
}

private class FakeToolValidator : PathValidator<Version, PathHolder.Eel, ValidatedPath.Executable<PathHolder.Eel>> {
  override val backProperty = AtomicProperty<ValidatedPath.Executable<PathHolder.Eel>?>(null)
  override val isDirtyValue = AtomicBooleanProperty(false)
  override val isValidationInProgress: Boolean = false
  override fun validate(input: String) = Unit
}
