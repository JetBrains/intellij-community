/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

/**
 * @author cdr
 */
public class TrailingSpacesInPropertyInspection extends PropertiesInspectionBase {
  public boolean myIgnoreVisibleSpaces;

  @NotNull
  public String getDisplayName() {
    return PropertiesBundle.message("trail.spaces.property.inspection.display.name");
  }

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
    return descriptors.toArray(new ProblemDescriptor[descriptors.size()]);
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

  private static class RemoveTrailingSpacesFix implements LocalQuickFix {
    private final boolean myIgnoreVisibleSpaces;

    private RemoveTrailingSpacesFix(boolean ignoreVisibleSpaces) {
      myIgnoreVisibleSpaces = ignoreVisibleSpaces;
    }

    @NotNull
    public String getFamilyName() {
      return "Remove trailing spaces";
    }

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
