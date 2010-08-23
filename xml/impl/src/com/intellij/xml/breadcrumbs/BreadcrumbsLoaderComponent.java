/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package com.intellij.xml.breadcrumbs;

import com.intellij.application.options.editor.WebEditorOptions;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

/**
 * @author spleaner
 */
public class BreadcrumbsLoaderComponent extends AbstractProjectComponent {  

  public BreadcrumbsLoaderComponent(@NotNull final Project project) {
    super(project);
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "HtmlBreadcrumbsComponent";
  }

  public void initComponent() {
    myProject.getMessageBus().connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new MyFileEditorManagerListener());
  }

  private static boolean isEnabled() {
    return WebEditorOptions.getInstance().isBreadcrumbsEnabled();
  }

  private static class MyFileEditorManagerListener implements FileEditorManagerListener {
    public void fileOpened(final FileEditorManager source, final VirtualFile file) {
      if (isEnabled() && isSuitable(source.getProject(), file)) {
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
      if (file instanceof HttpVirtualFile) {
        return false;
      }

      final FileViewProvider psiFile = PsiManager.getInstance(project).findViewProvider(file);

      return psiFile != null
             && hasNonEmptyHtml(psiFile)
             && BreadcrumbsXmlWrapper.findInfoProvider(psiFile) != null;
    }

    public static boolean hasNonEmptyHtml(FileViewProvider viewProvider) {
      final List<PsiFile> files = viewProvider.getAllFiles();

      if (files.size() < 2) return true; // There is only HTML Language

      for (PsiFile file : files) {
        if (file.getLanguage() == HTMLLanguage.INSTANCE && file instanceof XmlFile) {
          final XmlDocument xml = ((XmlFile)file).getDocument();
          return xml != null && xml.getRootTag() != null;
        }
      }
      return false;
    }

    public void fileClosed(final FileEditorManager source, final VirtualFile file) {
    }

    public void selectionChanged(final FileEditorManagerEvent event) {
    }
  }
}
