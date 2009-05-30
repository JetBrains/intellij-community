package com.intellij.lang.properties;

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.PropertyFileIndex;
import com.intellij.util.ArrayUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author max
 */
public class PropertiesReferenceManager {
  private final Project myProject;
  private final PsiManager myPsiManager;

  public static PropertiesReferenceManager getInstance(Project project) {
    return ServiceManager.getService(project, PropertiesReferenceManager.class);
  }

  public PropertiesReferenceManager(Project project, PsiManager psiManager) {
    myProject = project;
    myPsiManager = psiManager;
  }

  @NotNull
  public List<PropertiesFile> findPropertiesFiles(@NotNull final Module module, final String bundleName) {
    final ArrayList<PropertiesFile> result = new ArrayList<PropertiesFile>();
    processPropertiesFiles(module, new PropertiesFileProcessor() {
      public void process(String baseName, PropertiesFile propertiesFile) {
        if (baseName.equals(bundleName)) {
          result.add(propertiesFile);
        }
      }
    });
    return result;
  }

  @Nullable
  public PropertiesFile findPropertiesFile(final Module module, final String bundleName, final Locale locale) {
    List<PropertiesFile> propFiles = findPropertiesFiles(module, bundleName);
    if (locale != null) {
      for(PropertiesFile propFile: propFiles) {
        if (propFile.getLocale().equals(locale)) {
          return propFile;
        }
      }
    }

    // fallback to default locale
    for(PropertiesFile propFile: propFiles) {
      if (propFile.getLocale().getLanguage().length() == 0 || propFile.getLocale().equals(Locale.getDefault())) {
        return propFile;
      }
    }

    // fallback to any file
    if (!propFiles.isEmpty()) {
      return propFiles.get(0);
    }

    return null;
  }

  public String[] getPropertyFileBaseNames(final Module module) {
    final ArrayList<String> result = new ArrayList<String>();
    processPropertiesFiles(module, new PropertiesFileProcessor() {
      public void process(String baseName, PropertiesFile propertiesFile) {
        result.add(baseName);
      }
    });
    return ArrayUtil.toStringArray(result);
  }

  interface PropertiesFileProcessor {
    void process(String baseName, PropertiesFile propertiesFile);
  }

  private void processPropertiesFiles(@NotNull final Module module, PropertiesFileProcessor processor) {
    final Set<Module> dependentModules = new THashSet<Module>();
    ModuleUtil.getDependencies(module, dependentModules);

    if (PropertyFileIndex.DEBUG) {
      System.out.println("PropertiesReferenceManager.processPropertiesFiles");
      System.out.println("PropertiesFileType.FILE_TYPE = " + PropertiesFileType.FILE_TYPE);
    }
    for(VirtualFile file: PropertiesFilesManager.getInstance(myProject).getAllPropertiesFiles()) {
      if (PropertyFileIndex.DEBUG) {
        System.out.println("file = " + file.getPath());
      }

      if (!dependentModules.contains(ModuleUtil.findModuleForFile(file, myProject))) {
        continue;
      }

      PsiFile psiFile = myPsiManager.findFile(file);
      if (!(psiFile instanceof PropertiesFile)) continue;

      PsiDirectory directory = psiFile.getParent();
      String packageQualifiedName = PropertiesUtil.getPackageQualifiedName(directory);

      if (packageQualifiedName != null) {
        StringBuilder qName = new StringBuilder(packageQualifiedName);
        if (qName.length() > 0) {
          qName.append(".");
        }
        qName.append(PropertiesUtil.getBaseName(file));
        processor.process(qName.toString(), (PropertiesFile) psiFile);
      }
    }
  }
}
