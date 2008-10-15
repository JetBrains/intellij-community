package com.intellij.ide.browsers;

import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author spleaner
 */
public abstract class WebBrowserUrlProvider {
  /**
   * Browser exceptions are printed in Error Dialog when user presses any browser button.
   */
  public static class BrowserException extends Exception {
    public BrowserException(final String message) {
      super(message);
    }
  }

  @NotNull
  public abstract String getUrl(@NotNull PsiFile file, boolean shiftDown) throws Exception;
}
