// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlEntityRef;
import com.intellij.psi.xml.XmlText;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class TextToCDataIntention implements IntentionAction {
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getText() {
    return getFamilyName();
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getFamilyName() {
    return XmlBundle.message("convert.text.to.cdata");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return file.getLanguage().isKindOf(XMLLanguage.INSTANCE) &&
           getText(editor, file) != null &&
           !file.getLanguage().isKindOf(HTMLLanguage.INSTANCE);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiElement textElement = getText(editor, file);
    if (textElement == null) return;


    List<PsiElement> texts = new ArrayList<>();
    PsiElement curr = textElement.getPrevSibling();
    while (isText(curr)) {
      texts.add(0, curr);
      curr = curr.getPrevSibling();
    }
    texts.add(textElement);
    curr = textElement.getNextSibling();
    while (isText(curr)) {
      texts.add(curr);
      curr = curr.getNextSibling();
    }

    StringBuilder text = new StringBuilder();
    for (PsiElement element : texts) {
      text.append(StringUtil.unescapeXmlEntities(element.getText()));
    }
    int start = 0;
    while (true) {
      int pos = text.indexOf("]]>", start);
      if (pos < 0) break;
      text.insert(pos + 1, "]]><![CDATA[");
      start = pos + 2;
    }

    int begin = texts.get(0).getTextRange().getStartOffset();
    String replacement = "<![CDATA[" + text + "]]>";
    editor.getDocument().replaceString(begin, texts.get(texts.size() - 1).getTextRange().getEndOffset(), replacement);
    editor.getCaretModel().moveToOffset(begin + replacement.length());
  }

  private static PsiElement getText(Editor editor, PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    PsiElement parent = element != null ? element.getParent() : null;
    if (isText(parent)) return parent;

    element = file.findElementAt(offset - 1);
    parent = element != null ? element.getParent() : null;
    if (isText(parent)) return parent;
    return null;
  }

  private static boolean isText(PsiElement element) {
    if (element instanceof XmlText) return true;
    if (element instanceof XmlEntityRef && element.getParent() instanceof XmlText) return true;
    return false;
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
