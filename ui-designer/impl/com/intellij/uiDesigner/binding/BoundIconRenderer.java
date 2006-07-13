package com.intellij.uiDesigner.binding;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.palette.Palette;
import com.intellij.uiDesigner.palette.ComponentItem;
import com.intellij.uiDesigner.editor.UIFormEditor;
import com.intellij.util.Icons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 28.10.2005
 * Time: 17:57:34
 * To change this template use File | Settings | File Templates.
 */
public class BoundIconRenderer extends GutterIconRenderer {
  private PsiElement myElement;
  private Icon myIcon;

  public BoundIconRenderer(final PsiElement field) {
    myElement = field;
    if (myElement instanceof PsiField) {
      final PsiType type = ((PsiField)myElement).getType();
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
    }
  }

  @NotNull
  public Icon getIcon() {
    if (myIcon != null) {
      return myIcon;
    }
    return Icons.UI_FORM_ICON;
  }

  public boolean isNavigateAction() {
    return true;
  }

  @Nullable
  public AnAction getClickAction() {
    return new AnAction() {
      public void actionPerformed(AnActionEvent e) {
        PsiFile[] formFiles = getBoundFormFiles();
        if (formFiles.length > 0) {
          VirtualFile virtualFile = formFiles[0].getVirtualFile();
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

  @Nullable
  public String getTooltipText() {
    PsiFile[] formFiles = getBoundFormFiles();

    if (formFiles.length > 0) {
      return composeText(formFiles);
    }
    return super.getTooltipText();
  }

  private PsiFile[] getBoundFormFiles() {
    PsiFile[] formFiles = PsiFile.EMPTY_ARRAY;
    PsiSearchHelper helper = myElement.getManager().getSearchHelper();
    PsiClass aClass;
    if (myElement instanceof PsiField) {
      aClass = ((PsiField) myElement).getContainingClass();
    }
    else {
      aClass = (PsiClass) myElement;
    }
    if (aClass != null && aClass.getQualifiedName() != null) {
      formFiles = helper.findFormsBoundToClass(aClass.getQualifiedName());
    }
    return formFiles;
  }

  private static String composeText(final PsiFile[] formFiles) {
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
}
