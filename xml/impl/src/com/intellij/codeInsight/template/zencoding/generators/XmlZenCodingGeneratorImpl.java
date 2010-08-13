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
package com.intellij.codeInsight.template.zencoding.generators;

import com.intellij.codeInsight.template.zencoding.ZenCodingUtil;
import com.intellij.lang.ASTNode;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlComment;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class XmlZenCodingGeneratorImpl extends XmlZenCodingGenerator {
  public static final XmlZenCodingGeneratorImpl INSTANCE = new XmlZenCodingGeneratorImpl();

  private static boolean isTrueXml(FileType type) {
    return type == StdFileTypes.XHTML || type == StdFileTypes.JSPX || type == StdFileTypes.XML;
  }

  @NotNull
  public String toString(@NotNull XmlTag tag,
                         @NotNull List<Pair<String, String>> attribute2Value,
                         boolean hasChildren,
                         @NotNull PsiElement context) {
    FileType fileType = context.getContainingFile().getFileType();
    if (isTrueXml(fileType)) {
      closeUnclosingTags(tag);
    }
    return tag.getContainingFile().getText();
  }

  @NotNull
  public String buildAttributesString(@NotNull List<Pair<String, String>> attribute2value, boolean hasChildren, int numberInIteration) {
    StringBuilder result = new StringBuilder();
    for (Iterator<Pair<String, String>> it = attribute2value.iterator(); it.hasNext();) {
      Pair<String, String> pair = it.next();
      String name = pair.first;
      String value = ZenCodingUtil.getValue(pair, numberInIteration);
      result.append(getAttributeString(name, value));
      if (it.hasNext()) {
        result.append(' ');
      }
    }
    return result.toString();
  }

  public boolean isMyContext(@NotNull PsiElement context) {
    if (!(context.getLanguage() instanceof XMLLanguage)) {
      return false;
    }
    if (PsiTreeUtil.getParentOfType(context, XmlAttributeValue.class) != null) {
      return false;
    }
    if (PsiTreeUtil.getParentOfType(context, XmlComment.class) != null) {
      return false;
    }
    return true;
  }

  public String getSuffix() {
    return "html";
  }

  public boolean isAppliedByDefault(@NotNull PsiElement context) {
    return true;
  }

  private static String getAttributeString(String name, String value) {
    return name + "=\"" + value + '"';
  }

  @SuppressWarnings({"ConstantConditions"})
  private static void closeUnclosingTags(@NotNull XmlTag root) {
    final List<SmartPsiElementPointer<XmlTag>> tagToClose = new ArrayList<SmartPsiElementPointer<XmlTag>>();
    Project project = root.getProject();
    final SmartPointerManager pointerManager = SmartPointerManager.getInstance(project);
    root.accept(new XmlRecursiveElementVisitor() {
      @Override
      public void visitXmlTag(final XmlTag tag) {
        if (!isTagClosed(tag)) {
          tagToClose.add(pointerManager.createLazyPointer(tag));
        }
      }
    });
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    for (final SmartPsiElementPointer<XmlTag> pointer : tagToClose) {
      final XmlTag tag = pointer.getElement();
      if (tag != null) {
        final ASTNode child = XmlChildRole.START_TAG_END_FINDER.findChild(tag.getNode());
        if (child != null) {
          final int offset = child.getTextRange().getStartOffset();
          VirtualFile file = tag.getContainingFile().getVirtualFile();
          if (file != null) {
            final Document document = FileDocumentManager.getInstance().getDocument(file);
            documentManager.doPostponedOperationsAndUnblockDocument(document);
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              public void run() {
                document.replaceString(offset, tag.getTextRange().getEndOffset(), "/>");
              }
            });
          }
        }
      }
    }
    documentManager.commitAllDocuments();
  }

  private static boolean isTagClosed(@NotNull XmlTag tag) {
    ASTNode node = tag.getNode();
    assert node != null;
    final ASTNode emptyTagEnd = XmlChildRole.EMPTY_TAG_END_FINDER.findChild(node);
    final ASTNode endTagEnd = XmlChildRole.CLOSING_TAG_START_FINDER.findChild(node);
    return emptyTagEnd != null || endTagEnd != null;
  }
}
