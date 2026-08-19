package com.jetbrains.python.tests

import com.intellij.execution.Platform
import com.jetbrains.python.pathValidation.PlatformAndRoot
import com.jetbrains.python.pathValidation.ValidationRequest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ValidationRequestTest {

  @Test
  fun testAbsolutePath() {
    if (PlatformAndRoot.local.platform == Platform.UNIX) {
      assertNull("No error must be returned",
                 ValidationRequest("/", "", PlatformAndRoot.local, null).validate { null })
      assertNotNull("Path not absolute, but no error returned",
                    ValidationRequest("abc", "", PlatformAndRoot.local, null).validate { null })
    }
    else {
      assertNull("No error must be returned",
                 ValidationRequest("\\\\wsl$\\asdad", "", PlatformAndRoot.local, null).validate { null })
      assertNull("No error must be returned",
                 ValidationRequest("c:\\", "", PlatformAndRoot.local, null).validate { null })
      assertNotNull("Path not absolute, but no error returned",
                    ValidationRequest("abc", "", PlatformAndRoot.local, null).validate { null })
    }
  }
}