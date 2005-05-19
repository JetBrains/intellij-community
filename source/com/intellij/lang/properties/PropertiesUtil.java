package com.intellij.lang.properties;

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author cdr
 */
public class PropertiesUtil {
  private static final GlobalSearchScope PROP_FILES_SCOPE = new GlobalSearchScope() {
    public boolean contains(VirtualFile file) {
      return FileTypeManager.getInstance().getFileTypeByFile(file) == PropertiesFileType.FILE_TYPE;
    }

    public int compare(VirtualFile file1, VirtualFile file2) {
      return 0;
    }

    public boolean isSearchInModuleContent(Module aModule) {
      return true;
    }

    public boolean isSearchInLibraries() {
      return false;
    }
  };

  public static List<Property> findPropertiesByKey(Project project, final String key) {
    final PsiSearchHelper searchHelper = PsiManager.getInstance(project).getSearchHelper();
    final List<Property> properties = new ArrayList<Property>();

    searchHelper.processAllFilesWithWord(key, PROP_FILES_SCOPE, new Processor<PsiFile>() {
      public boolean process(PsiFile file) {
        if (file instanceof PropertiesFile) {
          properties.addAll(((PropertiesFile) file).findPropertiesByKey(key));
        }
        return true;
      }
    });

    return properties;
  }

  public static boolean isPropertyComplete(final Project project, ResourceBundle resourceBundle, String propertyName) {
    List<PropertiesFile> propertiesFiles = virtualFilesToProperties(project, resourceBundle.getPropertiesFiles());
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

  public static String getBaseName(VirtualFile virtualFile) {
    String name = virtualFile.getNameWithoutExtension();
    if (name.length() > 3 && name.charAt(name.length()-3) == '_') {
      name = name.substring(0, name.length() - 3);
    }
    if (name.length() > 3 && name.charAt(name.length()-3) == '_') {
      name = name.substring(0, name.length() - 3);
    }
    return name;
  }

  public static List<Property> findAllProperties(Project project, ResourceBundle resourceBundle, String key) {
    List<Property> result = new SmartList<Property>();
    List<PropertiesFile> propertiesFiles = virtualFilesToProperties(project, resourceBundle.getPropertiesFiles());
    for (PropertiesFile propertiesFile : propertiesFiles) {
      result.addAll(propertiesFile.findPropertiesByKey(key));
    }
    return result;
  }
}
