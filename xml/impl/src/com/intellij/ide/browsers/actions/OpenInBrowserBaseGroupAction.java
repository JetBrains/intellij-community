package com.intellij.ide.browsers.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.browsers.OpenInBrowserRequest;
import com.intellij.ide.browsers.WebBrowser;
import com.intellij.ide.browsers.WebBrowserManager;
import com.intellij.ide.browsers.WebBrowserUrlProvider;
import com.intellij.ide.browsers.impl.WebBrowserServiceImpl;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.util.Pair;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class OpenInBrowserBaseGroupAction extends ComputableActionGroup {
  private OpenFileInDefaultBrowserAction myDefaultBrowserAction;

  protected OpenInBrowserBaseGroupAction(boolean popup) {
    super(popup);
  }

  @Nullable
  public static Pair<OpenInBrowserRequest, WebBrowserUrlProvider> doUpdate(@NotNull AnActionEvent event) {
    OpenInBrowserRequest request = OpenFileInDefaultBrowserAction.createRequest(event.getDataContext());
    boolean applicable = false;
    WebBrowserUrlProvider provider = null;
    if (request != null) {
      applicable = HtmlUtil.isHtmlFile(request.getFile()) && !(request.getVirtualFile() instanceof LightVirtualFile);
      if (!applicable) {
        provider = WebBrowserServiceImpl.getProvider(request);
        applicable = provider != null;
      }
    }

    Presentation presentation = event.getPresentation();
    presentation.setVisible(applicable);
    presentation.setVisible(applicable);
    return applicable ? Pair.create(request, provider) : null;
  }

  @NotNull
  @Override
  protected AnAction[] computeChildren(@NotNull ActionManager manager) {
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
      WebBrowser browser = browsers.get(i);
      actions[i + offset] = new BaseWebBrowserAction(browser);
    }
    return actions;
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
  }
}