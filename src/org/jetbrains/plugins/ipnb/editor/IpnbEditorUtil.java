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
package org.jetbrains.plugins.ipnb.editor;

import com.google.common.collect.Lists;
import com.intellij.execution.impl.ConsoleViewUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.psi.impl.PyExpressionCodeFragmentImpl;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseWheelListener;
import java.util.List;

/**
 * @author traff
 */
public class IpnbEditorUtil {
  public enum PromptType { In, Out, None }

  public static Dimension PROMPT_SIZE = new Dimension(80, 30);
  public static int PANEL_WIDTH = 900;

  public static Editor createPythonCodeEditor(@NotNull Project project, @NotNull String text) {
    EditorEx editor = (EditorEx)EditorFactory.getInstance().createEditor(createPythonCodeDocument(project, text), project,
                                                                         PythonFileType.INSTANCE, false);

    setupEditor(editor);
    return editor;
  }

  private static void setupEditor(@NotNull final EditorEx editor) {
    if (!UIUtil.isUnderDarcula())
      editor.setBackgroundColor(Gray._247);
    noScrolling(editor);
    editor.getScrollPane().setBorder(null);
    ConsoleViewUtil.setupConsoleEditor(editor, false, false);
  }

  public static Editor createPlainCodeEditor(@NotNull final Project project, @NotNull final String text) {
    final EditorFactory editorFactory = EditorFactory.getInstance();
    final Document document = editorFactory.createDocument(text);
    EditorEx editor = (EditorEx)editorFactory.createEditor(document, project);
    setupEditor(editor);
    return editor;
  }

  private static void noScrolling(EditorEx editor) {
    editor.getScrollPane().setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
    editor.getScrollPane().setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    editor.getScrollPane().setWheelScrollingEnabled(false);

    List<MouseWheelListener> listeners = Lists.newArrayList(editor.getScrollPane().getMouseWheelListeners());
    for (MouseWheelListener l : listeners) {
      editor.getScrollPane().removeMouseWheelListener(l);
    }
  }

  @NotNull
  public static Document createPythonCodeDocument(@NotNull final Project project,
                                                  @NotNull String text) {
    text = text.trim();
    final PyExpressionCodeFragmentImpl fragment = new PyExpressionCodeFragmentImpl(project, "code.py", text, true);

    return PsiDocumentManager.getInstance(project).getDocument(fragment);
  }

  public static JComponent createPromptComponent(Integer promptNumber, @NotNull final PromptType type) {
    final String promptText = prompt(promptNumber, type);
    JLabel promptLabel = new JLabel(promptText);
    promptLabel.setHorizontalAlignment(SwingConstants.RIGHT);
    promptLabel.setPreferredSize(PROMPT_SIZE);
    promptLabel.setFont(promptLabel.getFont().deriveFont(Font.BOLD));
    final JBColor darkRed = new JBColor(new Color(210, 30, 50), new Color(210, 30, 50));
    promptLabel.setForeground(type == PromptType.In ? JBColor.BLUE : darkRed);
    promptLabel.setBackground(getBackground());
    return promptLabel;
  }

  protected static String prompt(Integer promptNumber, @NotNull final PromptType type) {
    if (type == PromptType.In)
      return promptNumber == null ? type + "[ ]:" : String.format(type + " [%d]:", promptNumber);
    else if (type == PromptType.Out)
      return promptNumber == null ? type + "[ ]:" : String.format(type + "[%d]:", promptNumber);
    return "";
  }


  public static Color getBackground() {
    return EditorColorsManager.getInstance().getGlobalScheme().getDefaultBackground();
  }
}
