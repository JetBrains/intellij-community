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
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.impl.DelegateColorScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapAppliancePlaces;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
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

  public static Editor createPythonCodeEditor(@NotNull Project project, @NotNull String text) {
    EditorEx editor =
      (EditorEx)EditorFactory.getInstance().createEditor(createPythonCodeDocument(project, text), project, PythonFileType.INSTANCE, false);
    noScrolling(editor);
    ConsoleViewUtil.setupConsoleEditor(editor, false, false);
    return editor;
  }

  private static void noScrolling(EditorEx editor) {
    editor.getScrollPane().setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
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

  public static JComponent createPromptComponent(@NotNull String promptText) {
    JLabel promptLabel = new JLabel(promptText);
    promptLabel.setFont(promptLabel.getFont().deriveFont(Font.BOLD));
    promptLabel.setForeground(JBColor.BLUE);
    promptLabel.setBackground(getBackground());
    return promptLabel;
  }

  public static Color getBackground() {
    return EditorColorsManager.getInstance().getGlobalScheme().getDefaultBackground();
  }
}
