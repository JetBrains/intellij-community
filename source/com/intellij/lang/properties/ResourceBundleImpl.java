/**
 * @author Alexey
 */
package com.intellij.lang.properties;

import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.project.Project;
import com.intellij.refactoring.rename.RenameHandlerRegistry;
import com.intellij.util.SmartList;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

import java.util.List;
import java.util.Collections;

public class ResourceBundleImpl implements ResourceBundle {
  private final @NotNull VirtualFile myBaseDirectory;
  private final @NotNull String myBaseName;
  @NonNls private static final String RESOURCE_BUNDLE_PREFIX = "resourceBundle:";

  public ResourceBundleImpl(@NotNull VirtualFile baseDirectory, @NotNull String baseName) {
    myBaseDirectory = baseDirectory;
    myBaseName = baseName;
  }

  public static final ResourceBundle NULL = new ResourceBundle() {
    @NotNull
    public List<PropertiesFile> getPropertiesFiles(final Project project) {
      return Collections.EMPTY_LIST;
    }

    @NotNull
    public String getBaseName() {
      return "";
    }
  };

  static {
    RenameHandlerRegistry.getInstance().registerHandler(ResourceBundleRenameHandler.INSTANCE);
  }

  @NotNull
  public List<PropertiesFile> getPropertiesFiles(final Project project) {
    VirtualFile[] children = myBaseDirectory.getChildren();
    List<PropertiesFile> result = new SmartList<PropertiesFile>();
    FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    PsiManager psiManager = PsiManager.getInstance(project);
    for (VirtualFile file : children) {
      if (!file.isValid() || fileTypeManager.getFileTypeByFile(file) != StdFileTypes.PROPERTIES) continue;
      if (Comparing.strEqual(PropertiesUtil.getBaseName(file), myBaseName)) {
        PsiFile psiFile = psiManager.findFile(file);
        if (psiFile instanceof PropertiesFile) {
          result.add((PropertiesFile)psiFile);
        }
      }
    }
    return result;
  }

  @NotNull
  public String getBaseName() {
    return myBaseName;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final ResourceBundleImpl resourceBundle = (ResourceBundleImpl)o;

    if (!myBaseDirectory.equals(resourceBundle.myBaseDirectory)) return false;
    if (!myBaseName.equals(resourceBundle.myBaseName)) return false;

    return true;
  }

  public int hashCode() {
    int result = myBaseDirectory.hashCode();
    result = 29 * result + myBaseName.hashCode();
    return result;
  }

  public static ResourceBundle createByUrl(String url) {
    if (!url.startsWith(RESOURCE_BUNDLE_PREFIX)) return null;

    String defaultPropertiesUrl = url.substring(RESOURCE_BUNDLE_PREFIX.length());
    VirtualFile defaultProperties = VirtualFileManager.getInstance().findFileByUrl(defaultPropertiesUrl);
    if (defaultProperties != null && FileTypeManager.getInstance().getFileTypeByFile(defaultProperties) == StdFileTypes.PROPERTIES) {
      ResourceBundleImpl resourceBundle = new ResourceBundleImpl(defaultProperties.getParent(),
                                                                 PropertiesUtil.getBaseName(defaultProperties));
      return resourceBundle;

    }
    return null;
  }

  public String getUrl() {
    return RESOURCE_BUNDLE_PREFIX +getBaseName();
  }
}