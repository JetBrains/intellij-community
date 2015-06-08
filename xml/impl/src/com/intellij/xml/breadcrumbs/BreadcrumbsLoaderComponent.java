/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.xml.breadcrumbs;

import com.intellij.application.options.editor.WebEditorOptions;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author spleaner
 */
public class BreadcrumbsLoaderComponent extends AbstractProjectComponent {

  public BreadcrumbsLoaderComponent(@NotNull final Project project) {
    super(project);
  }

  @Override
  @NonNls
  @NotNull
  public String getComponentName() {
    return "HtmlBreadcrumbsComponent";
  }

  @Override
  public void initComponent() {
    myProject.getMessageBus().connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new MyFileEditorManagerListener());
  }

  private static boolean isEnabled() {
    final WebEditorOptions webEditorOptions = WebEditorOptions.getInstance();
    return webEditorOptions.isBreadcrumbsEnabled() || webEditorOptions.isBreadcrumbsEnabledInXml();
  }

  private static class MyFileEditorManagerListener extends FileEditorManagerAdapter {
    @Override
    public void fileOpened(@NotNull final FileEditorManager source, @NotNull final VirtualFile file) {
      if (isEnabled() && isSuitable(source.getProject(), file)) {
        final FileEditor[] fileEditors = source.getAllEditors(file);
        for (final FileEditor fileEditor : fileEditors) {
          if (fileEditor instanceof TextEditor) {
            Editor editor = ((TextEditor)fileEditor).getEditor();
            if (BreadcrumbsXmlWrapper.getBreadcrumbsComponent(editor) != null) {
              continue;
            }
            final BreadcrumbsXmlWrapper wrapper = new BreadcrumbsXmlWrapper(editor);
            final JComponent c = wrapper.getComponent();
            source.addTopComponent(fileEditor, c);

            Disposer.register(fileEditor, wrapper);
            Disposer.register(fileEditor, new Disposable() {
              @Override
              public void dispose() {
                source.removeTopComponent(fileEditor, c);
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

      final FileViewProvider provider = PsiManager.getInstance(project).findViewProvider(file);

      return provider != null && BreadcrumbsXmlWrapper.findInfoProvider(provider) != null;
    }
  }
}