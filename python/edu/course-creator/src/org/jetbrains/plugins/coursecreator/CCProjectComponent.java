package org.jetbrains.plugins.coursecreator;

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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.coursecreator.format.Course;

import java.io.IOException;

public class CCProjectComponent implements ProjectComponent {
  private static final Logger LOG = Logger.getInstance(CCProjectComponent.class.getName());
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
  }
}
