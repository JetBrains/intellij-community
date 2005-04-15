package com.intellij.lang.properties;

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.util.CommonProcessors;
import gnu.trove.THashSet;

import java.util.*;

/**
 * @author cdr
 */
public class PropertiesUtil {
  public static List<Property> findPropertiesByKey(Project project, String key) {
    final PsiSearchHelper searchHelper = PsiManager.getInstance(project).getSearchHelper();
    List<Property> properties = new ArrayList<Property>();

    final List<String> words = StringUtil.getWordsIn(key);
    if (words.size() == 0) return properties;
    // put longer strings first
    Collections.sort(words, new Comparator<String>() {
      public int compare(final String o1, final String o2) {
        return o2.length() - o1.length();
      }
    });
    final GlobalSearchScope propFilesScope = new GlobalSearchScope() {
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
    Set<PsiFile> resultFiles = null;
    for (int i = 0; i < words.size(); i++) {
      String word = words.get(i);
      final Set<PsiFile> files = new THashSet<PsiFile>();
      searchHelper.processAllFilesWithWord(word, propFilesScope, new CommonProcessors.CollectProcessor<PsiFile>(files));
      final boolean firstTime = i == 0;
      if (firstTime) {
        resultFiles = files;
      }
      else {
        resultFiles.retainAll(files);
      }
      if (resultFiles.size() == 0) return properties;
    }

    for (Iterator<PsiFile> iterator = resultFiles.iterator(); iterator.hasNext();) {
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
