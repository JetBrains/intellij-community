package circlet.plugins.pipelines.services.execution

import circlet.pipelines.provider.io.*
import circlet.pipelines.provider.local.*
import java.nio.file.*

val ideaVolumeName = "idea"

class IdeaLocalVolumeProvider(val workingDir: Path, automationDataBasePath: DockerInDockerPaths) : LocalVolumeProvider(automationDataBasePath) {

    override fun volumeLocation(volumeName: String): CommonFile {
        if (ideaVolumeName == volumeName) {
            return CommonFile(workingDir)
        } else {
            error("unknown volume $volumeName")
        }
    }

}

