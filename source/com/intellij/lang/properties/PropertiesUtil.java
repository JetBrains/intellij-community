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
import com.intellij.util.CommonProcessors;
import gnu.trove.THashSet;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * @author cdr
 */
public class PropertiesUtil {
  private static final GlobalSearchScope PROP_FILES_SCOPE = new GlobalSearchScope() {
    public boolean contains(VirtualFile file) {
      return FileTypeManager.getInstance().getFileTypeByFile(file) == PropertiesSupportLoader.FILE_TYPE;
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

  public static List<Property> findPropertiesByKey(Project project, String key) {
    final PsiSearchHelper searchHelper = PsiManager.getInstance(project).getSearchHelper();
    List<Property> properties = new ArrayList<Property>();

    final Set<PsiFile> files = new THashSet<PsiFile>();
    searchHelper.processAllFilesWithWord(key, PROP_FILES_SCOPE, new CommonProcessors.CollectProcessor<PsiFile>(files));

    for (Iterator<PsiFile> iterator = files.iterator(); iterator.hasNext();) {
      PsiFile file = iterator.next();
      if (file instanceof PropertiesFile) {
        addPropertiesInFile((PropertiesFile)file, key, properties);
      }
    }
    return properties;
  }

  private static void addPropertiesInFile(final PropertiesFile file, final String key, final List<Property> properties) {
    Property[] allProperties = file.getProperties();
    for (int i = 0; i < allProperties.length; i++) {
      Property property = allProperties[i];
      if (key.equals(property.getKey())) {
        properties.add(property);
      }
    }
  }
}
