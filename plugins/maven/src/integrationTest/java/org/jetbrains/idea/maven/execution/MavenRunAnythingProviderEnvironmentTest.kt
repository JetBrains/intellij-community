// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution

import com.intellij.openapi.actionSystem.DataContext
import org.jetbrains.idea.maven.execution.MavenRunAnythingProvider.Companion.splitEnvironment
import org.jetbrains.idea.maven.execution.MavenRunAnythingProvider.CommandWithEnvironment
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MavenRunAnythingProviderEnvironmentTest {
  private val ticketLine = "CUCUMBERFILTERTAGS=\"@smoke and @sample\" mvn clean test"

  @Test
  fun `environment variables are split from the command`() {
    assertEquals(CommandWithEnvironment(mapOf("CUCUMBERFILTERTAGS" to "@smoke and @sample"), "mvn clean test"), splitEnvironment(ticketLine))
  }

  @Test
  fun `environment value with shell expansion is not split`() {
    for (line in listOf($$"FOO=$HOME mvn test", $$"FOO=\"$HOME\" mvn test", "FOO=`id` mvn test", "FOO=~/jdk mvn test")) {
      assertNull(splitEnvironment(line), line)
    }
  }

  @Test
  fun `environment value with literal shell characters is split`() {
    assertEquals(CommandWithEnvironment(mapOf("JAVA_HOME" to "C:\\jdk", "OPTS" to "-Da=b|c (d) <e>"), "mvn test"),
                 splitEnvironment("JAVA_HOME=C:\\jdk OPTS=\"-Da=b|c (d) <e>\" mvn test"))
  }

  @Test
  fun `provider matches the full mvn line with environment variables`() {
    val provider = MavenRunAnythingProvider()
    assertEquals(ticketLine, provider.findMatchingValue(DataContext.EMPTY_CONTEXT, ticketLine))
    assertEquals("FOO=1 mvn", provider.findMatchingValue(DataContext.EMPTY_CONTEXT, "FOO=1 mvn"))
    assertEquals("mvn clean test", provider.findMatchingValue(DataContext.EMPTY_CONTEXT, "mvn clean test"))
  }

  @Test
  fun `provider does not match a line that is not an mvn command`() {
    val provider = MavenRunAnythingProvider()
    for (line in listOf("FOO=1", "FOO=1 echo x", "FOO=1 mvnw test", "FOO=1 BAR mvn test", "FOO=\"a mvn test")) {
      assertNull(provider.findMatchingValue(DataContext.EMPTY_CONTEXT, line), line)
    }
  }
}
