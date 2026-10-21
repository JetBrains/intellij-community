package com.intellij.ide.starter.driver.reused

import com.intellij.ide.starter.testContextCache.CachedProjectConfiguration
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.file.Files

class ReusedProjectConfigurationValidationTest {
  @Test
  fun `missing companion project is rejected`() {
    val error = assertThrows<IllegalStateException> {
      findReusedProjectConfiguration(MissingProjectReusedIdeCase::class.java)
    }

    assertTrue(error.message.orEmpty().contains("@" + ReusedProject::class.java.simpleName))
  }

  @Test
  fun `project field with invalid type is rejected`() {
    val error = assertThrows<IllegalStateException> {
      findReusedProjectConfiguration(InvalidProjectTypeReusedIdeCase::class.java)
    }

    assertTrue(error.message.orEmpty().contains("must be a val (or lazy val) of " + CachedProjectConfiguration::class.java.simpleName))
  }

  @Test
  fun `duplicate project fields in one class are rejected`() {
    val error = assertThrows<IllegalStateException> {
      findReusedProjectConfiguration(DuplicateProjectFieldsReusedIdeCase::class.java)
    }

    assertTrue(error.message.orEmpty().contains("at most one companion property annotated with @" + ReusedProject::class.java.simpleName))
  }
}

@ReusedRun
private abstract class MissingProjectReusedIdeCase

@ReusedRun
private abstract class InvalidProjectTypeReusedIdeCase {
  companion object {
    @ReusedProject
    private val project = "invalid-project-type"
  }
}

@ReusedRun
private abstract class DuplicateProjectFieldsReusedIdeCase {
  @Suppress("unused")
  companion object {
    @ReusedProject
    val firstProject = testConfiguration("duplicate-project-fields-1")

    @ReusedProject
    val secondProject = testConfiguration("duplicate-project-fields-2")
  }
}

private fun testConfiguration(projectName: String): CachedProjectConfiguration {
  return cachedProjectConfiguration(Files.createTempDirectory(projectName), useRelease = true)
}
