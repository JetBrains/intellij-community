// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

import static org.jetbrains.idea.maven.model.MavenConstants.MODEL_VERSION_4_1_0;

@Service(Service.Level.PROJECT)
public final class MavenDomElementDescriptorHolder {
  private static final Logger LOG = Logger.getInstance(MavenDomElementDescriptorHolder.class);

  private enum FileKind {
    PROJECT_FILE_4_0 {
      @Override
      public String getSchemaUrl() {
        return MavenSchemaProvider.MAVEN_PROJECT_SCHEMA_4_0_URL;
      }
    },
    PROJECT_FILE_4_1 {
      @Override
      public String getSchemaUrl() {
        return MavenSchemaProvider.MAVEN_PROJECT_SCHEMA_4_1_URL;
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

  public @Nullable XmlElementDescriptor getDescriptor(@NotNull XmlTag tag) {
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

  private @Nullable XmlNSDescriptorImpl tryGetOrCreateDescriptor(final FileKind kind) {
    CachedValue<XmlNSDescriptorImpl> result = myDescriptorsMap.get(kind);
    if (result == null) {
      result = CachedValuesManager.getManager(myProject).createCachedValue(
        () -> CachedValueProvider.Result.create(doCreateDescriptor(kind), PsiModificationTracker.MODIFICATION_COUNT), false);
      myDescriptorsMap.put(kind, result);
    }
    return result.getValue();
  }

  private @Nullable XmlNSDescriptorImpl doCreateDescriptor(FileKind kind) {
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

  private static @Nullable FileKind getFileKind(PsiFile file) {
    if (MavenDomUtil.isProjectFile(file)) return getProjectFileKind(file);
    if (MavenDomUtil.isProfilesFile(file)) return FileKind.PROFILES_FILE;
    if (MavenDomUtil.isSettingsFile(file)) return getSettingsFileKind(file);
    return null;
  }

  private static @NotNull FileKind getProjectFileKind(PsiFile file) {
    String modelVersion = MavenDomUtil.getXmlProjectModelVersion(file);
    if (MODEL_VERSION_4_1_0.equals(modelVersion)) return FileKind.PROJECT_FILE_4_1;
    return FileKind.PROJECT_FILE_4_0;
  }

  private static @NotNull FileKind getSettingsFileKind(PsiFile file) {
    String nameSpace = MavenDomUtil.getXmlSettingsNameSpace(file);
    if (nameSpace == null) return FileKind.SETTINGS_FILE;
    if (nameSpace.contains("1.1.0")) return FileKind.SETTINGS_FILE_1_1;
    if (nameSpace.contains("1.2.0")) return FileKind.SETTINGS_FILE_1_2;
    return FileKind.SETTINGS_FILE;
  }
}
