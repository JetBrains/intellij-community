package com.intellij.lang.properties;

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import gnu.trove.THashMap;
import gnu.trove.THashSet;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 27, 2005
 * Time: 6:00:51 PM
 * To change this template use File | Settings | File Templates.
 */
public class PropertiesReferenceManager implements ProjectComponent {
  private final Project myProject;
  private final PropertiesFilesManager myPropertiesFilesManager;
  private final Map<String, Collection<VirtualFile>> myPropertiesMap = new THashMap<String, Collection<VirtualFile>>();
  private final List<VirtualFile> myChangedFiles = new ArrayList<VirtualFile>();

  public static PropertiesReferenceManager getInstance(Project project) {
    return project.getComponent(PropertiesReferenceManager.class);
  }

  public PropertiesReferenceManager(Project project, PropertiesFilesManager propertiesFilesManager) {
    myProject = project;
    myPropertiesFilesManager = propertiesFilesManager;
  }

  public void projectOpened() {
    final PsiReferenceProvider referenceProvider = new PropertiesReferenceProvider();
    ReferenceProvidersRegistry.getInstance(myProject).registerReferenceProvider(PsiLiteralExpression.class, referenceProvider);

    StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
      public void run() {
        ProjectRootManager.getInstance(myProject).getFileIndex().iterateContent(new ContentIterator() {
          public boolean processFile(VirtualFile fileOrDir) {
            boolean isPropertiesFile = myPropertiesFilesManager.addNewFile(fileOrDir);
            if (isPropertiesFile) {
              synchronized (myChangedFiles) {
                myChangedFiles.add(fileOrDir);
              }
            }
            return true;
          }
        });
      }
    });
  }

  public void projectClosed() {
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public String getComponentName() {
    return "Properties support manager";
  }
  public void beforePropertiesFileChange(final PropertiesFile propertiesFile, final Collection<String> propertiesBefore) {
    synchronized (myChangedFiles) {
      VirtualFile virtualFile = propertiesFile.getVirtualFile();
      if (propertiesBefore != null) {
        for (String key : propertiesBefore) {
          Collection<VirtualFile> containingFiles = myPropertiesMap.get(key);
          if (containingFiles != null) containingFiles.remove(virtualFile);
        }
      }
      myChangedFiles.add(virtualFile);
    }
  }

  private void updateChangedFiles() {
    while (true) {
      VirtualFile virtualFile;
      synchronized (myChangedFiles) {
        if (myChangedFiles.isEmpty()) break;
        virtualFile = myChangedFiles.remove(myChangedFiles.size() - 1);
      }
      PsiFile psiFile = PsiManager.getInstance(myProject).findFile(virtualFile);
      if (!(psiFile instanceof PropertiesFile)) continue;
      Set<String> keys = ((PropertiesFile)psiFile).getNamesMap().keySet();
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

  public Collection<Property> findPropertiesByKey(final String key) {
    updateChangedFiles();
    Collection<VirtualFile> virtualFiles = myPropertiesMap.get(key);
    if (virtualFiles == null || virtualFiles.size() == 0) return Collections.EMPTY_LIST;
    Collection<Property> result = new ArrayList<Property>(virtualFiles.size());
    PsiManager psiManager = PsiManager.getInstance(myProject);
    for (VirtualFile file : virtualFiles) {
      PsiFile psiFile = psiManager.findFile(file);
      if (!(psiFile instanceof PropertiesFile)) {
        virtualFiles.remove(file);
        continue;
      }
      List<Property> properties = ((PropertiesFile)psiFile).findPropertiesByKey(key);
      result.addAll(properties);
    }
    return result;
  }
}
