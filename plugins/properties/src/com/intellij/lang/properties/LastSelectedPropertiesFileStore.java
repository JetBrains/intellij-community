// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties;

import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.components.*;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.hash.LinkedHashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

@State(
  name = "LastSelectedPropertiesFileStore",
  storages = @Storage(value = "lastSelectedPropertiesFile.xml", roamingType = RoamingType.DISABLED)
)
public class LastSelectedPropertiesFileStore implements PersistentStateComponent<Element> {
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
  public void loadState(@NotNull Element state) {
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
