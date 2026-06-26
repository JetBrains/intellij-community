package com.intellij.python.terminal.shared
 
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.terminal.startup.MutableShellExecOptions
import org.jetbrains.plugins.terminal.startup.ShellExecOptionsCustomizer
import java.nio.file.Path

internal class PyVirtualEnvTerminalCustomizerShared : ShellExecOptionsCustomizer {
  override fun customizeExecOptions(
    project: Project,
    shellExecOptions: MutableShellExecOptions,
  ) {
  }

  override fun getDefaultStartWorkingDirectory(project: Project): Path? {
    // despite documentation, this method actually executes on the front end, before collecting 
    // the path and sending it to the backend, where it is exposed by [MutableShellExecOptions].
    // in order for the correct venv directory to be selected when a terminal opens, we return
    // the latest value collected by the [PyTerminalCurrentVenvPathTopicService], which collects
    // the path to the venv of the currently selected file from the backend.
    // this logic cannot exist on the backend, since it won't be present on the frontend when
    // the custom default path is actually calculated.
    // context: https://jetbrains.slack.com/archives/C02G3PEGSKZ/p1782294653257889
    return getCurrentVenvPath(project)
  }
}
