package com.intellij.jar;

import com.intellij.openapi.deployment.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.roots.ui.configuration.packaging.PackagingEditorPolicy;
import com.intellij.openapi.roots.ui.configuration.packaging.PackagingEditor;
import com.intellij.openapi.roots.libraries.Library;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class JarPackagingEditorPolicy extends PackagingEditorPolicy {
  private static final Logger LOG = Logger.getInstance("#com.intellij.jar.JarPackagingEditorPolicy");
  @NonNls private static final String JAR_EXTENSION = ".jar";
  private static final PackagingMethod[] COPY_OR_JAR_PACKAGING_METHODS = new PackagingMethod[]{
    PackagingMethod.COPY_FILES,
    PackagingMethod.JAR_AND_COPY_FILE,
    PackagingMethod.JAR_AND_COPY_FILE_AND_LINK_VIA_MANIFEST
  };
  private static final PackagingMethod[] COPY_PACKAGING_METHODS = new PackagingMethod[]{
    PackagingMethod.COPY_FILES,
    PackagingMethod.COPY_FILES_AND_LINK_VIA_MANIFEST
  };

  public JarPackagingEditorPolicy(final Module module) {
    super(module);
  }

  protected PackagingMethod[] getAllowedPackagingMethodsForLibrary(LibraryLink libraryLink) {
    return libraryLink.hasDirectoriesOnly() ? COPY_OR_JAR_PACKAGING_METHODS : COPY_PACKAGING_METHODS;
  }

  protected PackagingMethod[] getAllowedPackagingMethodsForModule(@NotNull Module module) {
    if (StdModuleTypes.JAVA.equals(module.getModuleType())) {
      return COPY_OR_JAR_PACKAGING_METHODS;
    }
    return PackagingMethod.EMPTY_ARRAY;
  }

  protected PackagingMethod[] getPackagingMethodForUnresolvedElement(final ContainerElement element) {
    return PackagingMethod.EMPTY_ARRAY;
  }

  public void setDefaultAttributes(ContainerElement element) {
    if (element instanceof LibraryLink) {
      element.setPackagingMethod(PackagingMethod.DO_NOT_PACKAGE);
    }
    if (element instanceof ModuleLink) {
      PackagingMethod[] allowedDeploymentMethods = getAllowedPackagingMethods(element);
      if (allowedDeploymentMethods.length < 1) {
        LOG.error("illegal Packaging methods for " + element);
      }
      element.setPackagingMethod(allowedDeploymentMethods[0]);
    }
    element.setURI(suggestDefaultRelativePath(element));
  }

  public String suggestDefaultRelativePath(ContainerElement element) {
    PackagingMethod packagingMethod = element.getPackagingMethod();
    if (packagingMethod == PackagingMethod.DO_NOT_PACKAGE) return NOT_APPLICABLE;
    boolean targetIsJar = packagingMethod == PackagingMethod.JAR_AND_COPY_FILE
                              || packagingMethod == PackagingMethod.JAR_AND_COPY_FILE_AND_LINK_VIA_MANIFEST;
    String relativePath = "/";
    if (targetIsJar && element instanceof ModuleLink) {
      relativePath = DeploymentUtil.appendToPath(relativePath, element.getPresentableName());
      if (!relativePath.endsWith(JAR_EXTENSION)) {
        relativePath += JAR_EXTENSION;
      }
    }
    return relativePath;
  }

  protected List<Module> getModulesToAdd(final PackagingEditor packagingEditor) {
    return Collections.emptyList();
  }

  protected List<Library> getLibrariesToAdd(final PackagingEditor packagingEditor) {
    return Collections.emptyList();
  }
}
