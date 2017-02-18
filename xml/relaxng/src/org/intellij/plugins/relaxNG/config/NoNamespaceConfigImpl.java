/*
 * Copyright 2007 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.plugins.relaxNG.config;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.editor.HectorComponentPanel;
import com.intellij.openapi.editor.HectorComponentPanelsProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

@State(name = "NoNamespaceConfig.Mappings", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
class NoNamespaceConfigImpl extends NoNamespaceConfig implements PersistentStateComponent<NoNamespaceConfigImpl.Mappings> {

  private final Map<VirtualFilePointer, VirtualFilePointer> myMappings = new HashMap<>();
  private final Project myProject;

  NoNamespaceConfigImpl(Project project) {
    myProject = project;
  }

  private VirtualFilePointer getMappedPointer(@NotNull PsiFile file) {
    final VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) return null;

    final String url = virtualFile.getUrl();
    for (VirtualFilePointer pointer : myMappings.keySet()) {
      if (url.equals(pointer.getUrl())) {
        return myMappings.get(pointer);
      }
    }
    return null;
  }

  @Override
  @Nullable
  public String getMapping(@NotNull PsiFile file) {
    final VirtualFilePointer pointer = getMappedPointer(file);
    return pointer != null ? pointer.getUrl() : null;
  }

  @Override
  public VirtualFile getMappedFile(@NotNull PsiFile file) {
    final VirtualFilePointer url = getMappedPointer(file);
    return url != null ? url.getFile() : null;
  }

  @Override
  public void setMapping(@NotNull PsiFile file, String location) {
    final VirtualFile virtualFile = file.getVirtualFile();
    assert virtualFile != null;

    final String url = virtualFile.getUrl();
    final VirtualFilePointerManager manager = VirtualFilePointerManager.getInstance();
    for (VirtualFilePointer pointer : myMappings.keySet()) {
      if (url.equals(pointer.getUrl())) {
        if (location == null) {
          myMappings.remove(pointer);
          return;
        } else if (!location.equals(myMappings.get(pointer).getUrl())) {
          myMappings.remove(pointer);
          myMappings.put(pointer, manager.create(location, myProject, null));
          return;
        }
      }
    }

    if (location != null) {
      myMappings.put(manager.create(url, myProject, null), manager.create(location, myProject, null));
    }
  }

  @Override
  public void initComponent() {
  }

  @Override
  public void disposeComponent() {
  }

  @Override
  @NotNull
  public String getComponentName() {
    return "RELAX-NG.NoNamespaceConfig";
  }

  @Override
  public void projectOpened() {
  }

  @Override
  public void projectClosed() {
    reset();
  }

  @Override
  public Mappings getState() {
    final HashMap<String, String> map = new HashMap<>();
    for (Map.Entry<VirtualFilePointer, VirtualFilePointer> entry : myMappings.entrySet()) {
      map.put(entry.getKey().getUrl(), entry.getValue().getUrl());
    }
    return new Mappings(map);
  }

  @Override
  public void loadState(Mappings state) {
    reset();

    final VirtualFilePointerManager manager = VirtualFilePointerManager.getInstance();
    final Map<String, String> map = state.myMappings;
    for (String file : map.keySet()) {
      myMappings.put(manager.create(file, myProject, null), manager.create(map.get(file), myProject, null));
    }
  }

  private void reset() {
    myMappings.clear();
  }

  @SuppressWarnings({ "CanBeFinal", "UnusedDeclaration" })
  public static class Mappings {
    @MapAnnotation(surroundWithTag = false, entryTagName = "mapping", keyAttributeName = "file", valueAttributeName = "schema")
    public Map<String, String> myMappings;

    public Mappings() {
      myMappings = new HashMap<>();
    }

    Mappings(Map<String, String> map) {
      myMappings = map;
    }
  }

  public static class HectorProvider implements HectorComponentPanelsProvider {
    @Override
    @Nullable
    public HectorComponentPanel createConfigurable(@NotNull PsiFile file) {
      if (file instanceof XmlFile) {
        try {
          final XmlTag rootTag = ((XmlFile)file).getDocument().getRootTag();
          if (rootTag.getNamespace().length() == 0) {
            return new NoNamespaceConfigPanel(NoNamespaceConfig.getInstance(file.getProject()), file);
          }
        } catch (NullPointerException e) {
          return null;
        }
      }
      return null;
    }
  }
}
