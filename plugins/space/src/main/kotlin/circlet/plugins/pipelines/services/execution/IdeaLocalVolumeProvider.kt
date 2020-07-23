package circlet.plugins.pipelines.services.execution

import circlet.pipelines.provider.io.CommonFile
import circlet.pipelines.provider.local.DockerInDockerPaths
import circlet.pipelines.provider.local.LocalVolumeProvider
import java.nio.file.Path

val ideaVolumeName = "idea"

class IdeaLocalVolumeProvider(val workingDir: Path, automationDataBasePath: DockerInDockerPaths) : LocalVolumeProvider(
  automationDataBasePath) {

  override fun volumeLocation(volumeName: String): CommonFile {
    if (ideaVolumeName == volumeName) {
      return CommonFile(workingDir)
    }
    else {
      error("unknown volume $volumeName")
    }
  }

}

