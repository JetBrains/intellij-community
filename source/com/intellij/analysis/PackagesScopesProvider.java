/*
 * User: anna
 * Date: 16-Jan-2008
 */
package com.intellij.analysis;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.psi.search.scope.packageSet.PackageSet;
import com.intellij.psi.search.scope.packageSet.PatternPackageSet;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class PackagesScopesProvider implements CustomScopesProvider {
  private NamedScope myProjectTestScope;
  private NamedScope myProjectProductionScope;
  private NamedScope myProblemsScope;

  private final Project myProject;

  public static PackagesScopesProvider getInstance(Project project) {
    for (CustomScopesProvider provider : Extensions.getExtensions(CUSTOM_SCOPES_PROVIDER, project)) {
      if (provider instanceof PackagesScopesProvider) return (PackagesScopesProvider)provider;
    }
    return null;
  }

  public PackagesScopesProvider(Project project) {
    myProject = project;
  }

  @NotNull
  public List<NamedScope> getCustomScopes() {
    final List<NamedScope> list = new ArrayList<NamedScope>();
    list.add(getProjectProductionScope());
    list.add(getProjectTestScope());
    list.add(getProblemsScope());
    return list;
  }

    public NamedScope getProjectTestScope() {
    if (myProjectTestScope == null) {
      final ProjectFileIndex index = ProjectRootManager.getInstance(myProject).getFileIndex();
      myProjectTestScope = new NamedScope(IdeBundle.message("predefined.scope.tests.name"), new PackageSet() {
        public boolean contains(PsiFile file, NamedScopesHolder holder) {
          final VirtualFile virtualFile = file.getVirtualFile();
          return file.getProject() == myProject && virtualFile != null && index.isInTestSourceContent(virtualFile);
        }

        public PackageSet createCopy() {
          return this;
        }

        public String getText() {
          return PatternPackageSet.SCOPE_TEST+":*..*";
        }

        public int getNodePriority() {
          return 0;
        }
      });
    }
    return myProjectTestScope;
  }

  public NamedScope getProjectProductionScope() {
    if (myProjectProductionScope == null) {
      final ProjectFileIndex index = ProjectRootManager.getInstance(myProject).getFileIndex();
      myProjectProductionScope = new NamedScope(IdeBundle.message("predefined.scope.production.name"), new PackageSet() {
        public boolean contains(PsiFile file, NamedScopesHolder holder) {
          final VirtualFile virtualFile = file.getVirtualFile();
          return file.getProject() == myProject
                 && virtualFile != null
                 && !index.isInTestSourceContent(virtualFile)
                 && !index.isInLibraryClasses(virtualFile)
                 && !index.isInLibrarySource(virtualFile)
            ;
        }

        public PackageSet createCopy() {
          return this;
        }

        public String getText() {
          return PatternPackageSet.SCOPE_SOURCE+":*..*";
        }

        public int getNodePriority() {
          return 0;
        }
      });
    }
    return myProjectProductionScope;
  }

  public NamedScope getProblemsScope() {
    if (myProblemsScope == null) {
      myProblemsScope = new NamedScope(IdeBundle.message("predefined.scope.problems.name"), new PackageSet() {
        public boolean contains(PsiFile file, NamedScopesHolder holder) {
          return file.getProject() == myProject && WolfTheProblemSolver.getInstance(myProject).isProblemFile(file.getVirtualFile());
        }

        public PackageSet createCopy() {
          return this;
        }

        public String getText() {
          return PatternPackageSet.SCOPE_PROBLEM + ":*..*";
        }

        public int getNodePriority() {
          return 1;
        }
      });
    }
    return myProblemsScope;
  }
}