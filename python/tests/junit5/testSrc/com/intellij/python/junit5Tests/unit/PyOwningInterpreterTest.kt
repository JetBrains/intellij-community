// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.intellij.testFramework.common.timeoutRunBlocking
import com.jetbrains.python.sdk.configuration.owningInterpreter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.writeText

/**
 * Which interpreter owns a tool's executable.
 *
 * This decides where an upgrade installs. Getting it wrong is silent: pip installs a newer copy into some other
 * interpreter, the resolved executable is untouched, and the row reports an upgrade that changed nothing the IDE
 * runs. So an owner that cannot be identified must come back as `null` — the caller then falls back — rather than as
 * a plausible-looking guess.
 */
class PyOwningInterpreterTest {
  @Test
  fun `a tool in a virtual environment is owned by the interpreter beside it`(@TempDir dir: Path): Unit = timeoutRunBlocking {
    // The layout pip-into-a-venv produces, and the one /tmp/uvpiptest/bin/uv has: a binary with no shebang.
    val bin = (dir / "bin").createDirectories()
    (dir / "pyvenv.cfg").writeText("home = /usr/bin\n")
    val python = executable(bin / "python3")
    val tool = executable(bin / "uv")

    assertThat(owningInterpreter(tool)).isEqualTo(python)
  }

  @Test
  fun `a console script is owned by the interpreter in its shebang`(@TempDir dir: Path): Unit = timeoutRunBlocking {
    // What `pip install --user` writes: a launcher in a shared scripts dir with no interpreter next to it.
    val python = executable(dir / "python3")
    val scripts = (dir / "bin").createDirectories()
    val tool = executable(scripts / "pipenv", "#!$python\nprint('hi')\n")

    assertThat(owningInterpreter(tool)).isEqualTo(python)
  }

  /** `~/.local/bin/pipenv` on a real machine is `#!/bin/sh`. Handing a shell to the pip helper is not an upgrade. */
  @Test
  fun `a shell wrapper owns nothing`(@TempDir dir: Path): Unit = timeoutRunBlocking {
    val shell = executable(dir / "sh")
    val tool = executable(dir / "pipenv", "#!$shell\nexec something\n")

    assertThat(owningInterpreter(tool)).isNull()
  }

  /** `#!/usr/bin/env python3` names no interpreter to install into. */
  @Test
  fun `an env shebang owns nothing`(@TempDir dir: Path): Unit = timeoutRunBlocking {
    val tool = executable(dir / "black", "#!/usr/bin/env python3\n")

    assertThat(owningInterpreter(tool)).isNull()
  }

  /** A standalone binary outside any environment, as `~/.local/bin/uv` is: nothing pip owns. */
  @Test
  fun `a binary outside an environment owns nothing`(@TempDir dir: Path): Unit = timeoutRunBlocking {
    val bin = (dir / "bin").createDirectories()
    executable(bin / "python3")
    val tool = executable(bin / "uv")

    // An interpreter sits beside it, but without a pyvenv.cfg this is a shared directory, not an environment.
    assertThat(owningInterpreter(tool)).isNull()
  }

  private fun executable(path: Path, content: String = "binary"): Path {
    path.writeText(content)
    path.toFile().setExecutable(true)
    return path
  }
}
