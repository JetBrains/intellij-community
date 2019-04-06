// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties.editor;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Batkovich
 */
public class GotoNextIncompletePropertyAction extends AnAction {
  private final static Logger LOG = Logger.getInstance(GotoNextIncompletePropertyAction.class);

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final ResourceBundleEditor editor = (ResourceBundleEditor)e.getData(PlatformDataKeys.FILE_EDITOR);
    LOG.assertTrue(editor != null);
    editor.selectNextIncompleteProperty();
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(e.getData(PlatformDataKeys.FILE_EDITOR) instanceof ResourceBundleEditor);
  }
}
