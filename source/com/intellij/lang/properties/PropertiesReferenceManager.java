package com.intellij.lang.properties;

import com.intellij.AppTopics;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager;
import com.intellij.openapi.fileTypes.FileTypeEvent;
import com.intellij.openapi.fileTypes.FileTypeListener;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.util.Processor;
import com.intellij.util.messages.MessageBusConnection;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * @author max
 */
public class PropertiesReferenceManager implements ProjectComponent {
  private final Project myProject;
  private final PropertiesFilesManager myPropertiesFilesManager;
  private final PsiManager myPsiManager;
  private final MessageBusConnection myConnection;

  public static PropertiesReferenceManager getInstance(Project project) {
    return project.getComponent(PropertiesReferenceManager.class);
  }

  public PropertiesReferenceManager(Project project, PropertiesFilesManager propertiesFilesManager, PsiManager psiManager) {
    myProject = project;
    myPropertiesFilesManager = propertiesFilesManager;
    myPsiManager = psiManager;

    myConnection = project.getMessageBus().connect();
    myConnection.subscribe(AppTopics.FILE_TYPES, new FileTypeListener() {
      public void beforeFileTypesChanged(FileTypeEvent event) {
      }

      public void fileTypesChanged(FileTypeEvent event) {
        StartupManager.getInstance(myProject).runWhenProjectIsInitialized(new Runnable(){
          public void run() {
             refreshAllKnownPropertiesFiles();
          }
        });
      }
    });
  }

  public void projectOpened() {
    final ReferenceProvidersRegistry registry = ReferenceProvidersRegistry.getInstance(myProject);
    registry.registerReferenceProvider(PsiLiteralExpression.class, new PropertiesReferenceProvider(true));
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
        refreshAllKnownPropertiesFiles();
  }
    });
  }

  private void refreshAllKnownPropertiesFiles() {
    final Processor<VirtualFile> processor = new Processor<VirtualFile>() {
      public boolean process(VirtualFile fileOrDir) {
        myPropertiesFilesManager.addNewFile(fileOrDir);
        return true;
      }
    };
    ProjectRootManager.getInstance(myProject).getFileIndex().iterateContent(new ContentIterator() {
      public boolean processFile(VirtualFile fileOrDir) {
        return processor.process(fileOrDir);
      }
    });
    for (VirtualFile virtualFile : EditorHistoryManager.getInstance(myProject).getFiles()) {
      processor.process(virtualFile);
    }
  }

  public void projectClosed() {
  }

  public void initComponent() {
  }

  public void disposeComponent() {
    myConnection.disconnect();
  }

  @NotNull
  public String getComponentName() {
    return "Properties reference manager";
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
    return result.toArray(new String[result.size()]);
  }

  interface PropertiesFileProcessor {
    void process(String baseName, PropertiesFile propertiesFile);
  }

  private void processPropertiesFiles(@NotNull final Module module, PropertiesFileProcessor processor) {
    final Set<Module> dependentModules = new THashSet<Module>();
    ModuleUtil.getDependencies(module, dependentModules);

    for(VirtualFile file: PropertiesFilesManager.getInstance().getAllPropertiesFiles()) {
      if (!dependentModules.contains(ModuleUtil.findModuleForFile(file, myProject))) {
        continue;
      }

      PsiFile psiFile = myPsiManager.findFile(file);
      if (!(psiFile instanceof PropertiesFile)) continue;

      PsiDirectory directory = psiFile.getParent();
      PsiPackage pkg = JavaDirectoryService.getInstance().getPackage(directory);
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
