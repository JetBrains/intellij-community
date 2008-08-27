package com.intellij.ide.browsers;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author spleaner
 */
public abstract class WebBrowserUrlProvider {
  public static ExtensionPointName<WebBrowserUrlProvider> EXTENSION_POINT_NAME =
      ExtensionPointName.create("com.intellij.webBrowserUrlProvider");

  /**
   * Browser exceptions are printed in Error Dialog when user presses any browser button.
   */
  public static class BrowserException extends Exception {
    public BrowserException(final String message) {
      super(message);
    }
  }

  @NotNull
  public abstract String getUrl(@NotNull PsiFile file, @NotNull Project project, boolean shiftDown) throws Exception;

  public abstract boolean isAvailableFor(@NotNull PsiFile file);
}
