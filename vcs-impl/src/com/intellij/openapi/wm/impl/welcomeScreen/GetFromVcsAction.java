package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.vcs.CheckoutProvider;
import com.intellij.openapi.vcs.checkout.CheckoutAction;
import com.intellij.ui.UIBundle;

import java.util.Arrays;
import java.util.Comparator;

public class GetFromVcsAction extends WelcomePopupAction{

  protected void fillActions(DefaultActionGroup group) {
    final CheckoutProvider[] providers = Extensions.getExtensions(CheckoutProvider.EXTENSION_POINT_NAME);
    Arrays.sort(providers, new Comparator<CheckoutProvider>() {
      public int compare(final CheckoutProvider o1, final CheckoutProvider o2) {
        // not strict but will do
        return o1.getVcsName().compareTo(o2.getVcsName());
      }
    });
    for (CheckoutProvider provider : providers) {
      group.add(new CheckoutAction(provider));
    }
  }

  protected String getCaption() {
    return UIBundle.message("welcome.screen.get.from.vcs.action.checkout.from.list.popup.title");
  }

  protected String getTextForEmpty() {
    return UIBundle.message("welcome.screen.get.from.vcs.action.no.vcs.plugins.with.check.out.action.installed.action.name");
  }
}
