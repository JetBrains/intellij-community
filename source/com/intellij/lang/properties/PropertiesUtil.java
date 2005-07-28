package com.intellij.lang.properties;

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author cdr
 */
public class PropertiesUtil {
  @NotNull
  private static final GlobalSearchScope PROP_FILES_SCOPE = new GlobalSearchScope() {
    public boolean contains(VirtualFile file) {
      return FileTypeManager.getInstance().getFileTypeByFile(file) == StdFileTypes.PROPERTIES;
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

  @NotNull
  public static Collection<Property> findPropertiesByKey(Project project, final String key) {
    final PsiSearchHelper searchHelper = PsiManager.getInstance(project).getSearchHelper();
    final Collection<Property> properties = new THashSet<Property>();
    List<String> words = StringUtil.getWordsIn(key);
    for (String word : words) {
      searchHelper.processAllFilesWithWord(word, PROP_FILES_SCOPE, new Processor<PsiFile>() {
        public boolean process(PsiFile file) {
          if (file instanceof PropertiesFile) {
            PropertiesFile propertiesFile = (PropertiesFile)file;
            properties.addAll(propertiesFile.findPropertiesByKey(key));
          }
          return true;
        }
      });
    }

    return properties;
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
}
