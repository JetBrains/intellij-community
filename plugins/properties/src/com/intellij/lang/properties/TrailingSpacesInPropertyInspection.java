/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.lang.properties;

import com.intellij.codeInspection.*;
import com.intellij.lang.ASTNode;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.lang.properties.psi.impl.PropertyImpl;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author cdr
 */
public class TrailingSpacesInPropertyInspection extends PropertySuppressableInspectionBase {
  @NotNull
  public String getDisplayName() {
    return PropertiesBundle.message("trail.spaces.property.inspection.display.name");
  }

  @NotNull
  public String getShortName() {
    return "TrailingSpacesInProperty";
  }

  public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull final InspectionManager manager, final boolean isOnTheFly) {
    if (!(file instanceof PropertiesFile)) return null;
    final List<Property> properties = ((PropertiesFile)file).getProperties();
    Module module = ModuleUtil.findModuleForPsiElement(file);
    if (module == null) return null;
    final List<ProblemDescriptor> descriptors = new SmartList<ProblemDescriptor>();

    for (Property property : properties) {
      ProgressManager.checkCanceled();

      ASTNode keyNode = ((PropertyImpl)property).getKeyNode();
      if (keyNode != null) {
        PsiElement key = keyNode.getPsi();
        TextRange textRange = getTrailingSpaces(key);
        if (textRange != null) {
          descriptors.add(manager.createProblemDescriptor(key, textRange, "Trailing Spaces", ProblemHighlightType.GENERIC_ERROR_OR_WARNING, true, RemoveTrailingSpacesFix.INSTANCE));
        }
      }
      ASTNode valueNode = ((PropertyImpl)property).getValueNode();
      if (valueNode != null) {
        PsiElement value = valueNode.getPsi();
        TextRange textRange = getTrailingSpaces(value);
        if (textRange != null) {
          descriptors.add(manager.createProblemDescriptor(value, textRange, "Trailing Spaces", ProblemHighlightType.GENERIC_ERROR_OR_WARNING, true, RemoveTrailingSpacesFix.INSTANCE));
        }
      }
    }
    return descriptors.toArray(new ProblemDescriptor[descriptors.size()]);
  }

  private static TextRange getTrailingSpaces(PsiElement element) {
    String key = element.getText();

    return PropertyImpl.trailingSpaces(key);
  }

  private static class RemoveTrailingSpacesFix implements LocalQuickFix {
    private static final RemoveTrailingSpacesFix INSTANCE = new RemoveTrailingSpacesFix();
    @NotNull
    public String getName() {
      return "Remove Trailing Spaces";
    }

    @NotNull
    public String getFamilyName() {
      return getName();
    }

    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getPsiElement();
      PsiElement parent = element == null ? null : element.getParent();
      if (!(parent instanceof PropertyImpl)) return;
      TextRange textRange = getTrailingSpaces(element);
      if (textRange != null) {
        Document document = PsiDocumentManager.getInstance(project).getDocument(element.getContainingFile());
        TextRange docRange = textRange.shiftRight(element.getTextRange().getStartOffset());
        document.deleteString(docRange.getStartOffset(), docRange.getEndOffset());
      }
    }
  }
}
