/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.testFramework.fixtures.impl;

import com.intellij.ide.startup.impl.StartupManagerImpl;
import com.intellij.idea.IdeaTestApplication;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.impl.EditorFactoryImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * @author mike
 */
class HeavyIdeaTestFixtureImpl implements IdeaProjectTestFixture {
  private Project myProject;
  private Set<File> myFilesToDelete = new HashSet<File>();
  private IdeaTestApplication myApplication;

  public void setUp() throws Exception {
    initApplication();
    setUpProject();
  }

  protected void setUpProject() throws IOException {
    ProjectManagerEx projectManager = ProjectManagerEx.getInstanceEx();

    File projectFile = File.createTempFile("temp", ".ipr");
    myFilesToDelete.add(projectFile);

    myProject = projectManager.newProject(projectFile.getPath(), false, false);

    ((StartupManagerImpl)StartupManager.getInstance(myProject)).runStartupActivities();
  }


  protected void initApplication() throws Exception {
    myApplication = IdeaTestApplication.getInstance();
    myApplication.setDataProvider(new MyDataProvider());
  }

  public void tearDown() throws Exception {
    ApplicationManager.getApplication().runWriteAction(EmptyRunnable.getInstance()); // Flash posponed formatting if any.
    FileDocumentManager.getInstance().saveAllDocuments();

    doPostponedFormatting(myProject);

    Disposer.dispose(myProject);

    for (final File fileToDelete : myFilesToDelete) {
      delete(fileToDelete);
    }

    myApplication.setDataProvider(null);

    EditorFactory editorFactory = EditorFactory.getInstance();
    final Editor[] allEditors = editorFactory.getAllEditors();
    ((EditorFactoryImpl)editorFactory).validateEditorsAreReleased(getProject());
    for (Editor editor : allEditors) {
      editorFactory.releaseEditor(editor);
    }
    assert 0 == allEditors.length : "There are unrealeased editors";
  }

  public Project getProject() {
    return myProject;
  }

  public Module getModule() {
    throw new UnsupportedOperationException();
  }

  private static void doPostponedFormatting(final Project project) {
    try {
      CommandProcessor.getInstance().runUndoTransparentAction(new Runnable() {
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              PsiDocumentManager.getInstance(project).commitAllDocuments();
              PostprocessReformattingAspect.getInstance(project).doPostponedFormatting();
            }
          });
        }
      });
    }
    catch (Throwable e) {
      // Way to go...
    }
  }

  private class MyDataProvider implements DataProvider {
    @Nullable
    public Object getData(@NonNls String dataId) {
      if (dataId.equals(DataConstants.PROJECT)) {
        return myProject;
      }
      else if (dataId.equals(DataConstants.EDITOR)) {
        return FileEditorManager.getInstance(myProject).getSelectedTextEditor();
      }
      else {
        return null;
      }
    }
  }

  private static void delete(File file) {
    if (file.isDirectory()) {
      File[] files = file.listFiles();
      for (File fileToDelete : files) {
        delete(fileToDelete);
      }
    }

    boolean b = file.delete();
    if (!b && file.exists()) {
      throw new IllegalStateException("Can't delete " + file.getAbsolutePath());
    }
  }
}
