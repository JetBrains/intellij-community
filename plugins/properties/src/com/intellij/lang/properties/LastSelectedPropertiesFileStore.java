/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.lang.properties;

import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.components.*;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.statistics.StatisticsInfo;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.util.containers.hash.LinkedHashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * @author cdr
 */
@State(
  name = "LastSelectedPropertiesFileStore",
  storages = @Storage(value = "lastSelectedPropertiesFile.xml", roamingType = RoamingType.DISABLED)
)
public class LastSelectedPropertiesFileStore implements PersistentStateComponent<Element> {
  private static final String PROPERTIES_FILE_STATISTICS_KEY = "PROPERTIES_FILE";

  private final Map<String, String> lastSelectedUrls = new LinkedHashMap<>();
  private String lastSelectedFileUrl;

  public static LastSelectedPropertiesFileStore getInstance() {
    return ServiceManager.getService(LastSelectedPropertiesFileStore.class);
  }

  @Nullable
  public String suggestLastSelectedPropertiesFileUrl(PsiFile context) {
    VirtualFile virtualFile = context.getVirtualFile();

    while (virtualFile != null) {
      String contextUrl = virtualFile.getUrl();
      String url = lastSelectedUrls.get(contextUrl);
      if (url != null) {
        return url;
      }
      virtualFile = virtualFile.getParent();
    }
    if (lastSelectedFileUrl != null) {
      VirtualFile lastFile = VirtualFileManager.getInstance().findFileByUrl(lastSelectedFileUrl);
      final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(context.getProject()).getFileIndex();
      if (lastFile != null && ModuleUtilCore.findModuleForPsiElement(context) == fileIndex.getModuleForFile(lastFile)) {
        return lastSelectedFileUrl;
      }
    }
    return null;
  }

  public static int getUseCount(@NotNull String path) {
    return StatisticsManager.getInstance().getUseCount(new StatisticsInfo(PROPERTIES_FILE_STATISTICS_KEY, path));
  }

  public void saveLastSelectedPropertiesFile(PsiFile context, PropertiesFile file) {
    VirtualFile virtualFile = context.getVirtualFile();
    if (virtualFile instanceof VirtualFileWindow) {
      virtualFile = ((VirtualFileWindow)virtualFile).getDelegate();
    }
    assert virtualFile != null;
    String contextUrl = virtualFile.getUrl();
    final VirtualFile vFile = file.getVirtualFile();
    if (vFile != null) {
      String url = vFile.getUrl();
      lastSelectedUrls.put(contextUrl, url);
      VirtualFile containingDir = virtualFile.getParent();
      lastSelectedUrls.put(containingDir.getUrl(), url);
      lastSelectedFileUrl = url;
      StatisticsManager.getInstance().incUseCount(new StatisticsInfo(PROPERTIES_FILE_STATISTICS_KEY, FileUtil.toSystemDependentName(VfsUtilCore.urlToPath(url))));
    }
  }

  @Override
  public Element getState() {
    Element state = new Element("state");
    for (Map.Entry<String, String> entry : lastSelectedUrls.entrySet()) {
      Element child = new Element("entry");
      child.setAttribute("context", entry.getKey());
      child.setAttribute("url", entry.getValue());
      state.addContent(child);
    }
    if (lastSelectedFileUrl != null) {
      state.setAttribute("lastSelectedFileUrl", lastSelectedFileUrl);
    }
    return state;
  }

  @Override
  public void loadState(Element state) {
    lastSelectedUrls.clear();
    for (Element child : state.getChildren("entry")) {
      String context = child.getAttributeValue("context");
      String url = child.getAttributeValue("url");
      VirtualFile propFile = VirtualFileManager.getInstance().findFileByUrl(url);
      VirtualFile contextFile = VirtualFileManager.getInstance().findFileByUrl(context);
      if (propFile != null && contextFile != null) {
        lastSelectedUrls.put(context, url);
      }
    }
    lastSelectedFileUrl = state.getAttributeValue("lastSelectedFileUrl");
  }
}
