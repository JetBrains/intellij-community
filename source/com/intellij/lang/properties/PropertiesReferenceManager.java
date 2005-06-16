package com.intellij.lang.properties;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;

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
            myPropertiesFilesManager.addNewFile(fileOrDir);
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
}
