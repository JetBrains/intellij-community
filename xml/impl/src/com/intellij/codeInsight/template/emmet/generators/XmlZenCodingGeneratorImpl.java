// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet.generators;

import com.intellij.application.options.emmet.EmmetOptions;
import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.codeInsight.template.HtmlTextContextType;
import com.intellij.codeInsight.template.emmet.ZenCodingUtil;
import com.intellij.ide.highlighter.XHtmlFileType;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class XmlZenCodingGeneratorImpl extends XmlZenCodingGenerator {
  public static final XmlZenCodingGeneratorImpl INSTANCE = new XmlZenCodingGeneratorImpl();

  private static boolean isTrueXml(FileType type) {
    return type == XHtmlFileType.INSTANCE || type == StdFileTypes.JSPX || type == XmlFileType.INSTANCE;
  }

  @Override
  public @NotNull String toString(@NotNull XmlTag tag,
                                  @NotNull Map<String, String> attributes,
                                  boolean hasChildren,
                                  @NotNull PsiElement context) {
    FileType fileType = context.getContainingFile().getFileType();
    PsiFile file = tag.getContainingFile();
    if (isTrueXml(fileType)) {
      closeUnclosingTags(tag);
    }

    return file.getText();
  }

  @Override
  public @NotNull String buildAttributesString(@NotNull Map<String, String> attributes,
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
  public boolean isMyContext(@NotNull CustomTemplateCallback callback, boolean wrapping) {
    return isMyContext(callback.getContext(), wrapping);
  }

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

  private static void closeUnclosingTags(@NotNull XmlTag root) {
    final List<SmartPsiElementPointer<XmlTag>> tagToClose = new ArrayList<>();
    Project project = root.getProject();
    final SmartPointerManager pointerManager = SmartPointerManager.getInstance(project);
    root.accept(new XmlRecursiveElementVisitor() {
      @Override
      public void visitXmlTag(final @NotNull XmlTag tag) {
        if (!isTagClosed(tag)) {
          tagToClose.add(pointerManager.createSmartPsiElementPointer(tag));
        }
      }
    });
    for (SmartPsiElementPointer<XmlTag> pointer : tagToClose) {
      XmlTag element = pointer.getElement();
      if (element == null) continue;

      String elementText = element.getText();
      if (!elementText.endsWith(">")) continue;

      PsiFile text = PsiFileFactory.getInstance(root.getProject())
        .createFileFromText("dummy.html", root.getLanguage(), StringUtil.trimEnd(element.getText(), ">") + "/>", false, true);
      XmlTag newTag = PsiTreeUtil.findChildOfType(text, XmlTag.class);
      if (newTag != null) {
        element.replace(newTag);
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
}
