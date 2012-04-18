/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.codeInsight.intentions;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.ui.ColorChooser;
import com.intellij.ui.ColorUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class XmlChooseColorIntentionAction extends PsiElementBaseIntentionAction {
  public XmlChooseColorIntentionAction() {
    setText(CodeInsightBundle.message("intention.color.chooser.dialog"));
  }

  public boolean isAvailable(@NotNull final Project project, final Editor editor, @NotNull final PsiElement element) {
    final PsiElement parent = element.getParent();
    return parent instanceof XmlAttributeValue && ColorUtil.fromHex(((XmlAttributeValue)parent).getValue(), null) != null;
  }

  @NotNull
  public String getFamilyName() {
    return getText();
  }

  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    if (!CodeInsightUtilBase.prepareFileForWrite(file)) return;
    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    invokeForLiteral(editor.getComponent(), element);
  }

  private void invokeForLiteral(JComponent editorComponent, PsiElement element) {
    final XmlAttributeValue literal = PsiTreeUtil.getParentOfType(element, XmlAttributeValue.class);
    if (literal == null) return;
    final String text = StringUtil.unquoteString(literal.getValue());
    final String hexPrefix = text.startsWith("#") ? "#" : "";

    Color oldColor;
    try {
      oldColor = Color.decode(text);
    }
    catch (NumberFormatException e) {
      oldColor = Color.GRAY;
    }
    Color color = ColorChooser.chooseColor(editorComponent, getText(), oldColor, true);
    if (color == null) return;
    if (!Comparing.equal(color, oldColor)) {
      final String newText =  hexPrefix + ColorUtil.toHex(color);
      final PsiManager manager = literal.getManager();
      final XmlAttribute newAttribute = XmlElementFactory.getInstance(manager.getProject()).createXmlAttribute("name", newText);
      literal.replace(newAttribute.getValueElement());
    }
  }
}

