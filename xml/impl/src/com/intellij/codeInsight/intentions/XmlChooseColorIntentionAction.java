// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intentions;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.ui.ColorChooserService;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class XmlChooseColorIntentionAction extends PsiElementBaseIntentionAction {
  public XmlChooseColorIntentionAction() {
    setText(XmlBundle.message("intention.color.chooser.dialog"));
  }

  @Override
  public boolean isAvailable(final @NotNull Project project, final Editor editor, final @NotNull PsiElement element) {
    final PsiElement parent = element.getParent();
    return parent instanceof XmlAttributeValue && ColorUtil.fromHex(((XmlAttributeValue)parent).getValue(), null) != null;
  }

  @Override
  public @NotNull String getFamilyName() {
    return getText();
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    chooseColor(editor.getComponent(), element);
  }

  public static void chooseColor(JComponent editorComponent, PsiElement element) {
    String caption = XmlBundle.message("intention.color.chooser.dialog");
    final XmlAttributeValue literal = PsiTreeUtil.getParentOfType(element, XmlAttributeValue.class, false);
    if (literal == null) return;
    final String text = StringUtil.unquoteString(literal.getValue());

    Color oldColor;
    try {
      oldColor = Color.decode(text);
    }
    catch (NumberFormatException e) {
      oldColor = JBColor.GRAY;
    }
    Color color = ColorChooserService.getInstance().showDialog(element.getProject(), editorComponent, caption, oldColor, true);
    if (color == null) return;
    if (!Comparing.equal(color, oldColor)) {
      if (!FileModificationService.getInstance().preparePsiElementForWrite(element)) return;
      final String newText = "#" + ColorUtil.toHex(color);
      final PsiManager manager = literal.getManager();
      final XmlAttribute newAttribute = XmlElementFactory.getInstance(manager.getProject()).createAttribute("name", newText, element);
      final Runnable replaceRunnable = () -> {
        final XmlAttributeValue valueElement = newAttribute.getValueElement();
        assert valueElement != null;
        literal.replace(valueElement);
      };
      WriteCommandAction.writeCommandAction(element.getProject()).withName(caption).run(() -> replaceRunnable.run());
    }
  }
}

