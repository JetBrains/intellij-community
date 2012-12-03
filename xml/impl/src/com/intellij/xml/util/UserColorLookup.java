/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.xml.util;

import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.codeInsight.lookup.LookupValueWithPriority;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlToken;
import com.intellij.ui.ColorChooser;
import com.intellij.ui.ColorPickerListener;
import com.intellij.ui.ColorPickerListenerFactory;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author maxim
 */
public class UserColorLookup extends LookupElementDecorator<LookupElement> {
  private static final String COLOR_STRING = XmlBundle.message("choose.color.in.color.lookup");

  public UserColorLookup() {
    super(PrioritizedLookupElement.withPriority(LookupElementBuilder.create(COLOR_STRING).withInsertHandler(
      new InsertHandler<LookupElement>() {
        @Override
        public void handleInsert(InsertionContext context, LookupElement item) {
          handleUserSelection(context);
        }
      }), LookupValueWithPriority.HIGH));
  }

  private static void handleUserSelection(InsertionContext context) {
    Color myColorAtCaret = null;

    Editor selectedTextEditor = context.getEditor();
    PsiElement element = context.getFile().findElementAt(selectedTextEditor.getCaretModel().getOffset());

    if (element instanceof XmlToken) {
      myColorAtCaret = getColorFromElement(element);
    }

    context.getDocument().deleteString(context.getStartOffset(), context.getTailOffset());

    ColorPickerListener[] listeners = ColorPickerListenerFactory.createListenersFor(element);
    Color color = ColorChooser.chooseColor(WindowManager.getInstance().suggestParentWindow(context.getProject()),
                                           XmlBundle.message("choose.color.dialog.title"), myColorAtCaret, true, listeners);

    if (color != null) {
      String s = Integer.toHexString(color.getRGB() & 0xFFFFFF);
      if (s.length() != 6) {
        StringBuilder buf = new StringBuilder(s);
        for (int i = 6 - buf.length(); i > 0; --i) {
          buf.insert(0, '0');
        }
        s = buf.toString();
      }
      s = "#" + s;
      context.getDocument().insertString(context.getStartOffset(), s);
      context.getEditor().getCaretModel().moveToOffset(context.getTailOffset());
    }
  }

  @Nullable
  public static Color getColorFromElement(final PsiElement element) {
    if (!(element instanceof XmlToken)) return null;

    return ColorSampleLookupValue.getColor(element.getText());
  }
}
