// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.lang.ASTNode;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.PropertiesBundle;
import com.intellij.lang.properties.PropertiesInspectionBase;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.impl.PropertyImpl;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public class TrailingSpacesInPropertyInspection extends PropertiesInspectionBase {
  public boolean myIgnoreVisibleSpaces;

  @Override
  @NotNull
  public String getShortName() {
    return "TrailingSpacesInProperty";
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    if (myIgnoreVisibleSpaces) {
      node.setAttribute("ignoreVisibleSpaces", Boolean.TRUE.toString());
    }
  }

  @Override
  public void readSettings(@NotNull Element node) throws InvalidDataException {
    final String attributeValue = node.getAttributeValue("ignoreVisibleSpaces");
    if (attributeValue != null) {
      myIgnoreVisibleSpaces = Boolean.valueOf(attributeValue);
    }
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
     return new SingleCheckboxOptionsPanel(PropertiesBundle.message("trailing.spaces.in.property.inspection.ignore.visible.spaces"), this, "myIgnoreVisibleSpaces");
  }

  @Override
  public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull final InspectionManager manager, final boolean isOnTheFly) {
    if (!(file instanceof PropertiesFile)) return null;
    final List<IProperty> properties = ((PropertiesFile)file).getProperties();
    final List<ProblemDescriptor> descriptors = new SmartList<>();
    for (IProperty property : properties) {
      ProgressManager.checkCanceled();
      final PropertyImpl propertyImpl = (PropertyImpl)property;
      for (ASTNode node : ContainerUtil.ar(propertyImpl.getKeyNode(), propertyImpl.getValueNode())) {
        if (node != null) {
          PsiElement key = node.getPsi();
          TextRange textRange = getTrailingSpaces(key, myIgnoreVisibleSpaces);
          if (textRange != null) {
            descriptors.add(manager.createProblemDescriptor(key, textRange, "Trailing spaces", ProblemHighlightType.GENERIC_ERROR_OR_WARNING, true, new RemoveTrailingSpacesFix(myIgnoreVisibleSpaces)));
          }
        }
      }
    }
    return descriptors.toArray(ProblemDescriptor.EMPTY_ARRAY);
  }

  @Nullable
  private static TextRange getTrailingSpaces(PsiElement element, boolean ignoreVisibleTrailingSpaces) {
    String key = element.getText();
    if (ignoreVisibleTrailingSpaces) {
      for (int i = key.length() - 1; i > -1; i--) {
        if (key.charAt(i) != ' ' && key.charAt(i) != '\t') {
          return i == key.length() - 1 ? null : new TextRange(i + 1, key.length());
        }
      }
      return element.getTextRange();
    } else {
      return PropertyImpl.trailingSpaces(key);
    }
  }

  private static final class RemoveTrailingSpacesFix implements LocalQuickFix {
    private final boolean myIgnoreVisibleSpaces;

    private RemoveTrailingSpacesFix(boolean ignoreVisibleSpaces) {
      myIgnoreVisibleSpaces = ignoreVisibleSpaces;
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return "Remove trailing spaces";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getPsiElement();
      PsiElement parent = element == null ? null : element.getParent();
      if (!(parent instanceof PropertyImpl)) return;
      TextRange textRange = getTrailingSpaces(element, myIgnoreVisibleSpaces);
      if (textRange != null) {
        Document document = PsiDocumentManager.getInstance(project).getDocument(element.getContainingFile());
        TextRange docRange = textRange.shiftRight(element.getTextRange().getStartOffset());
        document.deleteString(docRange.getStartOffset(), docRange.getEndOffset());
      }
    }
  }
}
