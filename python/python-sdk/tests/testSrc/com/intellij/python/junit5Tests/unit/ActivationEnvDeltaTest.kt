package com.intellij.python.junit5Tests.unit

import com.jetbrains.python.run.activationEnvDelta
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for the activation env diff behind [readPythonEnvironment][com.jetbrains.python.run.readPythonEnvironment].
 * See PY-71917: the previous implementation kept only a fixed whitelist and dropped everything a conda
 * `activate.d` script exported.
 */
class ActivationEnvDeltaTest {

  @Test
  @DisplayName("PY-71917: a variable added by activation is kept, whatever its name")
  fun `variable added by activation is kept`() {
    val reference = mapOf("HOME" to "/home/user")
    val activated = reference + ("GDAL_DRIVER_PATH" to "/env/lib/gdalplugins")
    assertEquals(mapOf("GDAL_DRIVER_PATH" to "/env/lib/gdalplugins"), activationEnvDelta(reference, activated))
  }

  @Test
  fun `variable changed by activation is kept with its new value`() {
    val reference = mapOf("PATH" to "/usr/bin")
    val activated = mapOf("PATH" to "/env/bin:/usr/bin")
    assertEquals(mapOf("PATH" to "/env/bin:/usr/bin"), activationEnvDelta(reference, activated))
  }

  @Test
  fun `variable left untouched by activation is not leaked out of the reference shell`() {
    val reference = mapOf("HOME" to "/home/user", "LANG" to "en_US.UTF-8")
    assertEquals(emptyMap<String, String>(), activationEnvDelta(reference, reference))
  }

  @Test
  fun `variable present only in the reference shell is dropped`() {
    val reference = mapOf("ONLY_IN_REFERENCE" to "x")
    val activated = emptyMap<String, String>()
    assertEquals(emptyMap<String, String>(), activationEnvDelta(reference, activated))
  }

  @Test
  @DisplayName("shell bookkeeping that differs between the two reads is not mistaken for activation")
  fun `non-activation noise variables are dropped even when they differ`() {
    val reference = mapOf("PWD" to "/start", "OLDPWD" to "/prev", "SHLVL" to "1", "_" to "/usr/bin/env")
    val activated = mapOf("PWD" to "/env/bin", "OLDPWD" to "/start", "SHLVL" to "2", "_" to "/bin/activate")
    assertEquals(emptyMap<String, String>(), activationEnvDelta(reference, activated))
  }

  @Test
  fun `real activation is isolated from noise and unchanged variables in a mixed environment`() {
    val reference = mapOf(
      "HOME" to "/home/user",
      "PATH" to "/usr/bin",
      "PWD" to "/home/user",
    )
    val activated = mapOf(
      "HOME" to "/home/user",                       // unchanged -> dropped
      "PATH" to "/env/bin:/usr/bin",                // changed by activation -> kept
      "PWD" to "/env",                              // noise -> dropped
      "CONDA_PREFIX" to "/env",                     // added by activation -> kept
      "PROJ_DATA" to "/env/share/proj",             // added by activation -> kept
    )
    assertEquals(
      mapOf(
        "PATH" to "/env/bin:/usr/bin",
        "CONDA_PREFIX" to "/env",
        "PROJ_DATA" to "/env/share/proj",
      ),
      activationEnvDelta(reference, activated),
    )
  }

  @Test
  fun `empty environments yield an empty delta`() {
    assertEquals(emptyMap<String, String>(), activationEnvDelta(emptyMap(), emptyMap()))
  }
}
