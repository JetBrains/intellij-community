// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.util;

import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.project.Project;
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
  private static final Function<Color, String> COLOR_TO_STRING_CONVERTER = color -> '#' + ColorUtil.toHex(color);

  public UserColorLookup() {
    this(COLOR_TO_STRING_CONVERTER);
  }

  public UserColorLookup(final Function<? super Color, String> colorToStringConverter) {
    this(colorToStringConverter, ColorSampleLookupValue.HIGH_PRIORITY);
  }

  public UserColorLookup(final Function<? super Color, String> colorToStringConverter, int priority) {
    super(PrioritizedLookupElement.withPriority(LookupElementBuilder.create(getColorString()).withInsertHandler(
      new InsertHandler<LookupElement>() {
        @Override
        public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElement item) {
          handleUserSelection(context, colorToStringConverter);
        }
      }), priority));
  }

  private static void handleUserSelection(InsertionContext context, @NotNull Function<? super Color, String> colorToStringConverter) {
    Project project = context.getProject();
    Editor editor = context.getEditor();
    int startOffset = context.getStartOffset();

    context.getDocument().deleteString(startOffset, context.getTailOffset());
    PsiElement element = context.getFile().findElementAt(editor.getCaretModel().getOffset());
    Color myColorAtCaret = element instanceof XmlToken ? getColorFromElement(element) : null;

    context.setLaterRunnable(() -> {
      if (editor.isDisposed() || project.isDisposed()) return;
      List<ColorPickerListener> listeners = ColorPickerListenerFactory.createListenersFor(element);
      Color color = ColorChooser.chooseColor(project, WindowManager.getInstance().suggestParentWindow(project),
                                             XmlBundle.message("choose.color.dialog.title"), myColorAtCaret, true, listeners, true);
      if (color != null) {
        WriteCommandAction.runWriteCommandAction(project, () -> {
          editor.getCaretModel().moveToOffset(startOffset);
          EditorModificationUtil.insertStringAtCaret(editor, colorToStringConverter.fun(color));
        });
      }
    });
  }

  @Nullable
  public static Color getColorFromElement(final PsiElement element) {
    if (!(element instanceof XmlToken)) return null;

    return ColorMap.getColor(element.getText());
  }

  private static String getColorString() {
    return XmlBundle.message("choose.color.in.color.lookup");
  }
}
