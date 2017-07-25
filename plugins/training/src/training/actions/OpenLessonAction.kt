package training.actions

import com.intellij.ide.RecentProjectsManager
import com.intellij.ide.scratch.ScratchFileService
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.projectImport.ProjectImportBuilder.getCurrentProject
import training.lang.LangManager
import training.learn.*
import training.learn.CourseManager.LEARN_PROJECT_NAME
import training.learn.dialogs.SdkModuleProblemDialog
import training.learn.exceptons.*
import training.ui.LearnToolWindowFactory
import training.util.MyClassLoader
import java.awt.FontFormatException
import java.io.IOException
import java.util.*
import java.util.concurrent.ExecutionException

/**
 * @author Sergey Karashevich
 */
class OpenLessonAction : AnAction() {

  override fun actionPerformed(e: AnActionEvent) {

    val lesson = e.getData(LESSON_DATA_KEY);
    val project = e.project

    if (lesson != null && project != null) {
      openLesson(project, lesson)
    } else {
      //in case of starting from Welcome Screen
      val myLearnProject = initLearnProject(project)
      openLearnToolWindowAndShowModules(myLearnProject!!)
    }
  }

  @Synchronized @Throws(BadModuleException::class, BadLessonException::class, IOException::class, FontFormatException::class, InterruptedException::class, ExecutionException::class, LessonIsOpenedException::class)
  fun openLesson(project: Project, lesson: Lesson) {
    var project = project

    try {
      CourseManager.getInstance().lastActivityTime = System.currentTimeMillis()

      if (lesson.isOpen) throw LessonIsOpenedException(lesson.name + " is opened")

      //If lesson doesn't have parent module
      if (lesson.module == null)
        throw BadLessonException("Unable to open lesson without specified module")

      val myProject = project
      val scratchFileName = "Learning"
      var vf: VirtualFile? = null
      val learnProject = CourseManager.getInstance().learnProject
      if (lesson.module!!.moduleType == Module.ModuleType.SCRATCH) {
        CourseManager.getInstance().checkEnvironment(project)
        vf = getScratchFile(myProject, lesson, scratchFileName)
      } else {
        //0. learnProject == null but this project is LearnProject then just getFileInLearnProject
        if (learnProject == null && getCurrentProject()!!.name == LEARN_PROJECT_NAME) {
          CourseManager.getInstance().learnProject = getCurrentProject()
          vf = getFileInLearnProject(lesson)
          //1. learnProject == null and current project has different name then initLearnProject and register post startup open lesson
        } else if (learnProject == null && getCurrentProject()!!.name != LEARN_PROJECT_NAME) {
          val myLearnProject = initLearnProject(myProject) ?: return
// in case of user aborted to create a LearnProject
          openLessonWhenLearnProjectStart(lesson, myLearnProject)
          return
          //2. learnProject != null and learnProject is disposed then reinitProject and getFileInLearnProject
        } else if (learnProject!!.isDisposed) {
          val myLearnProject = initLearnProject(myProject) ?: return
// in case of user aborted to create a LearnProject
          openLessonWhenLearnProjectStart(lesson, myLearnProject)
          return
          //3. learnProject != null and learnProject is opened but not focused then focus Project and getFileInLearnProject
        } else if (learnProject.isOpen && getCurrentProject() != learnProject) {
          vf = getFileInLearnProject(lesson)
          //4. learnProject != null and learnProject is opened and focused getFileInLearnProject
        } else if (learnProject.isOpen && getCurrentProject() == learnProject) {
          vf = getFileInLearnProject(lesson)
        } else {
          throw Exception("Unable to start Learn project")
        }
      }

      if (vf == null) return  //if user aborts opening lesson in LearnProject or Virtual File couldn't be computed
      if (lesson.module!!.moduleType != Module.ModuleType.SCRATCH)
        project = CourseManager.getInstance().learnProject!!

      //open next lesson if current is passed
      val currentProject = project
      CourseManager.getInstance().setLessonView()

      lesson.onStart()

      lesson.addLessonListener(object : LessonListenerAdapter() {
        @Throws(BadLessonException::class, ExecutionException::class, IOException::class, FontFormatException::class, InterruptedException::class, BadModuleException::class, LessonIsOpenedException::class)
        override fun lessonNext(lesson: Lesson) {
          if (lesson.module == null) return

          if (lesson.module!!.hasNotPassedLesson()) {
            val nextLesson = lesson.module!!.giveNotPassedAndNotOpenedLesson() ?: throw BadLessonException("Unable to obtain not passed and not opened lessons")
            openLesson(currentProject, nextLesson)
          }
        }
      })

      val target: String?
      if (lesson.targetPath != null) {
        val `is` = MyClassLoader.getInstance().getResourceAsStream(lesson.module!!.answersPath!! + lesson.targetPath!!) ?: throw IOException("Unable to get feedback for \"" + lesson.name + "\" lesson")
        target = Scanner(`is`).useDelimiter("\\Z").next()
      } else {
        target = null
      }


      //Dispose balloon while scratch file is closing. InfoPanel still exists.
      project.messageBus.connect(project).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerAdapter() {
        override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
          lesson.close()
        }
      })

