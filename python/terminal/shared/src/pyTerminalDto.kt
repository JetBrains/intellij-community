package com.intellij.python.terminal.shared

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.rpc.topics.ProjectRemoteTopic
import com.intellij.platform.rpc.topics.ProjectRemoteTopicListener
import com.intellij.platform.rpc.topics.sendToClient
import kotlinx.serialization.Serializable
import java.nio.file.Path
import kotlin.io.path.pathString

@Serializable
@JvmInline
value class PathDto private constructor(private val pathString: String) {
  fun asNioPathWithEelFs(eelDescriptor: EelDescriptor): Path =
    EelPath.parse(pathString, eelDescriptor).asNioPath()

  companion object {
    fun create(path: Path): PathDto {
      return PathDto(path.pathString)
    }
  }
}

private val PY_TERMINAL_CURRENT_VENV_PATH_TOPIC: ProjectRemoteTopic<PathDto> =
  ProjectRemoteTopic("PyTerminalCurrentVenvPathTopic", PathDto.serializer())

fun updateCurrentVenvPath(project: Project, path: Path) {
  PY_TERMINAL_CURRENT_VENV_PATH_TOPIC.sendToClient(project, PathDto.create(path))
}

internal class PyTerminalCurrentVenvPathTopicListener : ProjectRemoteTopicListener<PathDto> {
  override val topic: ProjectRemoteTopic<PathDto>
    get() = PY_TERMINAL_CURRENT_VENV_PATH_TOPIC

  override fun handleEvent(project: Project, event: PathDto) {
    project.service<PyTerminalCurrentVenvPathTopicService>().currentVenvPath = event
  }
}

@Service(Service.Level.PROJECT)
private class PyTerminalCurrentVenvPathTopicService {
  var currentVenvPath: PathDto? = null
}

fun getCurrentVenvPath(project: Project): Path? =
  project.service<PyTerminalCurrentVenvPathTopicService>()
    .currentVenvPath
    ?.asNioPathWithEelFs(project.getEelDescriptor())
