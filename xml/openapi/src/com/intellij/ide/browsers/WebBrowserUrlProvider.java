package com.intellij.ide.browsers;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author spleaner
 */
public abstract class WebBrowserUrlProvider {
  public static ExtensionPointName<WebBrowserUrlProvider> EP_NAME = ExtensionPointName.create("com.intellij.webBrowserUrlProvider");

  /**
   * Browser exceptions are printed in Error Dialog when user presses any browser button.
   */
  public static class BrowserException extends Exception {
    public BrowserException(final String message) {
      super(message);
    }
  }

  /**
   * Invariant: element has not null containing psi file with not null virtual file 
   */
  @NotNull
  public abstract String getUrl(@NotNull PsiElement element, boolean shiftDown) throws Exception;

  /**
   * Invariant: element has not null containing psi file with not null virtual file
   */
  public abstract boolean canHandleElement(@NotNull final PsiElement element);
}
