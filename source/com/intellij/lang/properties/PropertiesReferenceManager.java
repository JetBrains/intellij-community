package com.intellij.lang.properties;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.fileTypes.FileTypeEvent;
import com.intellij.openapi.fileTypes.FileTypeListener;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author max
 */
public class PropertiesReferenceManager implements ProjectComponent {
  private final Project myProject;
  private final PropertiesFilesManager myPropertiesFilesManager;
  private final FileTypeManager myFileTypeManager;
  private final PsiManager myPsiManager;
  private final Map<String, Collection<VirtualFile>> myPropertiesMap = new THashMap<String, Collection<VirtualFile>>();
  private final List<VirtualFile> myChangedFiles = new ArrayList<VirtualFile>();
  private final FileTypeListener myFileTypeChangedListener;
  private final Object LOCK = new Object();

  public static PropertiesReferenceManager getInstance(Project project) {
    return project.getComponent(PropertiesReferenceManager.class);
  }

  public PropertiesReferenceManager(Project project, PropertiesFilesManager propertiesFilesManager, FileTypeManager fileTypeManager, PsiManager psiManager) {
    myProject = project;
    myPropertiesFilesManager = propertiesFilesManager;
    myFileTypeManager = fileTypeManager;
    myPsiManager = psiManager;
    myFileTypeChangedListener = new FileTypeListener() {
      public void beforeFileTypesChanged(FileTypeEvent event) {

      }

      public void fileTypesChanged(FileTypeEvent event) {
        StartupManager.getInstance(myProject).runWhenProjectIsInitialized(new Runnable(){
          public void run() {
             refreshAllPropFilesInProject();
          }
        });
      }
    };
    fileTypeManager.addFileTypeListener(myFileTypeChangedListener);
  }

  public void projectOpened() {
    final ReferenceProvidersRegistry registry = ReferenceProvidersRegistry.getInstance(myProject);
    final PsiReferenceProvider referenceProvider = new PropertiesReferenceProvider(true);
    registry.registerReferenceProvider(PsiLiteralExpression.class, referenceProvider);
    registry.registerReferenceProvider(new ElementFilter() {
      public boolean isAcceptable(Object element, PsiElement context) {
        if (context instanceof PsiLiteralExpression) {
          PsiLiteralExpression literalExpression = (PsiLiteralExpression) context;
          if (literalExpression.getParent() instanceof PsiNameValuePair) {
            PsiNameValuePair nvp = (PsiNameValuePair) literalExpression.getParent();
            if (AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER.equals(nvp.getName())) {
              return true;
            }
          }
        }
        return false;
      }

      public boolean isClassAcceptable(Class hintClass) {
        return true;
      }
    }, PsiLiteralExpression.class, new ResourceBundleReferenceProvider());

    StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
      public void run() {
        refreshAllPropFilesInProject();
      }
    });
  }

  private void refreshAllPropFilesInProject() {
    ProjectRootManager.getInstance(myProject).getFileIndex().iterateContent(new ContentIterator() {
      public boolean processFile(VirtualFile fileOrDir) {
        boolean isPropertiesFile = myPropertiesFilesManager.addNewFile(fileOrDir);
        if (isPropertiesFile) {
          synchronized (LOCK) {
            myChangedFiles.add(fileOrDir);
          }
        }
        return true;
      }
    });
  }

  public void projectClosed() {
  }

  public void initComponent() {
  }

  public void disposeComponent() {
    myFileTypeManager.removeFileTypeListener(myFileTypeChangedListener);
  }

  @NotNull
  public String getComponentName() {
    return "Properties reference manager";
  }
  public void beforePropertiesFileChange(final PropertiesFile propertiesFile, final Collection<String> propertiesBefore) {
    synchronized (LOCK) {
      VirtualFile virtualFile = propertiesFile.getVirtualFile();
      if (propertiesBefore != null) {
        for (String key : propertiesBefore) {
          Collection<VirtualFile> containingFiles = myPropertiesMap.get(key);
          if (containingFiles != null) containingFiles.remove(virtualFile);
        }
      }
      if (virtualFile != null) {
        myChangedFiles.add(virtualFile);
      }
    }
  }

  private void updateChangedFiles() {
    while (true) {
      VirtualFile virtualFile;
      synchronized (LOCK) {
        if (myChangedFiles.isEmpty()) break;
        virtualFile = myChangedFiles.remove(myChangedFiles.size() - 1);
      }
      if (!virtualFile.isValid()) continue;
      PsiFile psiFile = myPsiManager.findFile(virtualFile);
      if (!(psiFile instanceof PropertiesFile)) continue;
      Set<String> keys = ((PropertiesFile)psiFile).getNamesMap().keySet();
      synchronized (LOCK) {
        for (String key : keys) {
          Collection<VirtualFile> containingFiles = myPropertiesMap.get(key);
          if (containingFiles == null) {
            containingFiles = new THashSet<VirtualFile>();
            myPropertiesMap.put(key, containingFiles);
          }
          containingFiles.add(virtualFile);
        }
      }
    }
  }

  @NotNull
  public List<Property> findPropertiesByKey(final String key) {
    updateChangedFiles();
    List<Property> result;
    synchronized (LOCK) {
      Collection<VirtualFile> virtualFiles = myPropertiesMap.get(key);
      if (virtualFiles == null || virtualFiles.isEmpty()) return Collections.emptyList();
      result = new ArrayList<Property>(virtualFiles.size());
      for (Iterator<VirtualFile> iterator = virtualFiles.iterator(); iterator.hasNext();) {
        VirtualFile virtualFile = iterator.next();
        if (!virtualFile.isValid()) {
          iterator.remove();
          continue;
        }
        PsiFile psiFile = myPsiManager.findFile(virtualFile);
        if (!(psiFile instanceof PropertiesFile)) {
          iterator.remove();
          continue;
        }
        List<Property> properties = ((PropertiesFile)psiFile).findPropertiesByKey(key);
        result.addAll(properties);
      }
    }
    return result;
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

    return null;
  }

  public String[] getPropertyFileBaseNames(final Module module) {
    final ArrayList<String> result = new ArrayList<String>();
    processPropertiesFiles(module, new PropertiesFileProcessor() {
      public void process(String baseName, PropertiesFile propertiesFile) {
        result.add(baseName);
      }
    });
    return result.toArray(new String[result.size()]);
  }

  interface PropertiesFileProcessor {
    void process(String baseName, PropertiesFile propertiesFile);
  }

  private void processPropertiesFiles(@NotNull final Module module, PropertiesFileProcessor processor) {
    updateChangedFiles();
    final Set<Module> dependentModules = new THashSet<Module>();
    ModuleUtil.getDependencies(module, dependentModules);

    for(VirtualFile file: PropertiesFilesManager.getInstance().getAllPropertiesFiles()) {
      if (!dependentModules.contains(VfsUtil.getModuleForFile(myProject, file))) {
        continue;
      }

      PsiFile psiFile = myPsiManager.findFile(file);
      if (!(psiFile instanceof PropertiesFile)) continue;

      PsiDirectory directory = (PsiDirectory)psiFile.getParent();
      PsiPackage pkg = directory.getPackage();
      if (pkg != null) {
        StringBuilder qName = new StringBuilder(pkg.getQualifiedName());
        if (qName.length() > 0) {
          qName.append(".");
        }
        qName.append(PropertiesUtil.getBaseName(file));
        processor.process(qName.toString(), (PropertiesFile) psiFile);
      }
    }
  }
}
