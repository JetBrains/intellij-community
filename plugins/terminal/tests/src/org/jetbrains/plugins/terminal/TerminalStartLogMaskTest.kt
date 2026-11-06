package org.jetbrains.plugins.terminal

import com.intellij.idea.TestFor
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry
import org.junit.jupiter.api.Test

@TestFor(classes = [LocalTerminalDirectRunner::class])
internal class TerminalStartLogMaskTest {
  @Test
  fun `masks a secret variable`() {
    val env = mapOf("IJ_MCP_AUTH_TOKEN" to "s3cret", "PATH" to "/usr/bin")

    assertThat(LocalTerminalDirectRunner.maskSecrets(env))
      .containsExactly(entry("IJ_MCP_AUTH_TOKEN", "***"), entry("PATH", "/usr/bin"))
  }

  @Test
  fun `matches the key in any case and position`() {
    val env = mapOf("my_token_var" to "a", "Secret" to "b", "DB_PASSWORD" to "c", "OPENAI_API_KEY" to "d", "TOKENIZER" to "e")

    assertThat(LocalTerminalDirectRunner.maskSecrets(env).values).containsOnly("***")
  }

  @Test
  fun `keeps the other variables and their order`() {
    val env = linkedMapOf("B" to "1", "A" to "2", "C" to "3")

    assertThat(LocalTerminalDirectRunner.maskSecrets(env)).containsExactlyEntriesOf(env)
  }

  @Test
  fun `masks the key and not the value`() {
    val env = mapOf("MESSAGE" to "the token is 42")

    assertThat(LocalTerminalDirectRunner.maskSecrets(env)).containsExactlyEntriesOf(env)
  }
}
