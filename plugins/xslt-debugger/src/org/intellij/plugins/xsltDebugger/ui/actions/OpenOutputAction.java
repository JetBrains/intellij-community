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
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.vfs.VcsFileSystem;
import com.intellij.openapi.vcs.vfs.VcsVirtualFile;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

@SuppressWarnings({ "ComponentNotRegistered" })
public class OpenOutputAction extends AnAction {
  private final AdditionalTabComponent myConsole;

  public OpenOutputAction(AdditionalTabComponent console) {
    super("Open in Editor");
    myConsole = console;
    getTemplatePresentation().setIcon(IconLoader.getIcon("/actions/export.png"));
  }

  public void actionPerformed(AnActionEvent e) {
    final Editor editor = PlatformDataKeys.EDITOR.getData(DataManager.getInstance().getDataContext(myConsole.getComponent()));
    if (editor != null) {
      try {
        final byte[] content = editor.getDocument().getText().getBytes("UTF-8");
        final String extension = "xml"; // TODO: get from output type
        final VcsVirtualFile file = new VcsVirtualFile("XSLT Output." + extension, content, null, VcsFileSystem.getInstance()) {
          @Override
          public Charset getCharset() {
            return Charset.forName("UTF-8");
          }
        };
        FileEditorManager.getInstance(PlatformDataKeys.PROJECT.getData(e.getDataContext())).openFile(file, true);
      } catch (UnsupportedEncodingException e1) {
        throw new AssertionError(e);
      }
    }
  }

  public void update(AnActionEvent e) {
    final Editor editor = PlatformDataKeys.EDITOR.getData(DataManager.getInstance().getDataContext(myConsole.getComponent()));
    e.getPresentation().setEnabled(editor != null && editor.getDocument().getTextLength() > 0);
  }
}