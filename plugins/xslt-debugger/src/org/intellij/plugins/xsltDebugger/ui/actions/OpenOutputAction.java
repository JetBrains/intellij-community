/*
 * Copyright 2002-2007 Sascha Weinreuter
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
package org.intellij.plugins.xsltDebugger.ui.actions;

import com.intellij.diagnostic.logging.AdditionalTabComponent;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class OpenOutputAction extends AnAction {
  private final AdditionalTabComponent myConsole;

  public OpenOutputAction(AdditionalTabComponent console) {
    super(LangBundle.message("button.open.in.editor"));
    myConsole = console;
    getTemplatePresentation().setIcon(AllIcons.ToolbarDecorator.Export);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Editor editor = CommonDataKeys.EDITOR.getData(DataManager.getInstance().getDataContext(myConsole.getComponent()));
    if (editor != null) {
      final String extension = "xml"; // TODO: get from output type
      final VirtualFile file = new LightVirtualFile("XSLT Output." + extension, editor.getDocument().getText()) {
        @NotNull
        @Override
        public Charset getCharset() {
          return StandardCharsets.UTF_8;
        }
      };
      FileEditorManager.getInstance(e.getProject()).openFile(file, true);
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    final Editor editor = CommonDataKeys.EDITOR.getData(DataManager.getInstance().getDataContext(myConsole.getComponent()));
    e.getPresentation().setEnabled(editor != null && editor.getDocument().getTextLength() > 0);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }
}
