// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.navigator

import com.intellij.execution.RunManagerListener
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.icons.AllIcons
import com.intellij.ide.util.treeView.TreeState
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ActionUtil.wrap
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.roots.ex.ProjectRootManagerEx.ProjectJdkListener
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.WriteExternalException
import com.intellij.openapi.wm.*
import com.intellij.openapi.wm.ex.ToolWindowManagerEx
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.AppUIUtil.invokeLaterIfProjectAlive
import com.intellij.ui.RemoteTransferUIManager.forceDirectTransfer
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.treeStructure.SimpleTree
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.containers.ContainerUtil
import icons.MavenIcons
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.idea.maven.MavenDisposable
import org.jetbrains.idea.maven.execution.MavenRunner
import org.jetbrains.idea.maven.execution.MavenRunnerSettings
import org.jetbrains.idea.maven.indices.IndexChangeProgressListener
import org.jetbrains.idea.maven.indices.MavenSystemIndicesManager
import org.jetbrains.idea.maven.navigator.structure.MavenProjectsNavigatorPanel
import org.jetbrains.idea.maven.navigator.structure.MavenProjectsStructure
import org.jetbrains.idea.maven.project.*
import org.jetbrains.idea.maven.server.MavenIndexUpdateState
import org.jetbrains.idea.maven.tasks.MavenShortcutsManager
import org.jetbrains.idea.maven.tasks.MavenTasksManager
import org.jetbrains.idea.maven.utils.MavenEelUtil.checkJdkAndShowNotification
import org.jetbrains.idea.maven.utils.MavenEelUtil.restartMavenConnectorsIfJdkIncorrect
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenSimpleProjectComponent
import org.jetbrains.idea.maven.utils.MavenUtil.invokeLater
import java.awt.Graphics
import javax.swing.JPanel
import javax.swing.JTextPane
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants
import javax.swing.tree.TreeSelectionModel

