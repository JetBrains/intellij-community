package org.jetbrains.idea.maven.performancePlugin

import com.intellij.openapi.ui.playback.PlaybackContext
import com.jetbrains.performancePlugin.commands.PerformanceCommandCoroutineAdapter
import java.lang.Thread.sleep

class UnlinkGradleProjectCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val NAME = "unlinkGradleProject"
    const val PREFIX = "$CMD_PREFIX$NAME"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    sleep(15000)
    //val project = context.project
    //
    //val filePath = extractCommandArgument(PREFIX)
    //val projectPath = findFile(extractCommandArgument(PREFIX), project)
    //
    //if (projectPath == null) {
    //  throw IllegalArgumentException("File not found: $filePath")
    //}
    //val systemId = ProjectSystemId("GRADLE")
    //val dataProject = ExternalSystemApiUtil.findProjectNode(project, systemId, projectPath.path)!!.data
    //withContext(Dispatchers.EDT) {
    //  detachProject(project, dataProject.owner, dataProject, null)
    //}

    // ---- ---

    //val projectImportProvider = ProjectImportProvider.PROJECT_IMPORT_PROVIDER
    //  .findFirstSafe { it: ProjectImportProvider? ->
    //    it is AbstractExternalProjectImportProvider && "GRADLE" == it.externalSystemId.id
    //  }!!


    //val manager = ExternalSystemApiUtil.getManager(ProjectSystemId("GRADLE"))!!

    //ProjectDataManagerImpl.getInstance()

  }

  override fun getName(): String {
    return NAME
  }
}