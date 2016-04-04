package com.jetbrains.edu.coursecreator;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.annotations.NotNull;

public class CCProjectComponent implements ProjectComponent {
  private final Project myProject;
  private final  CCVirtualFileListener myTaskFileLifeListener = new CCVirtualFileListener();
  private CCFileDeletedListener myListener;

  public CCProjectComponent(Project project) {
    myProject = project;
  }

  public void initComponent() {
    VirtualFileManager.getInstance().addVirtualFileListener(myTaskFileLifeListener);
  }

  public void disposeComponent() {
  }

  @NotNull
  public String getComponentName() {
    return "CCProjectComponent";
  }

  public void projectOpened() {
    VirtualFileManager.getInstance().addVirtualFileListener(myTaskFileLifeListener);
    StartupManager.getInstance(myProject).runWhenProjectIsInitialized(new Runnable() {
      @Override
      public void run() {
        final Course course = CCProjectService.getInstance(myProject).getCourse();
        if (course != null) {
          course.initCourse(true);
          myTaskFileLifeListener = new CCFileDeletedListener(myProject);
          VirtualFileManager.getInstance().addVirtualFileListener(myTaskFileLifeListener);
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
    VirtualFileManager.getInstance().removeVirtualFileListener(myTaskFileLifeListener);
    if (myListener != null) {
      VirtualFileManager.getInstance().removeVirtualFileListener(myTaskFileLifeListener);
    }
  }
}
