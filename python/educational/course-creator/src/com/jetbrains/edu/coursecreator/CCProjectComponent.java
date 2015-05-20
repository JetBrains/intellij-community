package com.jetbrains.edu.coursecreator;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.impl.EditorFactoryImpl;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.impl.text.PsiAwareTextEditorImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.jetbrains.edu.EduNames;
import com.jetbrains.edu.courseFormat.Course;
import com.jetbrains.edu.courseFormat.Lesson;
import com.jetbrains.edu.courseFormat.Task;
import com.jetbrains.edu.courseFormat.TaskFile;
import com.jetbrains.edu.coursecreator.actions.CCRunTestsAction;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class CCProjectComponent implements ProjectComponent {
  private static final Logger LOG = Logger.getInstance(CCProjectComponent.class.getName());
  private final Project myProject;
  private FileDeletedListener myListener;

  public CCProjectComponent(Project project) {
    myProject = project;
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  @NotNull
  public String getComponentName() {
    return "CCProjectComponent";
  }

  public void projectOpened() {
    StartupManager.getInstance(myProject).runWhenProjectIsInitialized(new Runnable() {
      @Override
      public void run() {
        final Course course = CCProjectService.getInstance(myProject).getCourse();
        if (course != null) {
          myProject.getMessageBus().connect(myProject).subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerAdapter() {
              @Override
              public void selectionChanged(@NotNull FileEditorManagerEvent event) {
                final VirtualFile oldFile = event.getOldFile();
                if (oldFile == null) {
                  return;
                }
                if (CCProjectService.getInstance(myProject).isTaskFile(oldFile)) {
                  FileEditorManager.getInstance(myProject).closeFile(oldFile);
                  ApplicationManager.getApplication().runWriteAction(new Runnable() {
                    @Override
                    public void run() {
                      try {
                        oldFile.delete(myProject);
                      }
                      catch (IOException e) {
                        LOG.error(e);
                      }
                    }
                  });
                }
              }
            });
          myListener = new FileDeletedListener();
          VirtualFileManager.getInstance().addVirtualFileListener(myListener);
          final CCEditorFactoryListener editorFactoryListener = new CCEditorFactoryListener();
          EditorFactory.getInstance().addEditorFactoryListener(editorFactoryListener, myProject);
          VirtualFile[] files = FileEditorManager.getInstance(myProject).getOpenFiles();
          for (VirtualFile file : files) {
            if (CCProjectService.getInstance(myProject).isTaskFile(file)) {
              FileEditorManager.getInstance(myProject).closeFile(file);
              continue;
            }
            FileEditor fileEditor = FileEditorManager.getInstance(myProject).getSelectedEditor(file);
            if (fileEditor instanceof PsiAwareTextEditorImpl) {
              Editor editor = ((PsiAwareTextEditorImpl)fileEditor).getEditor();
              editorFactoryListener.editorCreated(new EditorFactoryEvent(new EditorFactoryImpl(ProjectManager.getInstance()), editor));
            }
          }
        }
      }
    });
  }

  public void projectClosed() {
    if (myListener != null) {
      VirtualFileManager.getInstance().removeVirtualFileListener(myListener);
    }
  }

  private class FileDeletedListener extends VirtualFileAdapter {

    @Override
    public void fileDeleted(@NotNull VirtualFileEvent event) {
      if (myProject.isDisposed() || !myProject.isOpen()) {
        return;
      }
      Course course = CCProjectService.getInstance(myProject).getCourse();
      if (course == null) {
        return;
      }
      VirtualFile removedFile = event.getFile();
      if (removedFile.getName().contains(".answer")) {
        deleteTaskFile(removedFile);
      }
      if (removedFile.getName().contains(EduNames.TASK)) {
        deleteTask(removedFile);
      }
      if (removedFile.getName().contains(EduNames.LESSON)) {
        deleteLesson(course, removedFile);
      }
    }

    private void deleteLesson(@NotNull final Course course, @NotNull final VirtualFile file) {
      VirtualFile courseDir = file.getParent();
      if (!courseDir.getName().equals(myProject.getName())) {
        return;
      }
      Lesson lesson = course.getLesson(file.getName());
      if (lesson != null) {
        course.getLessons().remove(lesson);
      }
    }

    private void deleteTask(@NotNull final VirtualFile removedFile) {
      VirtualFile lessonDir = removedFile.getParent();
      final CCProjectService projectService = CCProjectService.getInstance(myProject);
      if (lessonDir == null || !lessonDir.getName().contains(EduNames.LESSON)) {
        return;
      }
      VirtualFile courseDir = lessonDir.getParent();
      if (!courseDir.getName().equals(myProject.getName())) {
        return;
      }
      final Course course = projectService.getCourse();
      Lesson lesson = course.getLesson(lessonDir.getName());
      if (lesson == null) {
        return;
      }
      Task task = lesson.getTask(removedFile.getName());
      if (task == null) {
        return;
      }
      lesson.getTaskList().remove(task);
    }

    private void deleteTaskFile(@NotNull final VirtualFile removedFile) {
      final CCProjectService projectService = CCProjectService.getInstance(myProject);
      final VirtualFile taskDir = removedFile.getParent();
      if (taskDir == null || !taskDir.getName().contains(EduNames.TASK)) {
        return;
      }
      VirtualFile lessonDir = taskDir.getParent();
      if (lessonDir == null || !lessonDir.getName().contains(EduNames.LESSON)) {
        return;
      }
      VirtualFile courseDir = lessonDir.getParent();
      if (!courseDir.getName().equals(myProject.getName())) {
        return;
      }
      final Course course = projectService.getCourse();
      Lesson lesson = course.getLesson(lessonDir.getName());
      if (lesson == null) {
        return;
      }
      Task task = lesson.getTask(taskDir.getName());
      if (task == null) {
        return;
      }
      TaskFile taskFile = projectService.getTaskFile(removedFile);
      if (taskFile == null) {
        return;
      }
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          CCRunTestsAction.clearTestEnvironment(taskDir, myProject);
        }
      });
      String name = CCProjectService.getRealTaskFileName(removedFile.getName());
      task.getTaskFiles().remove(name);
    }
  }
}
