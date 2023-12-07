// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.binding;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.editor.UIFormEditor;
import com.intellij.uiDesigner.palette.ComponentItem;
import com.intellij.uiDesigner.palette.Palette;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.List;


public class BoundIconRenderer extends GutterIconRenderer {
  private final @NotNull PsiElement myElement;
  private Icon myIcon;
  private final String myQName;

  public BoundIconRenderer(final @NotNull PsiElement element) {
    myElement = element;
    if (myElement instanceof PsiField field) {
      final PsiType type = field.getType();
      if (type instanceof PsiClassType) {
        PsiClass componentClass = ((PsiClassType)type).resolve();
        if (componentClass != null) {
          String qName = componentClass.getQualifiedName();
          if (qName != null) {
            final ComponentItem item = Palette.getInstance(myElement.getProject()).getItem(qName);
            if (item != null) {
              myIcon = item.getIcon();
            }
          }
        }
      }
      myQName = field.getContainingClass().getQualifiedName() + "#" + field.getName();
    }
    else {
      myQName = ((PsiClass) element).getQualifiedName();
    }
  }

  @Override
  public @NotNull Icon getIcon() {
    if (myIcon != null) {
      return myIcon;
    }
    return PlatformIcons.UI_FORM_ICON;
  }

  @Override
  public boolean isNavigateAction() {
    return true;
  }

  @Override
  public @Nullable AnAction getClickAction() {
    return new AnAction() {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        List<PsiFile> formFiles = getBoundFormFiles();
        if (formFiles.size() > 0) {
          final VirtualFile virtualFile = formFiles.get(0).getVirtualFile();
          if (virtualFile == null) {
            return;
          }
          Project project = myElement.getProject();
          FileEditor[] editors = FileEditorManager.getInstance(project).openFile(virtualFile, true);
          if (myElement instanceof PsiField) {
            for (FileEditor editor: editors) {
              if (editor instanceof UIFormEditor) {
                ((UIFormEditor)editor).selectComponent(((PsiField) myElement).getName());
              }
            }
          }
        }
      }
    };
  }

  @Override
  public @Nullable String getTooltipText() {
    List<PsiFile> formFiles = getBoundFormFiles();

    if (formFiles.size() > 0) {
      return composeText(formFiles);
    }
    return super.getTooltipText();
  }

  private List<PsiFile> getBoundFormFiles() {
    List<PsiFile> formFiles = Collections.emptyList();
    PsiClass aClass;
    if (myElement instanceof PsiField) {
      aClass = ((PsiField) myElement).getContainingClass();
    }
    else {
      aClass = (PsiClass) myElement;
    }
    if (aClass != null && aClass.getQualifiedName() != null) {
      formFiles = FormClassIndex.findFormsBoundToClass(aClass.getProject(), aClass);
    }
    return formFiles;
  }

  private static @NlsSafe String composeText(final List<PsiFile> formFiles) {
    @NonNls StringBuilder result = new StringBuilder("<html><body>");
    result.append(UIDesignerBundle.message("ui.is.bound.header"));
    @NonNls String sep = "";
    for (PsiFile file: formFiles) {
      result.append(sep);
      sep = "<br>";
      result.append("&nbsp;&nbsp;&nbsp;&nbsp;");
      result.append(file.getName());
    }
    result.append("</body></html>");
    return result.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    BoundIconRenderer that = (BoundIconRenderer)o;

    if (!myQName.equals(that.myQName)) return false;
    if (myIcon != null ? !myIcon.equals(that.myIcon) : that.myIcon != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myElement.hashCode();
    result = 31 * result + (myIcon != null ? myIcon.hashCode() : 0);
    return result;
  }
}
