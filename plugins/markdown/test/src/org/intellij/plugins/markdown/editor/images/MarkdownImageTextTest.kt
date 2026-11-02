// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.images

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.ui.UiInterceptors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.images.editor.ConfigureImageDialog
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@TestApplication
internal class MarkdownImageTextTest {
  @Test
  fun `image paths use forward slashes without a leading backslash`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val actual = withContext(Dispatchers.EDT) {
      var path: String? = null
      UiInterceptors.registerPossible(disposable, object : UiInterceptors.UiInterceptor<ConfigureImageDialog>(ConfigureImageDialog::class.java) {
        override fun doIntercept(dialog: ConfigureImageDialog) {
          val field = dialog.preferredFocusedComponent as TextFieldWithBrowseButton
          field.text = "\\Images\\MyImage.png"
          dialog.performOKAction()
        }
      })
      ConfigureImageDialog(null, "").show { path = it.path }
      path
    }
    assertEquals("Images/MyImage.png", actual)
  }
}
