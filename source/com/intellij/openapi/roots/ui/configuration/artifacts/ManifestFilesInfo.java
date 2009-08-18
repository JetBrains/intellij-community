package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElementResolvingContext;
import com.intellij.packaging.impl.elements.ManifestFileUtil;
import com.intellij.packaging.ui.ManifestFileConfiguration;

import java.io.File;
import java.util.Map;
import java.util.HashMap;

public class ManifestFilesInfo {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.ui.configuration.artifacts.ManifestFilesInfo");
  private Map<CompositePackagingElement<?>, ManifestFileConfiguration> myManifestFiles = new HashMap<CompositePackagingElement<?>, ManifestFileConfiguration>();
  private Map<CompositePackagingElement<?>, ManifestFileConfiguration> myOriginalManifestFiles = new HashMap<CompositePackagingElement<?>, ManifestFileConfiguration>();

  public ManifestFileConfiguration getManifestFile(CompositePackagingElement<?> element, ArtifactType artifactType,
                                                   final PackagingElementResolvingContext context) {
    ManifestFileConfiguration manifestFile = myManifestFiles.get(element);
    if (manifestFile == null) {
      manifestFile = ManifestFileUtil.createManifestFileConfiguration(element, context, artifactType);
      myOriginalManifestFiles.put(element, new ManifestFileConfiguration(manifestFile));
      myManifestFiles.put(element, manifestFile);
    }
    return manifestFile;
  }

  public void saveManifestFiles() {
    for (Map.Entry<CompositePackagingElement<?>, ManifestFileConfiguration> entry : myManifestFiles.entrySet()) {
      final ManifestFileConfiguration configuration = entry.getValue();
      final String path = configuration.getManifestFilePath();
      if (path == null) continue;

      final ManifestFileConfiguration original = myOriginalManifestFiles.get(entry.getKey());
      if (original != null && original.equals(configuration)) {
        continue;
      }

      VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
      if (file == null) {
        final File ioFile = new File(FileUtil.toSystemDependentName(path));
        FileUtil.createIfDoesntExist(ioFile);
        file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioFile);
        if (file == null) {
          //todo[nik] improve
          LOG.error("cannot create file: " + ioFile);
        }
      }

      ManifestFileUtil.updateManifest(file, configuration);
    }
  }

  public boolean isManifestFilesModified() {
    return !myOriginalManifestFiles.equals(myManifestFiles);
  }

  public void clear() {
    myManifestFiles.clear();
    myOriginalManifestFiles.clear();
  }
}