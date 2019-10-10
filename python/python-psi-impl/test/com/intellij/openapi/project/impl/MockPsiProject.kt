package com.intellij.openapi.project.impl

import com.intellij.ide.plugins.ContainerDescriptor
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.serviceContainer.PlatformComponentManagerImpl

class MockPsiProject(application: Application, parentDisposable: Disposable = application) : PlatformComponentManagerImpl(application), Project {
  init {
    Disposer.register(parentDisposable, this)
    myPicoContainer.registerComponentInstance(Project::class.java, this)
  }

  constructor() : this(ApplicationManager.getApplication())

  override fun getContainerDescriptor(pluginDescriptor: IdeaPluginDescriptorImpl): ContainerDescriptor =
    pluginDescriptor.project

  override fun getName(): String = ""

  override fun getBaseDir(): VirtualFile? = null

  override fun getBasePath(): String? = null

  override fun getProjectFile(): VirtualFile? = null

  override fun getProjectFilePath(): String? = null

  override fun getWorkspaceFile(): VirtualFile? = null

  override fun getLocationHash(): String = "mockPsi"

  override fun save() {
  }

  override fun isOpen(): Boolean = false

  override fun isInitialized(): Boolean = true

  override fun isDefault(): Boolean = false
}
