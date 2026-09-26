@file:JvmName("ShShellDetection")

package com.intellij.sh.run

import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.toEelApiBlocking
import java.nio.file.Path

/**
 * Resolves the user's default login shell on the [EelDescriptor]'s target (local, WSL or Docker) by reading the system passwd database
 * (`getent passwd <uid>` / `/etc/passwd`) via [com.intellij.platform.eel.EelExecApi.getUserLoginShell].
 *
 * It deliberately does **not** spawn a login shell or fetch login-shell environment variables (e.g. via `fetchLoginShellEnvVariables`).
 * Spawning a login shell executes the user's startup scripts (`.bashrc`/`.profile`), which can block for an unbounded time.
 * F.e. on IJPL-229839 a slow `.bashrc` inside an environment blocked the background action update for the Project View context
 * menu, so the menu was never built and only a spinner was shown.
 * A passwd lookup is bounded and free from side effects.
 *
 * @return the nio [Path] to the shell executable on the host, mapped for this descriptor via [asNioPath].
 */
internal fun EelDescriptor.detectDefaultShell(): Path =
  runBlockingMaybeCancellable {
    toEelApiBlocking().exec.getUserLoginShell()
  }.asNioPath()
