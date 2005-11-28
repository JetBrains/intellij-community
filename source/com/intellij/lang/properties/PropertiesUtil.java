package com.intellij.lang.properties;

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.impl.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author cdr
 */
public class PropertiesUtil {
  @NotNull
  public static Collection<Property> findPropertiesByKey(Project project, final String key) {
    return PropertiesReferenceManager.getInstance(project).findPropertiesByKey(key);
  }

  public static boolean isPropertyComplete(final Project project, ResourceBundle resourceBundle, String propertyName) {
    List<PropertiesFile> propertiesFiles = resourceBundle.getPropertiesFiles(project);
    for (PropertiesFile propertiesFile : propertiesFiles) {
      if (propertiesFile.findPropertyByKey(propertyName) == null) return false;
    }
    return true;
  }

  public static @NotNull List<PropertiesFile> virtualFilesToProperties(Project project, List<VirtualFile> files) {
    List<PropertiesFile> propertiesFiles = new ArrayList<PropertiesFile>(files.size());
    PsiManager psiManager = PsiManager.getInstance(project);
    for (VirtualFile file : files) {
      PsiFile psiFile = psiManager.findFile(file);
      if (psiFile instanceof PropertiesFile) {
        propertiesFiles.add((PropertiesFile)psiFile);
      }
    }
    return propertiesFiles;
  }

  @NotNull
  public static String getBaseName(@NotNull VirtualFile virtualFile) {
    String name = virtualFile.getNameWithoutExtension();

    String[] parts = name.split("_");
    String baseName = "";
    for (String part : parts) {
      if (part.length() == 2) {
        break;
      }
      if (baseName.length() != 0) baseName += "_";
      baseName += part;
    }

    return baseName;
  }

  @NotNull
  public static Locale getLocale(VirtualFile propertiesFile) {
    String name = propertiesFile.getNameWithoutExtension();
    String tail = StringUtil.trimStart(name, getBaseName(propertiesFile));
    tail = StringUtil.trimStart(tail, "_");
    String[] parts = tail.split("_");
    String language = parts.length == 0 ? "" : parts[0];
    String country = "";
    String variant = "";
    if (parts.length >= 2 && parts[1].length() == 2) {
      country = parts[1];
      for (int i = 2; i < parts.length; i++) {
        String part = parts[i];
        if (variant.length() != 0) variant += "_";
        variant += part;
      }
    }

    return new Locale(language,country,variant);
  }

  @NotNull
  public static List<Property> findAllProperties(Project project, ResourceBundle resourceBundle, String key) {
    List<Property> result = new SmartList<Property>();
    List<PropertiesFile> propertiesFiles = resourceBundle.getPropertiesFiles(project);
    for (PropertiesFile propertiesFile : propertiesFiles) {
      result.addAll(propertiesFile.findPropertiesByKey(key));
    }
    return result;
  }

  public static boolean isUnescapedBackSlashAtTheEnd (String text) {
    boolean result = false;
    for (int i = text.length()-1; i>=0; i--) {
      if (text.charAt(i) == '\\') {
        result = !result;
      }
      else {
        break;
      }
    }
    return result;
  }

  @Nullable
  public static PropertiesFile getPropertiesFile(final String bundleName, final Module searchFromModule) {
    @NonNls final String fileName = bundleName + ".properties";
    final Set<Module> dependentModules = new com.intellij.util.containers.HashSet<Module>();
    ModuleUtil.getDependencies(searchFromModule, dependentModules);
    for(Module m: dependentModules) {
      final PropertiesFile file = findPropertiesFile(fileName, m);
      if (file != null) {
        return file;
      }
    }
    return null;
  }

  private static PropertiesFile findPropertiesFile(final String name, final Module inModule) {
    final VirtualFile[] sourceRoots = ModuleRootManager.getInstance(inModule).getSourceRoots();
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(inModule.getProject()).getFileIndex();
    for (final VirtualFile sourceRoot : sourceRoots) {
      final String packagePrefix = fileIndex.getPackageNameByDirectory(sourceRoot);
      final String prefix = packagePrefix == null || packagePrefix.length() == 0 ? null : packagePrefix.replace('.', '/') + "/";
      final String relPath = prefix != null && name.startsWith(prefix) && name.length() > prefix.length() ? name.substring(prefix.length()) : name;
      final String fullPath = sourceRoot.getPath() + "/" + relPath;
      final VirtualFile fileByPath = LocalFileSystem.getInstance().findFileByPath(fullPath);
      if (fileByPath != null) {
        final PsiFile psiFile = PsiManager.getInstance(inModule.getProject()).findFile(fileByPath);
        if (psiFile instanceof PropertiesFile) {
          return (PropertiesFile)psiFile;
        }
      }
    }
    return null;
  }
}
