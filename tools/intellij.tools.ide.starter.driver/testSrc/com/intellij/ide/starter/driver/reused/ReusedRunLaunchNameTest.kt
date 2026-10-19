package com.intellij.ide.starter.driver.reused

import com.intellij.driver.client.Driver
import com.intellij.ide.starter.driver.engine.BackgroundRun
import com.intellij.ide.starter.utils.hyphenateTestName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.lang.reflect.Method
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class ReusedRunLaunchNameTest {
  @Test
  fun `launch name is hyphenated from display name`() {
    assertEquals("1", "1".hyphenateTestName())
    assertEquals("custom-display-name", "custom display name".hyphenateTestName())
  }

  @Test
  fun `reused run display names hide injected parameters`() {
    val generator = ReusedDisplayNameGenerator()
    val driverMethod = displayNameSampleMethod(Driver::class.java)
    val backgroundRunAndDriverMethod = displayNameSampleMethod(BackgroundRun::class.java, Driver::class.java)
    val stringAndDriverMethod = displayNameSampleMethod(String::class.java, Driver::class.java)

    assertEquals(
      ReusedDisplayNameGenerator::class.java,
      ReusedRun::class.java.getAnnotation(DisplayNameGeneration::class.java).value.java,
    )
    assertEquals(
      "display name sample",
      generator.generateDisplayNameForMethod(emptyList(), javaClass, driverMethod),
    )
    assertEquals(
      "display name sample",
      generator.generateDisplayNameForMethod(emptyList(), javaClass, backgroundRunAndDriverMethod),
    )
    assertEquals(
      "display name sample(String)",
      generator.generateDisplayNameForMethod(emptyList(), javaClass, stringAndDriverMethod),
    )
  }

  @Test
  fun `reused run display names hide the parameters that the session strategy injects`() {
    val generator = ReusedDisplayNameGenerator()
    val stringAndDriverMethod = displayNameSampleMethod(String::class.java, Driver::class.java)

    assertEquals(
      "display name sample",
      generator.generateDisplayNameForMethod(emptyList(), StringInjectingSample::class.java, stringAndDriverMethod),
    )
  }

  @Test
  fun `the IDE run timeout covers one start and every test of the class`() {
    assertEquals(10.minutes + 1.hours + 5.minutes, ideRunTimeout(ClassTimeoutSample::class.java))
    assertEquals(10.minutes + 10.minutes + 10.minutes, ideRunTimeout(NoTimeoutSample::class.java))
  }

  private fun displayNameSampleMethod(vararg parameterTypes: Class<*>): Method =
    javaClass.getDeclaredMethod("display name sample", *parameterTypes)

  @Suppress("unused")
  private fun `display name sample`(driver: Driver) {
  }

  @Suppress("unused")
  private fun `display name sample`(bgRun: BackgroundRun, driver: Driver) {
  }

  @Suppress("unused")
  private fun `display name sample`(name: String, driver: Driver) {
  }
}

private class StringInjectingStrategy : ReusedSessionStrategy {
  override fun injectedParameterTypes(): Set<Class<*>> = super.injectedParameterTypes() + String::class.java
}

@ReusedRun(sessionStrategy = StringInjectingStrategy::class)
private abstract class StringInjectingSample

// The samples are abstract, so JUnit does not run their tests.
@Suppress("unused")
@Timeout(value = 1, unit = TimeUnit.HOURS)
private abstract class ClassTimeoutSample {
  @Test
  fun `takes the class timeout`() {
  }

  @Test
  @Timeout(value = 5, unit = TimeUnit.MINUTES)
  fun `takes its own timeout`() {
  }

  fun `is not a test`() {
  }
}

@Suppress("unused")
private abstract class NoTimeoutSample {
  @Test
  fun first() {
  }

  @ParameterizedTest
  @ValueSource(ints = [1, 2])
  fun second(value: Int) {
  }
}
