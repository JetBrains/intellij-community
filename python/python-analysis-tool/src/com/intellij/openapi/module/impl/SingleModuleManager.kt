package com.intellij.openapi.module.impl

import com.intellij.core.CoreModule
import com.intellij.openapi.module.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleFileIndex
import com.intellij.util.graph.Graph
import java.io.FileNotFoundException
import java.util.*

class SingleModuleManager(private val myProject: Project): ModuleManager(), ModifiableModuleModel {
  private var myModule: CoreModule? = null

  override fun newModule(filePath: String, moduleTypeId: String): Module {
    if (myModule == null) {
      myModule = object: CoreModule(myProject, myProject, filePath) {
        init {
          registerService(ModuleFileIndex::class.java, createModuleFileIndex())
        }
      }
    }
    return myModule!!
  }

  override fun loadModule(filePath: String): Module = myModule ?: throw FileNotFoundException()

  override fun disposeModule(module: Module) {
    myModule?.dispose()
  }

  override fun getModules(): Array<Module> = myModule?.let { arrayOf<Module>(it) } ?: emptyArray()

  override fun findModuleByName(name: String): Module? = if (name == myModule?.name) myModule else null

  override fun getSortedModules(): Array<Module> = myModule?.let { arrayOf<Module>(it) } ?: emptyArray()

  override fun moduleDependencyComparator(): Comparator<Module> = throw UnsupportedOperationException()

  override fun getModuleDependentModules(module: Module): MutableList<Module> = mutableListOf()

  override fun isModuleDependent(module: Module, onModule: Module): Boolean = false

  override fun moduleGraph(): Graph<Module> = moduleGraph(true)

  override fun moduleGraph(includeTests: Boolean): Graph<Module> = throw UnsupportedOperationException()

  override fun getModifiableModel(): ModifiableModuleModel = this

  override fun getModuleGroupPath(module: Module): Array<String>? = null

  override fun hasModuleGroups(): Boolean = false

  override fun getAllModuleDescriptions(): MutableCollection<ModuleDescription> = mutableListOf()

  override fun getUnloadedModuleDescriptions(): MutableCollection<UnloadedModuleDescription> = mutableListOf()

  override fun getUnloadedModuleDescription(moduleName: String): UnloadedModuleDescription? = throw UnsupportedOperationException()

  override fun getModuleGrouper(model: ModifiableModuleModel?): ModuleGrouper = throw UnsupportedOperationException()

  override fun setUnloadedModules(unloadedModuleNames: MutableList<String>) {
  }

  override fun newModule(filePath: String, moduleTypeId: String, options: MutableMap<String, String>?): Module = newModule(filePath, moduleTypeId)

  override fun dispose() {
  }

  override fun isChanged(): Boolean = false

  override fun commit() {
  }

  override fun renameModule(module: Module, newName: String) {
  }

  override fun getModuleToBeRenamed(newName: String): Module? = null

  override fun getNewName(module: Module): String? = null

  override fun getActualName(module: Module): String = myModule?.name ?: ""

  override fun setModuleGroupPath(module: Module, groupPath: Array<out String>?) {
  }

  override fun getProject(): Project = myProject
}