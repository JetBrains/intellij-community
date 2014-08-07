/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.ui.ColorPickerListener;
import com.intellij.ui.ColorPickerListenerFactory;
import com.intellij.ui.ColorUtil;
import com.intellij.util.Function;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

/**
 * @author maxim
 */
public class UserColorLookup extends LookupElementDecorator<LookupElement> {
  private static final String COLOR_STRING = XmlBundle.message("choose.color.in.color.lookup");
  private static final Function<Color,String> COLOR_TO_STRING_CONVERTER = new Function<Color, String>() {
    @Override
    public String fun(Color color) {
      return '#' + ColorUtil.toHex(color);
    }
  };

  public UserColorLookup() {
    this(COLOR_TO_STRING_CONVERTER);
  }

  public UserColorLookup(final Function<Color, String> colorToStringConverter) {
    this(colorToStringConverter, LookupValueWithPriority.HIGH);
  }

  public UserColorLookup(final Function<Color, String> colorToStringConverter, int priority) {
    super(PrioritizedLookupElement.withPriority(LookupElementBuilder.create(COLOR_STRING).withInsertHandler(
      new InsertHandler<LookupElement>() {
        @Override
        public void handleInsert(InsertionContext context, LookupElement item) {
          handleUserSelection(context, colorToStringConverter);
        }
      }), priority));
  }

  private static void handleUserSelection(InsertionContext context, @NotNull Function<Color, String> colorToStringConverter) {
    Color myColorAtCaret = null;

    Editor selectedTextEditor = context.getEditor();
    PsiElement element = context.getFile().findElementAt(selectedTextEditor.getCaretModel().getOffset());

    if (element instanceof XmlToken) {
      myColorAtCaret = getColorFromElement(element);
    }

    context.getDocument().deleteString(context.getStartOffset(), context.getTailOffset());

    List<ColorPickerListener> listeners = ColorPickerListenerFactory.createListenersFor(element);
    Color color = ColorChooser.chooseColor(WindowManager.getInstance().suggestParentWindow(context.getProject()),
                                           XmlBundle.message("choose.color.dialog.title"), myColorAtCaret, true, listeners, true);

    if (color != null) {
      String colorString = colorToStringConverter.fun(color);
      context.getDocument().insertString(context.getStartOffset(), colorString);
      context.getEditor().getCaretModel().moveToOffset(context.getTailOffset());
    }
  }

  @Nullable
  public static Color getColorFromElement(final PsiElement element) {
    if (!(element instanceof XmlToken)) return null;

    return ColorMap.getColor(element.getText());
  }
}