      //to start any lesson we need to do 4 steps:
      //1. open editor or find editor
      var textEditor: TextEditor? = null
      if (FileEditorManager.getInstance(project).isFileOpen(vf)) {
        val editors = FileEditorManager.getInstance(project).getEditors(vf)
        for (fileEditor in editors) {
          if (fileEditor is TextEditor) {
            textEditor = fileEditor
          }
        }
      }
      if (textEditor == null) {
        val editors = FileEditorManager.getInstance(project).openFile(vf, true, true)
        for (fileEditor in editors) {
          if (fileEditor is TextEditor) {
            textEditor = fileEditor
          }
        }
      }
      if (textEditor!!.editor.isDisposed) {
        throw Exception("Editor is already disposed!!!")
      }

      //2. set the focus on this editor
      //FileEditorManager.getInstance(project).setSelectedEditor(vf, TextEditorProvider.getInstance().getEditorTypeId());
      FileEditorManager.getInstance(project).openEditor(OpenFileDescriptor(project, vf), true)

      //3. update tool window
      CourseManager.getInstance().learnPanel.clear()


      //4. Process lesson
      LessonProcessor.process(project, lesson, textEditor.editor, target)

    } catch (noSdkException: NoSdkException) {
      Messages.showMessageDialog(project, LearnBundle.message("dialog.noSdk.message", LangManager.getInstance().getLanguageDisplayName()!!), LearnBundle.message("dialog.noSdk.title"), Messages.getErrorIcon())
      if (ProjectSettingsService.getInstance(project).chooseAndSetSdk() != null) openLesson(project, lesson)
    } catch (noSdkException: InvalidSdkException) {
      Messages.showMessageDialog(project, LearnBundle.message("dialog.noSdk.message", LangManager.getInstance().getLanguageDisplayName()!!), LearnBundle.message("dialog.noSdk.title"), Messages.getErrorIcon())
      if (ProjectSettingsService.getInstance(project).chooseAndSetSdk() != null) openLesson(project, lesson)
    } catch (noJavaModuleException: NoJavaModuleException) {
      showModuleProblemDialog(project)
    } catch (e: Exception) {
      e.printStackTrace()
    }

  }

  //
  private fun openLearnToolWindowAndShowModules(myLearnProject: Project) {
    if (myLearnProject.isOpen && myLearnProject.isInitialized) {
      showModules(myLearnProject)
    } else {
      StartupManager.getInstance(myLearnProject).registerPostStartupActivity { showModules(myLearnProject) }
    }
  }

  private fun showModules(project: Project) {
    val toolWindowManager = ToolWindowManager.getInstance(project)
    val learnToolWindow = toolWindowManager.getToolWindow(LearnToolWindowFactory.LEARN_TOOL_WINDOW)
    if (learnToolWindow != null) {
      learnToolWindow.show(null)
      try {
        CourseManager.getInstance().setModulesView()
      } catch (e: Exception) {
        e.printStackTrace()
      }

    }
  }

  private fun openLessonWhenLearnProjectStart(lesson: Lesson?, myLearnProject: Project) {
    StartupManager.getInstance(myLearnProject).registerPostStartupActivity {
      val toolWindowManager = ToolWindowManager.getInstance(myLearnProject)
      val learnToolWindow = toolWindowManager.getToolWindow(LearnToolWindowFactory.LEARN_TOOL_WINDOW)
      if (learnToolWindow != null) {
        learnToolWindow.show(null)
        try {
          CourseManager.getInstance().setLessonView()
          CourseManager.getInstance().openLesson(myLearnProject, lesson)
        } catch (e: Exception) {
          e.printStackTrace()
        }

      }
    }
  }

  @Throws(IOException::class)
  private fun getScratchFile(project: Project, lesson: Lesson?, filename: String): VirtualFile? {
    var vf: VirtualFile? = null
    assert(lesson != null)
    assert(lesson!!.module != null)
    val myLanguage = lesson.lang

    val languageByID = Language.findLanguageByID(myLanguage)
    if (CourseManager.getInstance().mapModuleVirtualFile.containsKey(lesson.module)) {
      vf = CourseManager.getInstance().mapModuleVirtualFile[lesson.module]
      ScratchFileService.getInstance().scratchesMapping.setMapping(vf, languageByID)
    }
    if (vf == null || !vf.isValid) {
      //while module info is not stored

      //find file if it is existed
      vf = ScratchFileService.getInstance().findFile(ScratchRootType.getInstance(), filename, ScratchFileService.Option.existing_only)
      if (vf != null) {
        FileEditorManager.getInstance(project).closeFile(vf)
        ScratchFileService.getInstance().scratchesMapping.setMapping(vf, languageByID)
      }

      if (vf == null || !vf.isValid) {
        vf = ScratchRootType.getInstance().createScratchFile(project, filename, languageByID, "")
        assert(vf != null)
      }
      CourseManager.getInstance().registerVirtualFile(lesson.module, vf)
    }
    return vf
  }

  //
