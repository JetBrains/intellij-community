package com.intellij.lang.properties;

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import gnu.trove.THashMap;

import java.util.Collection;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 27, 2005
 * Time: 6:00:51 PM
 * To change this template use File | Settings | File Templates.
 */
public class PropertiesReferenceManager implements ProjectComponent {
  private final Project myProject;
  private final Map<VirtualFile, PropertiesFile> myAllPropertiesFiles = new THashMap<VirtualFile, PropertiesFile>();
  private VirtualFileListener myVirtualFileListener;

  public static PropertiesReferenceManager getInstance(Project project) {
    return project.getComponent(PropertiesReferenceManager.class);
  }

  public PropertiesReferenceManager(Project project) {
    myProject = project;
  }

  public void projectOpened() {
    final PsiReferenceProvider referenceProvider = new PropertiesReferenceProvider();
    ReferenceProvidersRegistry.getInstance(myProject).registerReferenceProvider(PsiLiteralExpression.class, referenceProvider);

    myVirtualFileListener = new VirtualFileAdapter() {
      public void fileCreated(VirtualFileEvent event) {
        addNewFile(event);
      }

      public void fileDeleted(VirtualFileEvent event) {
        removeOldFile(event);
      }

      public void fileMoved(VirtualFileMoveEvent event) {
        removeOldFile(event);
        addNewFile(event);
      }
    };
    VirtualFileManager.getInstance().addVirtualFileListener(myVirtualFileListener);
    StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
      public void run() {
        ProjectRootManager.getInstance(myProject).getFileIndex().iterateContent(new ContentIterator() {
          public boolean processFile(VirtualFile fileOrDir) {
            addNewFile(fileOrDir);
            return true;
          }
        });
      }
    });
  }

  private void removeOldFile(final VirtualFileEvent event) {
    myAllPropertiesFiles.remove(event.getFile());
  }

  private void addNewFile(final VirtualFileEvent event) {
    VirtualFile file = event.getFile();
    addNewFile(file);
  }

  private void addNewFile(final VirtualFile file) {
    PsiFile psiFile = PsiManager.getInstance(myProject).findFile(file);
    if (psiFile instanceof PropertiesFile) {
      myAllPropertiesFiles.put(file, (PropertiesFile)psiFile);
    }
  }

  public Collection<PropertiesFile> getAllPropertiesFiles() {
    return myAllPropertiesFiles.values();
  }

  public void projectClosed() {
    VirtualFileManager.getInstance().removeVirtualFileListener(myVirtualFileListener);
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public String getComponentName() {
    return "Properties support manager";
  }
}
