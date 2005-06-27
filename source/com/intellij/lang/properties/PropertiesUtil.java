package com.intellij.lang.properties;

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * @author cdr
 */
public class PropertiesUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.properties.PropertiesUtil");

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
  public static String getBaseName(VirtualFile virtualFile) {
    String name = virtualFile.getNameWithoutExtension();

    String suffix = getLocale(virtualFile).toString();
    String baseName = StringUtil.trimEnd(name, suffix);
    baseName = StringUtil.trimEnd(baseName, "_");
    return baseName;
  }

  @NotNull
  public static Locale getLocale(VirtualFile propertiesFile) {
    String name = propertiesFile.getNameWithoutExtension();
    String language = "";
    String country = "";
    String variant = "";
    int pos = name.indexOf('_');
    if (pos != -1) {
      int next = name.indexOf('_', pos + 1);
      language = name.substring(pos+1, next==-1 ? name.length() : next);

      if (next != -1) {
        int last = name.indexOf('_', next + 1);
        country = name.substring(next+1, last == -1 ? name.length() : last);
        variant = last == -1 ? "" : name.substring(last+1);
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
