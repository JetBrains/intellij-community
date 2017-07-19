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
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ipnb.editor.panels.code.IpnbCodeSourcePanel;
import org.jetbrains.plugins.ipnb.psi.IpnbPyFragment;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseWheelListener;
import java.util.List;

/**
 * @author traff
 */
public class IpnbEditorUtil {
  public enum PromptType {In, Out, None}

  public static final Dimension PROMPT_SIZE = new Dimension(80, 30);

  public static Editor createPythonCodeEditor(@NotNull final Project project, @NotNull final IpnbCodeSourcePanel codeSourcePanel) {
    final EditorFactory editorFactory = EditorFactory.getInstance();
    assert editorFactory != null;
    final String text = codeSourcePanel.getCell().getSourceAsString();
    final Module module = ProjectRootManagerEx.getInstanceEx(project).getFileIndex()
      .getModuleForFile(codeSourcePanel.getIpnbCodePanel().getFileEditor().getVirtualFile());
    final IpnbPyFragment fragment = new IpnbPyFragment(project, text, true, codeSourcePanel);
    fragment.putUserData(ModuleUtilCore.KEY_MODULE, module);
    final Document document = PsiDocumentManager.getInstance(project).getDocument(fragment);
    assert document != null;
    EditorEx editor = (EditorEx)editorFactory.createEditor(document, project, fragment.getVirtualFile(), false);
    editor.setFile(fragment.getVirtualFile());
    setupEditor(editor);
    return editor;
  }

  private static void setupEditor(@NotNull final EditorEx editor) {
    editor.setBackgroundColor(getEditablePanelBackground());
    noScrolling(editor);
    editor.getScrollPane().setBorder(null);
    editor.setContextMenuGroupId(null);
    final EditorSettings editorSettings = editor.getSettings();
    editorSettings.setLineMarkerAreaShown(false);
    editorSettings.setIndentGuidesShown(false);
    editorSettings.setLineNumbersShown(false);
    editorSettings.setFoldingOutlineShown(false);
    editorSettings.setAdditionalPageAtBottom(false);
    editorSettings.setAdditionalColumnsCount(0);
    editorSettings.setAdditionalLinesCount(0);
    editorSettings.setRightMarginShown(false);
  }

  public static Color getEditablePanelBackground() {
    return !UIUtil.isUnderDarcula() ? Gray._247 : EditorColorsManager.getInstance().getGlobalScheme().getColor(
      EditorColors.GUTTER_BACKGROUND);
  }

  public static Editor createPlainCodeEditor(@NotNull final Project project, @NotNull final String text) {
    final EditorFactory editorFactory = EditorFactory.getInstance();
    assert editorFactory != null;
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

  public static JLabel createPromptComponent(@Nullable Integer promptNumber, @NotNull final PromptType type) {
    final String promptText = prompt(promptNumber, type);
    JLabel promptLabel = new JLabel(promptText);
    promptLabel.setHorizontalAlignment(SwingConstants.RIGHT);
    promptLabel.setMinimumSize(PROMPT_SIZE);
    promptLabel.setPreferredSize(PROMPT_SIZE);
    final Font font = promptLabel.getFont();
    assert font != null;
    promptLabel.setFont(font.deriveFont(Font.BOLD));
    final JBColor darkRed = new JBColor(new Color(210, 30, 50), new Color(210, 30, 50));
    promptLabel.setForeground(type == PromptType.In ? JBColor.BLUE : darkRed);
    promptLabel.setBackground(getBackground());
    return promptLabel;
  }

  public static String prompt(@Nullable Integer promptNumber, @NotNull final PromptType type) {
    if (type == PromptType.In) {
      return promptNumber == null ? type + " [ ]:" : promptNumber > 0 ? String.format(type + " [%d]:", promptNumber) : type + " [*]:";
    }
    else if (type == PromptType.Out) {
      return promptNumber == null ? type + "[ ]:" : promptNumber > 0 ? String.format(type + "[%d]:", promptNumber) : type + "[*]:";
    }
    return "";
  }


  public static Color getBackground() {
    return EditorColorsManager.getInstance().getGlobalScheme().getDefaultBackground();
  }
}
