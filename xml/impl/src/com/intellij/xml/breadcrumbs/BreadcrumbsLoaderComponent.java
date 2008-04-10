/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package com.intellij.xml.breadcrumbs;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author spleaner
 */
public class BreadcrumbsLoaderComponent extends AbstractProjectComponent {
  private static final Key<Object> BREADCRUMBS_SUITABLE_FILE = new Key<Object>("breadcrumbs.suitable.file");

  public BreadcrumbsLoaderComponent(@NotNull final Project project) {
    super(project);
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "HtmlBreadCrumbsComponent";
  }

  public void initComponent() {
    final MyFileEditorManagerListener listener = new MyFileEditorManagerListener();
    Disposer.register(myProject, listener);
    FileEditorManager.getInstance(myProject).addFileEditorManagerListener(listener, listener);
  }

  private static class MyFileEditorManagerListener implements FileEditorManagerListener, Disposable {

    public void dispose() {
    }

    public void fileOpened(final FileEditorManager source, final VirtualFile file) {
      if (isSuitable(source.getProject(), file)) {
        final FileEditor[] fileEditors = source.getEditors(file);
        for (final FileEditor each : fileEditors) {
          if (each instanceof TextEditor) {
            final BreadcrumbsXmlWrapper wrapper = new BreadcrumbsXmlWrapper(((TextEditor)each).getEditor());
            final JComponent c = wrapper.getComponent();
            source.addTopComponent(each, c);

            Disposer.register(each, wrapper);
            Disposer.register(each, new Disposable() {
              public void dispose() {
                source.removeTopComponent(each, c);
              }
            });
          }
        }
      }
    }

    private static boolean isSuitable(final Project project, final VirtualFile file) {
      final FileViewProvider psiFile = PsiManager.getInstance(project).findViewProvider(file);
      
      return psiFile != null &&
             (BreadcrumbsXmlWrapper.findInfoProvider(psiFile) != null ||
              file.getUserData(BREADCRUMBS_SUITABLE_FILE) != null);
    }

    public void fileClosed(final FileEditorManager source, final VirtualFile file) {
    }

    public void selectionChanged(final FileEditorManagerEvent event) {
    }
  }
}