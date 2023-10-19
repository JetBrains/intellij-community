package org.jetbrains.idea.maven.performancePlugin

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleManager.Companion.getInstance
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ModuleOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.impl.ModifiableModelCommitter
import com.intellij.openapi.roots.ui.configuration.actions.ModuleDeleteProvider
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.SmartList
import com.jetbrains.performancePlugin.commands.OpenFileCommand.Companion.findFile
import com.jetbrains.performancePlugin.commands.PerformanceCommandCoroutineAdapter
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsManager
import java.util.function.Consumer

class UnlinkMavenProjectCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {

  companion object {
    const val NAME = "unlinkMavenProject"
    const val PREFIX = "$CMD_PREFIX$NAME"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val project = context.project

    val filePath = extractCommandArgument(PREFIX)
    val projectPomFile = findFile(extractCommandArgument(PREFIX), project)

    if (projectPomFile == null) {
      throw IllegalArgumentException("File not found: $filePath")
    }

    val projectsManager = MavenProjectsManager.getInstance(project)

    val removableFiles: MutableList<VirtualFile> = ArrayList()
    val filesToUnIgnore: MutableList<String> = ArrayList()
    val modulesToRemove: MutableList<Module> = mutableListOf()

    val managedProject = projectsManager.findProject(projectPomFile)!!
    addModuleToRemoveList(projectsManager, modulesToRemove, managedProject)
    projectsManager.getModules(managedProject).forEach(Consumer { mp: MavenProject ->
      addModuleToRemoveList(projectsManager, modulesToRemove, mp)
      filesToUnIgnore.add(mp.file.path)
    })
    removableFiles.add(projectPomFile)
    filesToUnIgnore.add(projectPomFile.path)

    removeModules(getInstance(project), modulesToRemove)
    projectsManager.removeManagedFiles(removableFiles)
    projectsManager.removeIgnoredFilesPaths(filesToUnIgnore)
  }

  private fun addModuleToRemoveList(manager: MavenProjectsManager, modulesToRemove: MutableList<Module>, project: MavenProject) {
    val module = manager.findModule(project)
    if (module == null) {
      return
    }
    modulesToRemove.add(module)
  }

  private fun removeModules(moduleManager: ModuleManager, modulesToRemove: List<Module>) {
    WriteAction.run<RuntimeException> {
      val usingModels: MutableList<ModifiableRootModel> = SmartList()
      for (module in modulesToRemove) {
        val moduleRootManager = ModuleRootManager.getInstance(module!!)
        for (entry in moduleRootManager.orderEntries) {
          if (entry is ModuleOrderEntry) {
            usingModels.add(moduleRootManager.modifiableModel)
            break
          }
        }
      }

      val moduleModel = moduleManager.getModifiableModel()
      for (module in modulesToRemove) {
        ModuleDeleteProvider.removeModule(module!!, usingModels, moduleModel)
      }
      ModifiableModelCommitter.multiCommit(usingModels, moduleModel)
    }
  }

  override fun getName(): String {
    return NAME
  }
}