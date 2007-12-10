package com.intellij.refactoring;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPackage;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesProcessor;
import com.intellij.refactoring.move.moveClassesOrPackages.MultipleRootsMoveDestination;

/**
 *  @author dsl
 */
public class MovePackageMultirootTest extends MultiFileTestCase {
  protected String getTestRoot() {
    return "/refactoring/movePackageMultiroot/";
  }

  public void testMovePackage() throws Exception {
    doTest(createAction(new String[]{"pack1"}, "target"));
  }

  private PerformAction createAction(final String[] packageNames, final String targetPackageName) {
    return new PerformAction() {
      public void performAction(VirtualFile rootDir, VirtualFile rootAfter) throws Exception {
        final PsiManager manager = PsiManager.getInstance(myProject);
        PsiPackage[] sourcePackages = new PsiPackage[packageNames.length];
        for (int i = 0; i < packageNames.length; i++) {
          String packageName = packageNames[i];
          sourcePackages[i] = JavaPsiFacade.getInstance(manager.getProject()).findPackage(packageName);
          assertNotNull(sourcePackages[i]);
        }
        PsiPackage targetPackage = JavaPsiFacade.getInstance(manager.getProject()).findPackage(targetPackageName);
        assertNotNull(targetPackage);
        new MoveClassesOrPackagesProcessor(myProject, sourcePackages,
                                           new MultipleRootsMoveDestination(new PackageWrapper(targetPackage)),
                                           true, true, null).run();
        FileDocumentManager.getInstance().saveAllDocuments();
      }
    };
  }

  protected void setupProject(VirtualFile rootDir) {
    final ModifiableRootModel rootModel = ModuleRootManager.getInstance(myModule).getModifiableModel();
    final ContentEntry contentEntry = rootModel.addContentEntry(rootDir);
    final VirtualFile[] children = rootDir.getChildren();
    for (int i = 0; i < children.length; i++) {
      VirtualFile child = children[i];
      if (child.getName().startsWith("src")) {
        contentEntry.addSourceFolder(child, false);
      }
    }

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        rootModel.commit();
      }
    });
  }
}
