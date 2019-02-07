// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.util.documentation;

import com.intellij.lang.documentation.DocumentationMarkup;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.platform.templates.github.DownloadUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Supplier;

public class MdnDocumentationUtil {
  private static final Logger LOG = Logger.getInstance(MdnDocumentationUtil.class);
  private static final String[] HTTP_PREFIXES = {"http://", "https://"};
  private static final List<String> VISIBLE_BROWSERS = Arrays.asList("chrome", "chrome_android", "edge", "firefox", "ie", "opera", "safari",
                                                                     "safari_ios");

  @NotNull
  private static String getFormattedCompatibilityData(@Nullable Map data) {
    Object compat = data != null ? data.get("__compat") : null;
    if (compat != null) {
      Map support = (Map)((Map)compat).get("support");
      if (support != null) {
        boolean everywhere = true;
        Map<String, String> versions = new LinkedHashMap<>();
        for (Object key : support.keySet()) {
          String browserId = key.toString();
          if (!VISIBLE_BROWSERS.contains(browserId)) continue;
          Object browserInfos = support.get(key);
          Map browserInfo = (Map)(browserInfos instanceof ArrayList ? ((ArrayList)browserInfos).get(0) : browserInfos);
          Object versionAdded = browserInfo.get("version_added");
          String version = versionAdded != null ? versionAdded.toString() : "";
          everywhere &= anyVersion(browserId, version);
          if (!StringUtil.isEmpty(version) && !"false".equals(version)) {
            versions.put(browserId, version);
          }
        }
        if (everywhere) return "";
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, String> entry : versions.entrySet()) {
          if (result.length() > 0) {
            result.append(", ");
          }
          result.append(getReadableBrowserName(entry.getKey()));
          String version = entry.getValue();
          if (!anyVersion(entry.getKey(), version)) {
            result.append(" ").append(version);
          }
        }
        return result.toString();
      }
    }
    return "";
  }

  private static boolean anyVersion(String browser, String version) {
    if (browser.startsWith("edge") && "12".equals(version)) return true;
    if (browser.equals("firefox_android") && "4".equals(version)) return true;
    if (browser.equals("chrome_android") && "18".equals(version)) return true;
    return "true".equals(version) || "1".equals(version);
  }

  private static String getReadableBrowserName(String key) {
    if ("webview_android".equals(key)) {
      return "Android WebView";
    }
    if ("safari_ios".equals(key)) {
      return "Safari iOS";
    }
    if ("ie".equals(key)) {
      return "IE";
    }
    if ("samsunginternet_android".equals(key)) {
      return "Samsung Internet";
    }
    return StringUtil.capitalizeWords(key.replace('_', ' '), true);
  }

  @Nullable
  public static String getMdnUrl(@Nullable Map data) {
    Object compat = data != null ? data.get("__compat") : null;
    if (compat != null) {
      Object mdnUrl = ((Map)compat).get("mdn_url");
      return mdnUrl != null ? mdnUrl.toString() : null;
    }
    return null;
  }

  public static boolean isDeprecated(@Nullable Map data) {
    Object compat = data != null ? data.get("__compat") : null;
    if (compat != null) {
      Object status = ((Map)compat).get("status");
      if (status != null) {
        return "true".equals(((Map)status).get("deprecated").toString());
      }
    }
    return false;
  }

  @Nullable
  public static String fetchExternalDocumentation(List<String> docUrls, Supplier<String> defaultDocProducer) {
    String url = null;
    for (String urlCandidate : docUrls) {
      if (urlCandidate.contains("developer.mozilla.org")) {
        url = urlCandidate;
      }
    }
    if (url == null) return null;
    File targetDir = new File(PathManager.getConfigPath(), "mdn");
    File targetFile = new File(targetDir, makeUniqueFileName(url));
    try {
      String text = defaultDocProducer.get();
      if (text == null || !text.contains(DocumentationMarkup.CONTENT_START)) {
        if (!targetFile.exists()) {
          DownloadUtil.downloadAtomically(ProgressManager.getInstance().getProgressIndicator(), url + "?raw&summary", targetFile);
        }
        String content = FileUtil.loadFile(targetFile, CharsetToolkit.UTF8_CHARSET);
        String mdnDecorated = decorate(fixLinks(content), url);
        if (text == null) {
          return mdnDecorated;
        }
        else {
          int definitionEnd = text.indexOf(DocumentationMarkup.DEFINITION_END);
          assert definitionEnd > 0;
          definitionEnd += DocumentationMarkup.DEFINITION_END.length();
          return text.substring(0, definitionEnd) +
                 DocumentationMarkup.CONTENT_START + mdnDecorated + DocumentationMarkup.CONTENT_END +
                 (definitionEnd < text.length() ? text.substring(definitionEnd) : "");
        }
      }
    }
    catch (IOException e) {
      LOG.warn(e);
    }
    return null;
  }

  private static String decorate(String mdnDoc, String url) {
    return mdnDoc +
           "<div style='padding-top: 5px'>By <a href='" +
           url +
           "$history'>Mozilla Contributors</a>, <a href='http://creativecommons.org/licenses/by-sa/2.5/'>CC BY-SA 2.5</a></div>";
  }

  private static String fixLinks(String s) {
    return s.replaceAll("href=\"/", "href=\"https://developer.mozilla.org/");
  }

  private static String makeUniqueFileName(String filePath) {
    String path = filePath;
    for (String prefix : HTTP_PREFIXES) {
      if (filePath.contains(prefix)) {
        path = filePath.replace(prefix, "http_");
        break;
      }
    }
    return path.replace('/', '_').replace('\\', '_').replace(':', '_');
  }

  @NotNull
  public static String buildDoc(@NotNull String name, @NotNull String description, @Nullable Map mdnCompatData) {
    return buildDoc(name, description, mdnCompatData, null);
  }

  @NotNull
  public static String buildDoc(@NotNull String name,
                                @NotNull String description,
                                @Nullable Map mdnCompatData,
                                @Nullable List<Couple<String>> additionalData) {
    StringBuilder buf = new StringBuilder();

    buf.append(DocumentationMarkup.DEFINITION_START).append(name).append(DocumentationMarkup.DEFINITION_END);
    buf.append(DocumentationMarkup.CONTENT_START);
    buf.append(StringUtil.capitalize(description));
    buf.append(DocumentationMarkup.CONTENT_END);

    String compatibilityData = getFormattedCompatibilityData(mdnCompatData);

    boolean deprecated = isDeprecated(mdnCompatData);
    if (deprecated || !compatibilityData.isEmpty() || !ContainerUtil.isEmpty(additionalData)) {
      buf.append(DocumentationMarkup.SECTIONS_START);
    }
    if (!ContainerUtil.isEmpty(additionalData)) {
      for (Couple<String> entry : additionalData) {
        buf.append(DocumentationMarkup.SECTION_HEADER_START).append(entry.first);
        buf.append(DocumentationMarkup.SECTION_SEPARATOR).append(entry.second);
        buf.append(DocumentationMarkup.SECTION_END);
      }
    }
    if (deprecated) {
      buf.append(DocumentationMarkup.SECTION_HEADER_START).append("Deprecated");
      buf.append(DocumentationMarkup.SECTION_END);
    }
    if (!compatibilityData.isEmpty()) {
      buf.append(DocumentationMarkup.SECTION_HEADER_START).append("Supported by:");
      buf.append(DocumentationMarkup.SECTION_SEPARATOR).append(compatibilityData);
      buf.append(DocumentationMarkup.SECTION_END);
    }
    if (deprecated || !compatibilityData.isEmpty() || !ContainerUtil.isEmpty(additionalData)) {
      buf.append(DocumentationMarkup.SECTIONS_END);
    }

    return buf.toString();
  }
}
