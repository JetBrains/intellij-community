package com.intellij.ide.browsers;

import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.lang.Language;
import com.intellij.lang.xhtml.XHTMLLanguage;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.ide.BrowserUtil;

/**
 * @author spleaner
 */
public class HtmlWebBrowserUrlProvider extends WebBrowserUrlProvider {

  @NotNull
  public String getUrl(@NotNull final VirtualFile file, @NotNull final Project project) throws Exception {
    return BrowserUtil.getURL(file.getUrl()).toString();
  }

  public boolean isAvailableFor(@NotNull final PsiFile file) {
    final Language language = file.getLanguage();
    return language instanceof HTMLLanguage || language instanceof XHTMLLanguage;
  }
}
