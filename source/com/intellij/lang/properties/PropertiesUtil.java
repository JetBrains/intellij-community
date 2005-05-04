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

import java.util.ArrayList;
import java.util.List;

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

}