//    private fun showSdkProblemDialog(project: Project, sdkMessage: String) {
//        val dialog = SdkProjectProblemDialog(project, sdkMessage)
//        dialog.show()
//    }
//
  private fun showModuleProblemDialog(project: Project) {
    val dialog = SdkModuleProblemDialog(project)
    dialog.show()
  }

  //
  @Throws(IOException::class)
  private fun getFileInLearnProject(lesson: Lesson): VirtualFile {

    val function = object : Computable<VirtualFile> {

      override fun compute(): VirtualFile {
        val learnProject = CourseManager.getInstance().learnProject!!
        val sourceRootFile = ProjectRootManager.getInstance(learnProject).contentSourceRoots[0]
        val myLanguage = lesson.lang
        val languageByID = Language.findLanguageByID(myLanguage)
        val extensionFile = languageByID!!.associatedFileType!!.defaultExtension

        var fileName = "Test." + extensionFile
        if (lesson.module != null) {
          fileName = lesson.module!!.nameWithoutWhitespaces + "." + extensionFile
        }

        var lessonVirtualFile = sourceRootFile.findChild(fileName)
        if (lessonVirtualFile == null) {

          try {
            lessonVirtualFile = sourceRootFile.createChildData(this, fileName)
          } catch (e: IOException) {
            e.printStackTrace()
          }

        }

        CourseManager.getInstance().registerVirtualFile(lesson.module, lessonVirtualFile)
        return lessonVirtualFile!!
      }
    }

    val vf = ApplicationManager.getApplication().runWriteAction(function)
    assert(vf is VirtualFile)
    return vf
  }

  //
  private fun initLearnProject(projectToClose: Project?): Project? {
    var myLearnProject: Project? = null

    //if projectToClose is open
    val openProjects = ProjectManager.getInstance().openProjects
    for (openProject in openProjects) {
      val name = openProject.name
      if (name == LEARN_PROJECT_NAME) {
        myLearnProject = openProject
        if (ApplicationManager.getApplication().isUnitTestMode) return openProject
      }
    }

    if (myLearnProject == null || myLearnProject.projectFile == null) {

      if (!ApplicationManager.getApplication().isUnitTestMode && projectToClose != null)
        if (!NewLearnProjectUtil.showDialogOpenLearnProject(projectToClose))
          return null //if user abort to open lesson in a new Project
      if (CourseManager.getInstance().learnProjectPath != null) {
        try {
          myLearnProject = ProjectManager.getInstance().loadAndOpenProject(CourseManager.getInstance().learnProjectPath)
        } catch (e: Exception) {
          e.printStackTrace()
        }

      } else {
        try {
          myLearnProject = NewLearnProjectUtil.createLearnProject(LEARN_PROJECT_NAME, projectToClose, LangManager.getInstance().getLangSupport()!!)
        } catch (e: IOException) {
          e.printStackTrace()
        }

      }

      LangManager.getInstance().getLangSupport()!!.applyToProjectAfterConfigure().invoke(myLearnProject!!)
    }

    CourseManager.getInstance().learnProject = myLearnProject

    assert(CourseManager.getInstance().learnProject != null)
    assert(CourseManager.getInstance().learnProject!!.projectFile != null)
    assert(CourseManager.getInstance().learnProject!!.projectFile!!.parent != null)
    assert(CourseManager.getInstance().learnProject!!.projectFile!!.parent.parent != null)

    CourseManager.getInstance().learnProjectPath = CourseManager.getInstance().learnProject!!.basePath
    //Hide LearnProject from Recent projects
    RecentProjectsManager.getInstance().removePath(CourseManager.getInstance().learnProject!!.presentableUrl)

    return myLearnProject

  }

  //
  companion object {

    var LESSON_DATA_KEY = DataKey.create<Lesson>("LESSON_DATA_KEY")
  }


}
