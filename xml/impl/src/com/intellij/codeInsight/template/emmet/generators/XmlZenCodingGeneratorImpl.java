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
package com.intellij.codeInsight.template.emmet.generators;

import com.intellij.application.options.emmet.EmmetOptions;
import com.intellij.application.options.emmet.XmlEmmetConfigurable;
import com.intellij.codeInsight.template.HtmlTextContextType;
import com.intellij.codeInsight.template.emmet.ZenCodingUtil;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
public class XmlZenCodingGeneratorImpl extends XmlZenCodingGenerator {
  public static final XmlZenCodingGeneratorImpl INSTANCE = new XmlZenCodingGeneratorImpl();

  private static boolean isTrueXml(FileType type) {
    return type == StdFileTypes.XHTML || type == StdFileTypes.JSPX || type == StdFileTypes.XML;
  }

  @Override
  @NotNull
  public String toString(@NotNull XmlTag tag,
                         @NotNull Map<String, String> attributes,
                         boolean hasChildren,
                         @NotNull PsiElement context) {
    FileType fileType = context.getContainingFile().getFileType();
    if (isTrueXml(fileType)) {
      closeUnclosingTags(tag);
    }
    return tag.getContainingFile().getText();
  }

  @Override
  @NotNull
  public String buildAttributesString(@NotNull Map<String, String> attributes,
                                      boolean hasChildren,
                                      int numberInIteration,
                                      int totalIterations, @Nullable String surroundedText) {
    StringBuilder result = new StringBuilder();
    for (Map.Entry<String, String> entry : attributes.entrySet()) {
      String name = entry.getKey();
      String value = ZenCodingUtil.getValue(entry.getValue(), numberInIteration, totalIterations, surroundedText);
      result.append(getAttributeString(name, value));
      result.append(' ');
    }
    return result.toString().trim();
  }

  @Override
  public boolean isMyContext(@NotNull PsiElement context, boolean wrapping) {
    return isMyLanguage(context.getLanguage()) && (wrapping || HtmlTextContextType.isInContext(context));
  }

  protected boolean isMyLanguage(Language language) {
    return language instanceof XMLLanguage;
  }

  @Override
  public String getSuffix() {
    return "html";
  }

  @Override
  public boolean isEnabled() {
    return EmmetOptions.getInstance().isEmmetEnabled();
  }

  @Override
  public boolean isAppliedByDefault(@NotNull PsiElement context) {
    return true;
  }

  private static String getAttributeString(String name, String value) {
    return name + "=\"" + value + '"';
  }

  @SuppressWarnings({"ConstantConditions"})
  private static void closeUnclosingTags(@NotNull XmlTag root) {
    final List<SmartPsiElementPointer<XmlTag>> tagToClose = new ArrayList<>();
    Project project = root.getProject();
    final SmartPointerManager pointerManager = SmartPointerManager.getInstance(project);
    root.accept(new XmlRecursiveElementVisitor() {
      @Override
      public void visitXmlTag(final XmlTag tag) {
        if (!isTagClosed(tag)) {
          tagToClose.add(pointerManager.createSmartPsiElementPointer(tag));
        }
      }
    });
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
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
            ApplicationManager.getApplication().runWriteAction(() -> {
              document.replaceString(offset, tag.getTextRange().getEndOffset(), "/>");
              documentManager.commitDocument(document);
            });
          }
        }
      }
    }
  }

  private static boolean isTagClosed(@NotNull XmlTag tag) {
    ASTNode node = tag.getNode();
    assert node != null;
    final ASTNode emptyTagEnd = XmlChildRole.EMPTY_TAG_END_FINDER.findChild(node);
    final ASTNode endTagEnd = XmlChildRole.CLOSING_TAG_START_FINDER.findChild(node);
    return emptyTagEnd != null || endTagEnd != null;
  }
  
  @Nullable
  @Override
  public Configurable createConfigurable() {
    return new XmlEmmetConfigurable();
  }
}
