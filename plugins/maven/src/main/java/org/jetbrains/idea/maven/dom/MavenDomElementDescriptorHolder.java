/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package org.jetbrains.idea.maven.dom;

import com.intellij.javaee.ExternalResourceManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.impl.schema.XmlNSDescriptorImpl;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

public class MavenDomElementDescriptorHolder {
  private enum FileKind {
    PROJECT_FILE {
      public String getSchemaUrl() {
        return MavenSchemaProvider.MAVEN_PROJECT_SCHEMA_URL;
      }
    },
    PROFILES_FILE {
      public String getSchemaUrl() {
        return MavenSchemaProvider.MAVEN_PROFILES_SCHEMA_URL;
      }
    },
    SETTINGS_FILE {
      public String getSchemaUrl() {
        return MavenSchemaProvider.MAVEN_SETTINGS_SCHEMA_URL;
      }
    };

    public abstract String getSchemaUrl();
  }

  private final Project myProject;
  private final Map<FileKind, XmlNSDescriptorImpl> myDescriptorsMap = new THashMap<FileKind, XmlNSDescriptorImpl>();

  public MavenDomElementDescriptorHolder(Project project) {
    myProject = project;
  }

  public static MavenDomElementDescriptorHolder getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, MavenDomElementDescriptorHolder.class);
  }

  @Nullable
  public XmlElementDescriptor getDescriptor(@NotNull XmlTag tag) {
    FileKind kind = getFileKind(tag.getContainingFile());
    if (kind == null) return null;

    XmlNSDescriptorImpl desc;
    synchronized (this) {
      desc = tryGetOrCreateDescriptor(kind);
      if (desc == null) return null;
    }
    return desc.getElementDescriptor(tag.getName(), desc.getDefaultNamespace());
  }

  @Nullable
  private XmlNSDescriptorImpl tryGetOrCreateDescriptor(FileKind kind) {
    XmlNSDescriptorImpl result = myDescriptorsMap.get(kind);
    if (result != null && result.isValid()) return result;

    String schemaUrl = kind.getSchemaUrl();
    String location = ExternalResourceManager.getInstance().getResourceLocation(schemaUrl);
    if (schemaUrl.equals(location)) return null;

    VirtualFile schema;
    try {
      schema = VfsUtil.findFileByURL(new URL(location));
    }
    catch (MalformedURLException ignore) {
      return null;
    }

    if (schema == null) return null;

    PsiFile psiFile = PsiManager.getInstance(myProject).findFile(schema);
    if (!(psiFile instanceof XmlFile)) return null;

    result = new XmlNSDescriptorImpl();

    result.init(psiFile);
    myDescriptorsMap.put(kind, result);

    return result;
  }

  private FileKind getFileKind(PsiFile file) {
    if (MavenDomUtil.isProjectFile(file)) return FileKind.PROJECT_FILE;
    if (MavenDomUtil.isProfilesFile(file)) return FileKind.PROFILES_FILE;
    if (MavenDomUtil.isSettingsFile(file)) return FileKind.SETTINGS_FILE;
    return null;
  }
}
