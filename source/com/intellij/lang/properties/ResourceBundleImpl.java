/**
 * @author Alexey
 */
package com.intellij.lang.properties;

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.refactoring.rename.RenameHandlerRegistry;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

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
      return Collections.emptyList();
    }

    @NotNull
    public PropertiesFile getDefaultPropertiesFile(final Project project) {
      throw new IllegalStateException();
    }

    @NotNull
    public String getBaseName() {
      return "";
    }

    @NotNull
    public VirtualFile getBaseDirectory() {
      throw new IllegalStateException();
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
  public PropertiesFile getDefaultPropertiesFile(final Project project) {
    List<PropertiesFile> files = getPropertiesFiles(project);
    // put default properties file first
    Collections.sort(files, new Comparator<PropertiesFile>() {
      public int compare(final PropertiesFile o1, final PropertiesFile o2) {
        return Comparing.compare(o1.getName(), o2.getName());
      }
    });
    return files.get(0);
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

  @Nullable
  public static ResourceBundle createByUrl(String url) {
    if (!url.startsWith(RESOURCE_BUNDLE_PREFIX)) return null;

    String defaultPropertiesUrl = url.substring(RESOURCE_BUNDLE_PREFIX.length());
    VirtualFile defaultProperties = VirtualFileManager.getInstance().findFileByUrl(defaultPropertiesUrl);
    if (defaultProperties != null && FileTypeManager.getInstance().getFileTypeByFile(defaultProperties) == StdFileTypes.PROPERTIES) {
      VirtualFile baseDirectory = defaultProperties.getParent();
      assert baseDirectory != null;
      return new ResourceBundleImpl(baseDirectory, PropertiesUtil.getBaseName(defaultProperties));

    }
    return null;
  }

  public String getUrl() {
    return RESOURCE_BUNDLE_PREFIX +getBaseName();
  }

  @NotNull
  public VirtualFile getBaseDirectory() {
    return myBaseDirectory;
  }
}