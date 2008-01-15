/*
 * User: anna
 * Date: 14-Jan-2008
 */
package com.intellij.packageDependencies;

import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.AnalysisScopeBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.Profile;
import com.intellij.profile.ProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class JavaAnalysisScope extends AnalysisScope {
  public static final int PACKAGE = 5;

  public JavaAnalysisScope(PsiPackage pack, Module module) {
    super(pack.getProject());
    myModule = module;
    myElement = pack;
    myType = PACKAGE;
  }

  public JavaAnalysisScope(final PsiJavaFile psiFile) {
    super(psiFile);
  }

  public AnalysisScope[] getNarrowedComplementaryScope(Project defaultProject) {
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(defaultProject).getFileIndex();
    final HashSet<Module> modules = new HashSet<Module>();
    if (myType == FILE) {
      if (myElement instanceof PsiJavaFile && !PsiUtil.isInJspFile(myElement)) {
        PsiJavaFile psiJavaFile = (PsiJavaFile)myElement;
        final PsiClass[] classes = psiJavaFile.getClasses();
        boolean onlyPackLocalClasses = true;
        for (final PsiClass aClass : classes) {
          if (aClass.hasModifierProperty(PsiModifier.PUBLIC)) {
            onlyPackLocalClasses = false;
          }
        }
        if (onlyPackLocalClasses) {
          final PsiDirectory psiDirectory = psiJavaFile.getContainingDirectory();
          if (psiDirectory != null) {
            return new AnalysisScope[]{new JavaAnalysisScope(JavaDirectoryService.getInstance().getPackage(psiDirectory), null)};
          }
        }
      }
    }
    else if (myType == PACKAGE) {
      final PsiDirectory[] directories = ((PsiPackage)myElement).getDirectories();
      for (PsiDirectory directory : directories) {
        modules.addAll(getAllInterestingModules(fileIndex, directory.getVirtualFile()));
      }
      return collectScopes(defaultProject, modules);
    }
    return super.getNarrowedComplementaryScope(defaultProject);
  }

  

  public String getShortenName() {
    if (myType == PACKAGE)
       return AnalysisScopeBundle.message("scope.package", ((PsiPackage)myElement).getQualifiedName());
    return super.getShortenName();
  }

  public String getDisplayName() {
    if (myType == PACKAGE) {
      return AnalysisScopeBundle.message("scope.package", ((PsiPackage)myElement).getQualifiedName());
    }
    return super.getDisplayName();
  }

  protected void initFilesSet() {
    if (myType == PACKAGE) {
      myFilesSet = new HashSet<VirtualFile>();
      accept(createFileSearcher());
      return;
    }
    super.initFilesSet();
  }

  public Set<String> getActiveInspectionProfiles() {
    if (myType == PACKAGE) {
      final ProjectProfileManager profileManager =
        ProjectProfileManager.getProjectProfileManager(myElement.getProject(), Profile.INSPECTION);
      assert profileManager != null;
      final PsiDirectory[] psiDirectories = ((PsiPackage)myElement).getDirectories();
      final Set<String> result = new HashSet<String>();
      processDirectories(psiDirectories, result, profileManager);
      return result;
    }
    return super.getActiveInspectionProfiles();
  }

  protected void accept(final PsiElementVisitor visitor, final boolean needReadAction) {
    if (myElement instanceof PsiPackage) {
      final PsiPackage pack = (PsiPackage)myElement;
      final Set<PsiDirectory> dirs = new HashSet<PsiDirectory>();
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          dirs.addAll(Arrays.asList(pack.getDirectories(GlobalSearchScope.projectScope(myElement.getProject()))));
        }
      });
      for (PsiDirectory dir : dirs) {
        accept(dir, visitor, needReadAction);
      }
    } else {
      super.accept(visitor, needReadAction);
    }
  }
}