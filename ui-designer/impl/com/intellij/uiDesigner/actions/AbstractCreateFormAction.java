/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.actions;

import com.intellij.ide.IdeView;
import com.intellij.ide.actions.CreateElementActionBase;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDirectory;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author yole
 */
public abstract class AbstractCreateFormAction extends CreateElementActionBase {
  public AbstractCreateFormAction(String text, String description, Icon icon) {
    super(text, description, icon);
  }

  public void update(final AnActionEvent e) {
    super.update(e);
    final DataContext dataContext = e.getDataContext();
    final Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    final Presentation presentation = e.getPresentation();
    if (presentation.isEnabled()) {
      final IdeView view = (IdeView)dataContext.getData(DataConstantsEx.IDE_VIEW);
      if (view != null) {
        final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
        final PsiDirectory[] dirs = view.getDirectories();
        for (final PsiDirectory dir : dirs) {
          if (projectFileIndex.isInSourceContent(dir.getVirtualFile()) && dir.getPackage() != null) {
            return;
          }
        }
      }

      presentation.setEnabled(false);
      presentation.setVisible(false);
    }
  }

  protected String createFormBody(Project project, @Nullable final String fullQualifiedClassName,
                                  @NonNls final String formName, final String layoutManager) throws IncorrectOperationException {

    final InputStream inputStream = getClass().getResourceAsStream(formName);

    final StringBuffer buffer = new StringBuffer();
    try {
      for (int ch; (ch = inputStream.read()) != -1;) {
        buffer.append((char)ch);
      }
    }
    catch (IOException e) {
      throw new IncorrectOperationException(UIDesignerBundle.message("error.cannot.read", formName),e);
    }

    String s = buffer.toString();

    if (fullQualifiedClassName != null) {
      s = StringUtil.replace(s, "$CLASS$", fullQualifiedClassName);
    }
    else {
      s = StringUtil.replace(s, "bind-to-class=\"$CLASS$\"", "");
    }

    s = StringUtil.replace(s, "$LAYOUT$", layoutManager);

    return StringUtil.convertLineSeparators(s);
  }

  protected String getActionName(final PsiDirectory directory, final String newName) {
    return UIDesignerBundle.message("progress.creating.class", directory.getPackage().getQualifiedName(), newName);
  }
}
