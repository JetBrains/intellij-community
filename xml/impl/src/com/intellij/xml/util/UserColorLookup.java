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
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlToken;
import com.intellij.ui.ColorChooser;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author maxim
 */
public class UserColorLookup extends LookupElementDecorator<LookupElement> {
  private static final String COLOR_STRING = XmlBundle.message("choose.color.in.color.lookup");

  public UserColorLookup() {
    super(PrioritizedLookupElement.withPriority(LookupElementBuilder.create(COLOR_STRING).setInsertHandler(new InsertHandler<LookupElement>() {
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

    Color color = ColorChooser
      .chooseColor(WindowManager.getInstance().suggestParentWindow(context.getProject()), XmlBundle.message("choose.color.dialog.title"),
                   myColorAtCaret, true);

    if (color != null) {
      String s = Integer.toHexString(color.getRGB() & 0xFFFFFF);
      if (s.length() != 6) {
        StringBuffer buf = new StringBuffer(s);
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
    Color myColorAtCaret = null;

    if (!(element instanceof XmlToken)) return null;

    final String text = element.getText();

    if (text.length() > 0 && text.charAt(0) == '#') {
      myColorAtCaret = decodeColor(text);
    }
    else {
      final String hexCodeForColorName = ColorSampleLookupValue.getHexCodeForColorName(text);
      if (hexCodeForColorName != null) myColorAtCaret = decodeColor(hexCodeForColorName);
    }

    return myColorAtCaret;
  }

  private static Color decodeColor(final String text) {
    Color myColorAtCaret = null;
    try {
      String s = text.substring(1);

      if (s.length() == 3) { // css color short hand
        StringBuilder buf = new StringBuilder(6);
        buf.append(s.charAt(0)).append(s.charAt(0));
        buf.append(s.charAt(1)).append(s.charAt(1));
        buf.append(s.charAt(2)).append(s.charAt(2));

        s = buf.toString();
      }
      myColorAtCaret = Color.decode("0x" + s);
    }
    catch (Exception ignore) {
    }
    return myColorAtCaret;
  }

}
