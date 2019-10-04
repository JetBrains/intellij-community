// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.browsers.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.browsers.WebBrowser;
import com.intellij.ide.browsers.WebBrowserManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diff.impl.DiffUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.util.CachedValueProvider;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class OpenInBrowserBaseGroupAction extends ComputableActionGroup {
  private OpenFileInDefaultBrowserAction myDefaultBrowserAction;

  protected OpenInBrowserBaseGroupAction(boolean popup) {
    super(popup);

    Presentation p = getTemplatePresentation();
    p.setText("Open in _Browser");
    p.setDescription("Open selected file in browser");
    p.setIcon(AllIcons.Nodes.PpWeb);
  }

  @NotNull
  @Override
  protected final CachedValueProvider<AnAction[]> createChildrenProvider(@NotNull final ActionManager actionManager) {
    return () -> {
      List<WebBrowser> browsers = WebBrowserManager.getInstance().getBrowsers();
      boolean addDefaultBrowser = isPopup();
      int offset = addDefaultBrowser ? 1 : 0;
      AnAction[] actions = new AnAction[browsers.size() + offset];

      if (addDefaultBrowser) {
        if (myDefaultBrowserAction == null) {
          myDefaultBrowserAction = new OpenFileInDefaultBrowserAction();
          myDefaultBrowserAction.getTemplatePresentation().setText("Default");
          myDefaultBrowserAction.getTemplatePresentation().setIcon(AllIcons.Nodes.PpWeb);
        }
        actions[0] = myDefaultBrowserAction;
      }

      for (int i = 0, size = browsers.size(); i < size; i++) {
        actions[i + offset] = new BaseOpenInBrowserAction(browsers.get(i));
      }

      return CachedValueProvider.Result.create(actions, WebBrowserManager.getInstance());
    };
  }

  public static final class OpenInBrowserGroupAction extends OpenInBrowserBaseGroupAction {
    public OpenInBrowserGroupAction() {
      super(true);
    }
  }

  public static final class OpenInBrowserEditorContextBarGroupAction extends OpenInBrowserBaseGroupAction {
    public OpenInBrowserEditorContextBarGroupAction() {
      super(false);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      Editor editor = e.getData(CommonDataKeys.EDITOR);
      final WebBrowserManager browserManager = WebBrowserManager.getInstance();
      e.getPresentation().setVisible(browserManager.isShowBrowserHover() && !browserManager.getActiveBrowsers().isEmpty() &&
                                     editor != null && !DiffUtil.isDiffEditor(editor));
    }
  }
}