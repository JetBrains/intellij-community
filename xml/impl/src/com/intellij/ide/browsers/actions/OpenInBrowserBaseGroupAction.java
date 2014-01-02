package com.intellij.ide.browsers.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.browsers.WebBrowser;
import com.intellij.ide.browsers.WebBrowserManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.ComputableActionGroup;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class OpenInBrowserBaseGroupAction extends ComputableActionGroup {
  private OpenFileInDefaultBrowserAction myDefaultBrowserAction;

  protected OpenInBrowserBaseGroupAction(boolean popup) {
    super(popup);
  }

  @NotNull
  @Override
  protected AnAction[] computeChildren(@NotNull ActionManager manager) {
    List<AnAction> actionsByEP = new SmartList<AnAction>();
    for (OpenInBrowserActionProducer actionProducer : OpenInBrowserActionProducer.EP_NAME.getExtensions()) {
      actionsByEP.addAll(actionProducer.getActions());
    }

    List<WebBrowser> browsers = WebBrowserManager.getInstance().getBrowsers();
    boolean addDefaultBrowser = isPopup();
    int offset = addDefaultBrowser ? 1 : 0;
    AnAction[] actions = new AnAction[browsers.size() + offset + actionsByEP.size()];

    if (addDefaultBrowser) {
      if (myDefaultBrowserAction == null) {
        myDefaultBrowserAction = new OpenFileInDefaultBrowserAction();
        myDefaultBrowserAction.getTemplatePresentation().setText("Default");
        myDefaultBrowserAction.getTemplatePresentation().setIcon(AllIcons.Nodes.PpWeb);
      }
      actions[0] = myDefaultBrowserAction;
    }

    for (int i = 0, size = browsers.size(); i < size; i++) {
      actions[i + offset] = new BaseWebBrowserAction(browsers.get(i));
    }

    ArrayUtil.copy(actionsByEP, actions, offset + browsers.size());

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