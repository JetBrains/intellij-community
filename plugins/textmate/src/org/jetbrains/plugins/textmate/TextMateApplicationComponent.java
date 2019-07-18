package org.jetbrains.plugins.textmate;

import com.intellij.openapi.Disposable;

final class TextMateApplicationComponent implements Disposable {
  private final TextMateService myTextMateService;

  TextMateApplicationComponent() {
    myTextMateService = TextMateService.getInstance();
    myTextMateService.reloadThemesFromDisk();
    myTextMateService.registerEnabledBundles(true);
  }

  @Override
  public void dispose() {
    myTextMateService.unregisterAllBundles(true);
    myTextMateService.clearListeners();
  }
}
