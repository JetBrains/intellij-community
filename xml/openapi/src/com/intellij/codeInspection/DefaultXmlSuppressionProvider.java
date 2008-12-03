/*
 * Copyright 2000-2008 JetBrains s.r.o.
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

import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlProlog;
import com.intellij.psi.xml.XmlTag;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public class DefaultXmlSuppressionProvider extends XmlSuppressionProvider {

  @Override
  public boolean isProviderAvailable(PsiFile file) {
    return true;
  }

  public boolean isSuppressedFor(PsiElement element, String inspectionId) {
    final XmlTag tag = PsiTreeUtil.getContextOfType(element, XmlTag.class, false);
    return tag != null && findSuppression(tag, inspectionId) != null;
  }

  public void suppressForFile(PsiElement element, String inspectionId) {
    final PsiFile file = element.getContainingFile();
    final XmlDocument document = ((XmlFile)file).getDocument();
    final PsiElement anchor = document != null ? document.getRootTag() : file.findElementAt(0);
    assert anchor != null;
    suppress(file, findSuppressionLeaf(anchor, null), inspectionId, anchor.getTextRange().getStartOffset());
  }

  public void suppressForTag(PsiElement element, String inspectionId) {
    final XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class);
    assert tag != null;
    suppress(element.getContainingFile(), findSuppressionLeaf(tag, null), inspectionId, tag.getTextRange().getStartOffset());
  }

  @Nullable
  protected PsiElement findSuppression(final PsiElement anchor, final String id) {
    final PsiElement element = findSuppressionLeaf(anchor, id);
    if (element != null) return element;

    final PsiFile file = anchor.getContainingFile();
    if (file instanceof XmlFile) {
      return findFileSuppression(file, id);
    }

    return null;
  }

  @Nullable
  protected PsiElement findFileSuppression(PsiFile file, String id) {
    final XmlDocument document = ((XmlFile)file).getDocument();
    final XmlTag rootTag = document != null ? document.getRootTag() : null;
    PsiElement leaf = rootTag != null ? rootTag.getPrevSibling() : file.findElementAt(0);
    return findSuppressionLeaf(leaf, id);
  }

  @Nullable
  protected PsiElement findSuppressionLeaf(PsiElement leaf, @Nullable final String id) {
    while (leaf != null) {
      if (leaf instanceof PsiComment || leaf instanceof XmlProlog) {
        @NonNls String text = leaf.getText();
        if (isSuppressedFor(text, id)) return leaf;
      }
      leaf = leaf.getPrevSibling();
      if (leaf instanceof XmlTag) {
        return null;
      }
    }
    return null;
  }

  private boolean isSuppressedFor(@NonNls final String text, @Nullable final String id) {
    if (!text.contains(getPrefix())) {
      return false;
    }
    if (id == null) {
      return true;
    }
    @NonNls final String[] parts = text.split("[ ,]");
    return ArrayUtil.find(parts, id) != -1 || ArrayUtil.find(parts, XmlSuppressableInspectionTool.ALL) != -1;
  }

  protected void suppress(PsiFile file, final PsiElement suppressionElement, String inspectionId, final int offset) {
    final Project project = file.getProject();
    if (!CommonRefactoringUtil.checkReadOnlyStatus(file)) {
      return;
    }
    final Document doc = PsiDocumentManager.getInstance(project).getDocument(file);
    assert doc != null;

    if (suppressionElement != null) {
      final TextRange textRange = suppressionElement.getTextRange();
      String text = suppressionElement.getText();
      final String suppressionText = getSuppressionText(inspectionId, text);
      doc.replaceString(textRange.getStartOffset(), textRange.getEndOffset(), suppressionText);
    } else {
      final String suppressionText = getSuppressionText(inspectionId, null);
      doc.insertString(offset, suppressionText);
      CodeStyleManager.getInstance(project).adjustLineIndent(doc, offset + suppressionText.length());
      UndoUtil.markPsiFileForUndo(file);
    }
  }

  protected String getSuppressionText(String inspectionId, @Nullable String originalText) {
    if (originalText == null) {
      return getPrefix() + inspectionId + getSuffix();
    } else if (inspectionId.equals(XmlSuppressableInspectionTool.ALL)) {
      final int pos = originalText.indexOf(getPrefix());
      return originalText.substring(0, pos) + getPrefix() + inspectionId + getSuffix();
    }
    return originalText.replace(getSuffix(), ", " + inspectionId + getSuffix());
  }

  @NonNls
  protected String getPrefix() {
    return "<!--suppress ";
  }

  @NonNls
  protected String getSuffix() {
    return " -->\n";
  }
}
