package com.jetbrains.python.tests

import com.intellij.execution.Platform
import com.jetbrains.python.pathValidation.PlatformAndRoot
import com.jetbrains.python.pathValidation.ValidationRequest
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ValidationRequestTest {

  @Test
  fun testAbsolutePath() {
    if (PlatformAndRoot.local.platform == Platform.UNIX) {
      assertNull(ValidationRequest("/", "", PlatformAndRoot.local, null).validate { null },
                 "No error must be returned")
      assertNotNull(ValidationRequest("abc", "", PlatformAndRoot.local, null).validate { null },
                    "Path not absolute, but no error returned")
    }
    else {
      assertNull(ValidationRequest("\\\\wsl$\\asdad", "", PlatformAndRoot.local, null).validate { null },
                 "No error must be returned")
      assertNull(ValidationRequest("c:\\", "", PlatformAndRoot.local, null).validate { null },
                 "No error must be returned")
      assertNotNull(ValidationRequest("abc", "", PlatformAndRoot.local, null).validate { null },
                    "Path not absolute, but no error returned")
    }
  }
}