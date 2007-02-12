package com.intellij.openapi.vcs.checkout;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.vcs.CheckoutProvider;
import org.jetbrains.annotations.Nullable;

public class CheckoutActionGroup extends ActionGroup {

  private AnAction[] myChildren;

  public void update(AnActionEvent e) {
    super.update(e);
    final CheckoutProvider[] providers = Extensions.getExtensions(CheckoutProvider.EXTENSION_POINT_NAME);
    if (providers.length == 0) {
      e.getPresentation().setVisible(false);
    }
  }

  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    if (myChildren == null) {
      final CheckoutProvider[] providers = Extensions.getExtensions(CheckoutProvider.EXTENSION_POINT_NAME);
      myChildren = new AnAction[providers.length];
      for (int i = 0; i < providers.length; i++) {
        CheckoutProvider provider = providers[i];
        myChildren[i] = new CheckoutAction(provider);
      }
    }
    return myChildren;
  }
}
