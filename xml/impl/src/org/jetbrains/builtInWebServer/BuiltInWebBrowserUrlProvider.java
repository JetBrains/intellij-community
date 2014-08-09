package org.jetbrains.builtInWebServer;

import com.intellij.ide.browsers.OpenInBrowserRequest;
import com.intellij.ide.browsers.WebBrowserUrlProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.Url;
import com.intellij.util.Urls;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.ide.BuiltInServerManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class BuiltInWebBrowserUrlProvider extends WebBrowserUrlProvider implements DumbAware {
  @NotNull
  public static List<Url> getUrls(@NotNull VirtualFile file, @NotNull Project project, @Nullable String currentAuthority) {
    if (currentAuthority != null && !compareAuthority(currentAuthority)) {
      return Collections.emptyList();
    }

    String path = WebServerPathToFileManager.getInstance(project).getPath(file);
    if (path == null) {
      return Collections.emptyList();
    }

    int effectiveBuiltInServerPort = BuiltInServerOptions.getInstance().getEffectiveBuiltInServerPort();
    Url url = Urls.newHttpUrl(currentAuthority == null ? "localhost:" + effectiveBuiltInServerPort : currentAuthority, '/' + project.getName() + '/' + path);
    int defaultPort = BuiltInServerManager.getInstance().getPort();
    if (currentAuthority != null || defaultPort == effectiveBuiltInServerPort) {
      return Collections.singletonList(url);
    }
    return Arrays.asList(url, Urls.newHttpUrl("localhost:" + defaultPort, '/' + project.getName() + '/' + path));
  }

  public static boolean compareAuthority(@Nullable String currentAuthority) {
    if (currentAuthority == null) {
      return false;
    }

    int portIndex = currentAuthority.indexOf(':');
    if (portIndex < 0) {
      return false;
    }

    String host = currentAuthority.substring(0, portIndex);
    if (!BuiltInWebServer.isOwnHostName(host)) {
      return false;
    }

    int port = StringUtil.parseInt(currentAuthority.substring(portIndex + 1), -1);
    return port == BuiltInServerOptions.getInstance().getEffectiveBuiltInServerPort() ||
           port == BuiltInServerManager.getInstance().getPort();
  }

  @Override
  public boolean canHandleElement(@NotNull OpenInBrowserRequest request) {
    return request.getFile().getViewProvider().isPhysical() && !(request.getVirtualFile() instanceof LightVirtualFile) && isMyLanguage(request.getFile());
  }

  protected boolean isMyLanguage(PsiFile psiFile) {
    return HtmlUtil.isHtmlFile(psiFile);
  }

  @Nullable
  @Override
  protected Url getUrl(@NotNull OpenInBrowserRequest request, @NotNull VirtualFile virtualFile) throws BrowserException {
    return ContainerUtil.getFirstItem(getUrls(virtualFile, request.getProject(), null));
  }
}