@State(name = "MavenProjectNavigator", storages = [Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE)], getStateRequiresEdt = true)
class MavenProjectsNavigator(project: Project) : MavenSimpleProjectComponent(
  project), PersistentStateComponent<MavenProjectsNavigatorState?>, Disposable {
  private var myState = MavenProjectsNavigatorState()

  private var myTree: SimpleTree? = null
  @TestOnly fun structureForTests(): MavenProjectsStructure? = myStructure

  private var myStructure: MavenProjectsStructure? = null

  init {
    project.getMessageBus()
      .connect(MavenDisposable.getInstance(project))
      .subscribe<MavenImportListener>(MavenImportListener.TOPIC, object : MavenImportListener {
        override fun importFinished(importedProjects: MutableCollection<MavenProject?>, newModules: MutableList<Module>) {
          scheduleStructureUpdate()
        }
      })
  }

  override fun getState(): MavenProjectsNavigatorState {
    ThreadingAssertions.assertEventDispatchThread()
    if (this.myStructure != null) {
      try {
        myState.treeState = Element("root")
        TreeState.createOn(myTree!!).writeExternal(myState.treeState)
      }
      catch (e: WriteExternalException) {
        MavenLog.LOG.warn(e)
      }
    }
    return myState
  }

  override fun loadState(state: MavenProjectsNavigatorState) {
    myState = state
    scheduleStructureUpdate()
  }

  var groupModules: Boolean
    get() = myState.groupStructurally
    set(value) {
      if (myState.groupStructurally != value) {
        myState.groupStructurally = value
        scheduleStructureUpdate()
      }
    }

  var showIgnored: Boolean
    get() = myState.showIgnored
    set(value) {
      if (myState.showIgnored != value) {
        myState.showIgnored = value
        scheduleStructureUpdate()
      }
    }

  var showBasicPhasesOnly: Boolean
    get() = myState.showBasicPhasesOnly
    set(value) {
      if (myState.showBasicPhasesOnly != value) {
        myState.showBasicPhasesOnly = value
        scheduleStructureUpdate()
      }
    }

  var alwaysShowArtifactId: Boolean
    get() = myState.alwaysShowArtifactId
    set(value) {
      if (myState.alwaysShowArtifactId != value) {
        myState.alwaysShowArtifactId = value
        scheduleStructureUpdate()
      }
    }

  var showVersions: Boolean
    get() = myState.showVersions
    set(value) {
      if (myState.showVersions != value) {
        myState.showVersions = value
        scheduleStructureUpdate()
      }
    }

  override fun initializeComponent() {
    if (isNormalProject()) {
      doInit()
    }
  }

  @TestOnly
  fun initForTests() {
    doInit()
    initTree()
    initStructure()
  }

  private fun doInit() {
    MavenProjectsManager.getInstance(myProject).addManagerListener(object : MavenProjectsManager.Listener {
      override fun activated() {
        invokeLaterIfProjectAlive(myProject, Runnable { initToolWindow() })
        listenForProjectsChanges()
        scheduleStructureUpdate()
      }
    })
  }

  override fun dispose() {
  }

  private fun listenForProjectsChanges() {
    MavenProjectsManager.getInstance(myProject).addProjectsTreeListener(MyProjectsListener(), this)

    MavenShortcutsManager.getInstance(myProject).addListener(MavenShortcutsManager.Listener {
      scheduleStructureRequest(
        Runnable { myStructure!!.updateGoals() })
    }, this)

    MavenTasksManager.getInstance(myProject).addListener(object : MavenTasksManager.Listener {
      override fun compileTasksChanged() {
        scheduleStructureRequest(Runnable { myStructure!!.updateGoals() })
      }
    }, this)

    MavenRunner.getInstance(myProject).getSettings().addListener(object : MavenRunnerSettings.Listener {
      override fun skipTestsChanged() {
        scheduleStructureRequest(Runnable { myStructure!!.updateGoals() })
      }
    }, this)

    myProject.getMessageBus().connect().subscribe<RunManagerListener>(RunManagerListener.TOPIC, object : RunManagerListener {
      fun changed() {
        scheduleStructureRequest(Runnable { myStructure!!.updateRunConfigurations() })
      }

      override fun runConfigurationAdded(settings: RunnerAndConfigurationSettings) {
        changed()
      }

      override fun runConfigurationRemoved(settings: RunnerAndConfigurationSettings) {
        changed()
      }

      override fun runConfigurationChanged(settings: RunnerAndConfigurationSettings) {
        changed()
      }

      override fun beforeRunTasksChanged() {
        scheduleStructureRequest(Runnable { myStructure!!.updateGoals() })
      }
    })

    ApplicationManager.getApplication().getMessageBus().connect(this).subscribe<IndexChangeProgressListener>(
      MavenSystemIndicesManager.TOPIC, object : IndexChangeProgressListener {
        override fun indexStatusChanged(state: MavenIndexUpdateState) {
          doScheduleStructureRequest(Runnable {
            myStructure!!.updateRepositoryStatus(state)
          })
        }
      })

    ProjectRootManagerEx.getInstanceEx(myProject).addProjectJdkListener(ProjectJdkListener {
      checkJdkAndShowNotification(myProject)
      restartMavenConnectorsIfJdkIncorrect(myProject)
    })

    StartupManager.getInstance(myProject).runAfterOpened(Runnable {
      DumbService.getInstance(myProject).runWhenSmart(Runnable {
        checkJdkAndShowNotification(myProject)
      })
    })
  }

  fun initToolWindow() {
    initTree()
    val toolWindowManager = ToolWindowManager.getInstance(myProject)
    toolWindowManager.invokeLater(Runnable { initializeToolWindow(toolWindowManager) })
  }

  private fun initializeToolWindow(toolWindowManager: ToolWindowManager) {
    val panel: JPanel = MavenProjectsNavigatorPanel(myProject, myTree)

    wrap("Maven.RemoveRunConfiguration").registerCustomShortcutSet(CommonShortcuts.getDelete(), myTree, this)
    wrap("Maven.EditRunConfiguration").registerCustomShortcutSet(CommonShortcuts.getEditSource(), myTree, this)

    val toolWindow = toolWindowManager.registerToolWindow(TOOL_WINDOW_ID) {
      icon = MavenIcons.ToolWindowMaven
      anchor = ToolWindowAnchor.RIGHT
      canCloseContent = false
      contentFactory = object : ToolWindowFactory {
        override fun createToolWindowContent(
          project: Project,
          toolWindow: ToolWindow
        ) {
        }
      }
    }

    val contentManager = toolWindow.getContentManager()
    Disposer.register(this, Disposable {
      // fire content removed events, so subscribers could clean up caches
      if (!myProject.isDisposed()) {
        contentManager.removeAllContents(true)
      }
      Disposer.dispose(contentManager)
      if (!myProject.isDisposed()) {
        toolWindow.remove()
      }
    })
    val contentFactory = ContentFactory.getInstance()
    val content = contentFactory.createContent(panel, "", false)
    contentManager.addContent(content)
    contentManager.setSelectedContent(content, false)

    myProject.getMessageBus().connect(content).subscribe<ToolWindowManagerListener>(ToolWindowManagerListener.TOPIC,
                                                                                    object : ToolWindowManagerListener {
                                                                                      var wasVisible: Boolean = false

                                                                                      override fun stateChanged(toolWindowManager: ToolWindowManager) {
                                                                                        if (toolWindow.isDisposed()) {
                                                                                          return
                                                                                        }

                                                                                        val visible = (toolWindowManager as ToolWindowManagerEx).shouldUpdateToolWindowContent(
                                                                                          toolWindow)
                                                                                        if (!visible || wasVisible) {
                                                                                          return
                                                                                        }
                                                                                        scheduleStructureUpdate()
                                                                                        wasVisible = true
                                                                                      }
                                                                                    })

    val actionManager = ActionManager.getInstance()

    val group = DefaultActionGroup()
    group.add(actionManager.getAction("Maven.GroupProjects"))
    group.add(actionManager.getAction("Maven.ShowIgnored"))
    group.add(actionManager.getAction("Maven.ShowBasicPhasesOnly"))
    group.add(actionManager.getAction("Maven.AlwaysShowArtifactId"))
    group.add(actionManager.getAction("Maven.ShowVersions"))

    toolWindow.setAdditionalGearActions(group)
    forceDirectTransfer(panel)
  }

  private fun initTree() {
    val mavenProjectManager = MavenProjectsManager.getInstance(myProject)
    myTree = object : SimpleTree() {
      private val myPane = JTextPane()

      init {
        myPane.setOpaque(false)
        val addIconText = "'+'"
        val refreshIconText = "'Reimport'"
        val message = MavenProjectBundle.message("maven.navigator.nothing.to.display", addIconText, refreshIconText)
        val firstEol = message.indexOf("\n")
        val addIconMarkerIndex = message.indexOf(addIconText)
        myPane.replaceSelection(message.take(addIconMarkerIndex))
        myPane.insertIcon(AllIcons.General.Add)
        val refreshIconMarkerIndex = message.indexOf(refreshIconText)
        myPane.replaceSelection(message.substring(addIconMarkerIndex + addIconText.length, refreshIconMarkerIndex))
        myPane.insertIcon(AllIcons.Actions.Refresh)
        myPane.replaceSelection(message.substring(refreshIconMarkerIndex + refreshIconText.length))

        val document = myPane.getStyledDocument()
        val centerAlignment = SimpleAttributeSet()
        StyleConstants.setAlignment(centerAlignment, StyleConstants.ALIGN_CENTER)
        val justifiedAlignment = SimpleAttributeSet()
        StyleConstants.setAlignment(justifiedAlignment, StyleConstants.ALIGN_JUSTIFIED)

        document.setParagraphAttributes(0, firstEol, centerAlignment, false)
        document.setParagraphAttributes(firstEol + 2, document.getLength(), justifiedAlignment, false)
      }

      override fun paintComponent(g: Graphics) {
        super.paintComponent(g)

        if (mavenProjectManager.hasProjects()) {
          return
        }

        myPane.setFont(getFont())
        myPane.setBackground(getBackground())
        myPane.setForeground(getForeground())
        val bounds = getBounds()
        myPane.setBounds(0, 0, bounds.width - 10, bounds.height)

        val g2 = g.create(bounds.x + 10, bounds.y + 20, bounds.width, bounds.height)
        try {
          myPane.paint(g2)
        }
        finally {
          g2.dispose()
        }
      }
    }
    myTree!!.getEmptyText().clear()

    myTree!!.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION)
  }

  fun selectInTree(project: MavenProject?) {
    scheduleStructureRequest(Runnable { myStructure!!.select(project) })
  }

  private fun scheduleStructureRequest(r: Runnable) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      if (null != this.myStructure) {
        r.run()
      }
    }
    else {
      doScheduleStructureRequest(r)
    }
  }

  private fun doScheduleStructureRequest(r: Runnable) {
    val toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(TOOL_WINDOW_ID)
    if (toolWindow == null) return

    invokeLater(myProject, Runnable {
      val files = MavenProjectsManager.getInstance(myProject).getState().originalFiles
      val hasMavenProjects = files != null && !files.isEmpty()
      if (toolWindow.isAvailable() != hasMavenProjects) {
        MavenLog.LOG.info("Set MavenToolWindow availability: $hasMavenProjects")
        toolWindow.setAvailable(hasMavenProjects)
        if (hasMavenProjects
            && myProject.getUserData<Boolean?>(ExternalSystemDataKeys.NEWLY_CREATED_PROJECT) == null
        ) {
          toolWindow.activate(null)
        }
      }

      val shouldCreate = this.myStructure == null
      if (shouldCreate) {
        initStructure()
      }

      r.run()
      if (shouldCreate) {
        TreeState.createFrom(myState.treeState).applyTo(myTree!!)
      }
    })
  }

  private fun initStructure() {
    this.myStructure = MavenProjectsStructure(myProject,
                                                    MavenProjectsStructure.MavenStructureDisplayMode.SHOW_ALL,
                                                    MavenProjectsManager.getInstance(myProject),
                                                    MavenTasksManager.getInstance(myProject),
                                                    MavenShortcutsManager.getInstance(myProject),
                                                    this,
                                                    myTree)
  }

  @ApiStatus.Internal
  fun scheduleStructureUpdate() {
    scheduleStructureRequest(Runnable { myStructure!!.update() })
  }

  private inner class MyProjectsListener : MavenProjectsTree.Listener {
    override fun projectsIgnoredStateChanged(
      ignored: List<MavenProject>,
      unignored: List<MavenProject>,
      fromImport: Boolean
    ) {
      scheduleStructureRequest(Runnable { myStructure!!.updateIgnored(ContainerUtil.concat<MavenProject?>(ignored, unignored)) })
    }

    override fun profilesChanged() {
      scheduleStructureRequest(Runnable { myStructure!!.updateProfiles() })
    }

    override fun projectsUpdated(updated: List<Pair<MavenProject, MavenProjectChanges>>, deleted: List<MavenProject>)  {
      scheduleUpdateProjects(updated.map { it.first }, ArrayList(deleted))
    }

    override fun projectResolved(projectWithChanges: Pair<MavenProject, MavenProjectChanges>) {
      scheduleUpdateProjects(listOf(projectWithChanges.first), emptyList())
    }

    override fun pluginsResolved(project: MavenProject) {
      scheduleUpdateProjects(listOf(project), emptyList())
    }

    fun scheduleUpdateProjects(projects: List<MavenProject>, deleted: List<MavenProject>) {
      scheduleStructureRequest(Runnable { myStructure!!.updateProjects(projects, deleted) })
    }
  }

  companion object {
    const val TOOL_WINDOW_ID: String = "Maven"
    const val TOOL_WINDOW_PLACE_ID: String = "Maven tool window"

    @JvmStatic
    fun getInstance(project: Project): MavenProjectsNavigator {
      return project.getService(MavenProjectsNavigator::class.java)
    }
  }
}
