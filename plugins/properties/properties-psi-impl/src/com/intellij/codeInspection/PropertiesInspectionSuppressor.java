/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.codeInsight.FileModificationService;
import com.intellij.lang.properties.PropertiesBundle;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.PropertiesList;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class PropertiesInspectionSuppressor implements InspectionSuppressor {
  private final static Logger LOG = Logger.getInstance(PropertiesInspectionSuppressor.class);

  @Override
  @NotNull
  public SuppressQuickFix[] getSuppressActions(final PsiElement element, @NotNull final String toolId) {
    return new SuppressQuickFix[] {new SuppressSinglePropertyFix(toolId), new SuppressForFile(toolId)};
  }

  @Override
  public boolean isSuppressedFor(@NotNull PsiElement element, @NotNull String toolId) {
    Property property = PsiTreeUtil.getParentOfType(element, Property.class, false);
    PropertiesFile file;
    if (property == null) {
      PsiFile containingFile = element.getContainingFile();
      if (containingFile instanceof PropertiesFile) {
        file = (PropertiesFile)containingFile;
      }
      else {
        return false;
      }
    }
    else {
      PsiElement prev = property.getPrevSibling();
      while (prev instanceof PsiWhiteSpace || prev instanceof PsiComment) {
        if (prev instanceof PsiComment) {
          @NonNls String text = prev.getText();
          if (text.contains("suppress") && text.contains("\"" + toolId + "\"")) return true;
        }
        prev = prev.getPrevSibling();
      }
      file = property.getPropertiesFile();
    }
    PsiElement leaf = file.getContainingFile().findElementAt(0);
    while (leaf instanceof PsiWhiteSpace) leaf = leaf.getNextSibling();

    while (leaf instanceof PsiComment) {
      @NonNls String text = leaf.getText();
      if (text.contains("suppress") && text.contains("\"" + toolId + "\"") && text.contains("file")) {
        return true;
      }
      leaf = leaf.getNextSibling();
      if (leaf instanceof PsiWhiteSpace) leaf = leaf.getNextSibling();
      // comment before first property get bound to the file, not property
      if (leaf instanceof PropertiesList && leaf.getFirstChild() == property && text.contains("suppress") && text.contains("\"" + toolId + "\"")) {
        return true;
      }
    }

    return false;
  }

  private static class SuppressSinglePropertyFix implements SuppressQuickFix {
    private final String shortName;

    private SuppressSinglePropertyFix(String shortName) {
      this.shortName = shortName;
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return PropertiesBundle.message("unused.property.suppress.for.property");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getStartElement();
      final PsiFile file = element.getContainingFile();
      if (!FileModificationService.getInstance().prepareFileForWrite(file)) return;

      final Property property = PsiTreeUtil.getParentOfType(element, Property.class);
      LOG.assertTrue(property != null);
      final int start = property.getTextRange().getStartOffset();

      @NonNls final Document doc = PsiDocumentManager.getInstance(project).getDocument(file);
      LOG.assertTrue(doc != null);
      final int line = doc.getLineNumber(start);
      final int lineStart = doc.getLineStartOffset(line);

      doc.insertString(lineStart, "# suppress inspection \"" + shortName +
                                  "\"\n");
    }

    @Override
    public boolean isAvailable(@NotNull Project project, @NotNull PsiElement context) {
      final Property property = PsiTreeUtil.getParentOfType(context, Property.class);
      return property != null && property.isValid();
    }

    @Override
    public boolean isSuppressAll() {
      return false;
    }
  }



  private static class SuppressForFile implements SuppressQuickFix {
    private final String shortName;

    private SuppressForFile(String shortName) {
      this.shortName = shortName;
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return PropertiesBundle.message("unused.property.suppress.for.file");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getStartElement();
      final PsiFile file = element.getContainingFile();
      if (!FileModificationService.getInstance().prepareFileForWrite(file)) return;

      @NonNls final Document doc = PsiDocumentManager.getInstance(project).getDocument(file);
      LOG.assertTrue(doc != null, file);

      doc.insertString(0, "# suppress inspection \"" +
                          shortName +
                          "\" for whole file\n");
    }

    @Override
    public boolean isAvailable(@NotNull Project project, @NotNull PsiElement context) {
      return context.isValid() && context.getContainingFile() instanceof PropertiesFile;
    }

    @Override
    public boolean isSuppressAll() {
      return false;
    }

    @Override
    public boolean startInWriteAction() {
      return true;
    }
  }
}
