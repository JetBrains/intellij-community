// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom;

import com.intellij.javaee.ExternalResourceManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.impl.schema.XmlNSDescriptorImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

@Service(Service.Level.PROJECT)
public final class MavenDomElementDescriptorHolder {
  private static final Logger LOG = Logger.getInstance(MavenDomElementDescriptorHolder.class);

  private enum FileKind {
    PROJECT_FILE {
      @Override
      public String getSchemaUrl() {
        return MavenSchemaProvider.MAVEN_PROJECT_SCHEMA_URL;
      }
    },
    PROFILES_FILE {
      @Override
      public String getSchemaUrl() {
        return MavenSchemaProvider.MAVEN_PROFILES_SCHEMA_URL;
      }
    },
    SETTINGS_FILE {
      @Override
      public String getSchemaUrl() {
        return MavenSchemaProvider.MAVEN_SETTINGS_SCHEMA_URL;
      }
    },
    SETTINGS_FILE_1_1 {
      @Override
      public String getSchemaUrl() {
        return MavenSchemaProvider.MAVEN_SETTINGS_SCHEMA_URL_1_1;
      }
    },
    SETTINGS_FILE_1_2 {
      @Override
      public String getSchemaUrl() {
        return MavenSchemaProvider.MAVEN_SETTINGS_SCHEMA_URL_1_2;
      }
    };

    public abstract String getSchemaUrl();
  }

  private final Project myProject;
  private final Map<FileKind, CachedValue<XmlNSDescriptorImpl>> myDescriptorsMap = new HashMap<>();

  public MavenDomElementDescriptorHolder(Project project) {
    myProject = project;
  }

  public static MavenDomElementDescriptorHolder getInstance(@NotNull Project project) {
    return project.getService(MavenDomElementDescriptorHolder.class);
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
    LOG.assertTrue(tag.isValid());
    LOG.assertTrue(desc.isValid());
    return desc.getElementDescriptor(tag.getName(), desc.getDefaultNamespace());
  }

  @Nullable
  private XmlNSDescriptorImpl tryGetOrCreateDescriptor(final FileKind kind) {
    CachedValue<XmlNSDescriptorImpl> result = myDescriptorsMap.get(kind);
    if (result == null) {
      result = CachedValuesManager.getManager(myProject).createCachedValue(
        () -> CachedValueProvider.Result.create(doCreateDescriptor(kind), PsiModificationTracker.MODIFICATION_COUNT), false);
      myDescriptorsMap.put(kind, result);
    }
    return result.getValue();
  }

  @Nullable
  private XmlNSDescriptorImpl doCreateDescriptor(FileKind kind) {
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

    XmlNSDescriptorImpl result = new XmlNSDescriptorImpl();
    result.init(psiFile);
    return result;
  }

  @Nullable
  private static FileKind getFileKind(PsiFile file) {
    if (MavenDomUtil.isProjectFile(file)) return FileKind.PROJECT_FILE;
    if (MavenDomUtil.isProfilesFile(file)) return FileKind.PROFILES_FILE;
    if (MavenDomUtil.isSettingsFile(file)) return getSettingsFileKind(file);
    return null;
  }

  @NotNull
  private static FileKind getSettingsFileKind(PsiFile file) {
    String nameSpace = MavenDomUtil.getXmlSettingsNameSpace(file);
    if (nameSpace == null) return FileKind.SETTINGS_FILE;
    if (nameSpace.contains("1.1.0")) return FileKind.SETTINGS_FILE_1_1;
    if (nameSpace.contains("1.2.0")) return FileKind.SETTINGS_FILE_1_2;
    return FileKind.SETTINGS_FILE;
  }
}
