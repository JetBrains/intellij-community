package org.jetbrains.plugins.coursecreator;

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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.coursecreator.format.Course;

public class CCProjectComponent implements ProjectComponent {
  private final Project myProject;

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
        Course course = CCProjectService.getInstance(myProject).getCourse();
        if (course != null) {
          EditorFactory.getInstance().addEditorFactoryListener(new CCEditorFactoryListener(), myProject);
          VirtualFile[] files = FileEditorManager.getInstance(myProject).getOpenFiles();
          for (VirtualFile file : files) {
            FileEditor fileEditor = FileEditorManager.getInstance(myProject).getSelectedEditor(file);
            if (fileEditor instanceof PsiAwareTextEditorImpl) {
              Editor editor = ((PsiAwareTextEditorImpl)fileEditor).getEditor();
              new CCEditorFactoryListener().editorCreated(new EditorFactoryEvent(new EditorFactoryImpl(ProjectManager.getInstance()), editor ));
            }
          }
        }
      }
    });
  }

  public void projectClosed() {
  }
}
