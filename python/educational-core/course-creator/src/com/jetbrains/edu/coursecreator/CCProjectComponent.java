package com.jetbrains.edu.coursecreator;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.impl.EditorFactoryImpl;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.impl.text.PsiAwareTextEditorImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.jetbrains.edu.courseFormat.Course;
import org.jetbrains.annotations.NotNull;

public class CCProjectComponent implements ProjectComponent {
  private final Project myProject;
  private CCFileDeletedListener myListener;

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
          course.initCourse(true);
          myListener = new CCFileDeletedListener(myProject);
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
}
