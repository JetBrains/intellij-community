/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 20, 2001
 * Time: 11:23:12 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.analysis;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.impl.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ArrayUtil;

import java.io.File;
import java.util.*;

public class AnalysisScope {
  private static final Logger LOG = Logger.getInstance("#com.intellij.analysis.AnalysisScope");

  public static final int PROJECT = 1;
  public static final int DIRECTORY = 2;
  public static final int FILE = 3;
  public static final int MODULE = 4;
  public static final int PACKAGE = 5;
  public static final int INVALID = 6;

  private final Project myProject;
  private PsiFileFilter myFilter;
  private final Module myModule;
  private final PsiElement myElement;
  private final int myType;
  private HashSet myFilesSet;


  public interface PsiFileFilter {
    boolean accept(PsiFile file);
  }

  public static final PsiFileFilter SOURCE_JAVA_FILES = new PsiFileFilter() {
    public boolean accept(PsiFile file) {
      return file instanceof PsiJavaFile && !(file instanceof PsiCompiledElement);
    }
  };

  public static final PsiFileFilter CONTENT_FILES = new PsiFileFilter() {
    public boolean accept(PsiFile file) {
      return !(file instanceof PsiCompiledElement);
    }
  };

  public AnalysisScope(Project project, PsiFileFilter filter) {
    myProject = project;
    myFilter = filter;
    myElement = null;
    myModule = null;
    myType = PROJECT;
  }

  public AnalysisScope(Module module, PsiFileFilter filter) {
    myFilter = filter;
    myProject = null;
    myElement = null;
    myModule = module;
    myType = MODULE;
  }

  public AnalysisScope(PsiDirectory psiDirectory, PsiFileFilter filter) {
    myFilter = filter;
    myProject = null;
    myModule = null;
    myElement = psiDirectory;
    if (psiDirectory.getPackage() != null) {
      myType = DIRECTORY;
    }
    else {
      myType = INVALID;
    }
  }

  public AnalysisScope(PsiPackage psiPackage, PsiFileFilter filter) {
    myFilter = filter;
    myProject = null;
    myModule = null;
    myElement = psiPackage;
    myType = PACKAGE;
  }

  public AnalysisScope(PsiFile psiFile, PsiFileFilter filter) {
    myFilter = filter;
    myProject = null;
    myElement = psiFile;
    myModule = null;
    myType = FILE;
  }

  private PsiElementVisitor createFileSearcher() {
    PsiElementVisitor visitor = new PsiRecursiveElementVisitor() {
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        visitElement(expression);
      }

      public void visitFile(PsiFile file) {
        if (myFilter.accept(file)) {
          myFilesSet.add(file.getVirtualFile());
        }
      }
    };

    return visitor;
  }

  public boolean contains(PsiElement psiElement) {
    if (myFilesSet == null) initFilesSet();

    return myFilesSet.contains(psiElement.getContainingFile().getVirtualFile());
  }

  public boolean contains(VirtualFile file) {
    if (myFilesSet == null) initFilesSet();

    return myFilesSet.contains(file);
  }

  private void initFilesSet() {
    if (myType == FILE) {
      myFilesSet = new HashSet(1);
      myFilesSet.add(((PsiFile)myElement).getVirtualFile());
    }
    else if (myType == DIRECTORY) {
      myFilesSet = new HashSet();
      myElement.accept(createFileSearcher());
    }
    else if (myType == PROJECT || myType == MODULE || myType == PACKAGE) {
      myFilesSet = new HashSet();
      accept(createFileSearcher());
    }
  }

  public AnalysisScope[] getNarrowedComplementaryScope(Project defaultProject) {
    if (myType == PROJECT) {
      return new AnalysisScope[]{new AnalysisScope(defaultProject, SOURCE_JAVA_FILES)};
    }
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(defaultProject).getFileIndex();
    final HashSet<Module> modules = new HashSet<Module>();
    if (myType == FILE) {
      if (myElement instanceof PsiJavaFile) {
        PsiJavaFile psiJavaFile = (PsiJavaFile)myElement;
        final PsiClass[] classes = psiJavaFile.getClasses();
        boolean onlyPackLocalClasses = true;
        for (int i = 0; i < classes.length; i++) {
          final PsiClass aClass = classes[i];
          if (aClass.hasModifierProperty(PsiModifier.PUBLIC)) {
            onlyPackLocalClasses = false;
          }
        }
        if (onlyPackLocalClasses) {
          return new AnalysisScope[]{new AnalysisScope(psiJavaFile.getContainingDirectory().getPackage(), SOURCE_JAVA_FILES)};
        }
      }
      final VirtualFile vFile = ((PsiFile)myElement).getVirtualFile();
      modules.addAll(getAllInterstingModules(fileIndex, vFile));
    }
    else if (myType == DIRECTORY) {
      final VirtualFile vFile = ((PsiDirectory)myElement).getVirtualFile();
      modules.addAll(getAllInterstingModules(fileIndex, vFile));
    }
    else if (myType == PACKAGE) {
      final PsiDirectory[] directories = ((PsiPackage)myElement).getDirectories();
      for (int idx = 0; idx < directories.length; idx++) {
        modules.addAll(getAllInterstingModules(fileIndex, directories[idx].getVirtualFile()));
      }
    }
    else if (myType == MODULE) {
      modules.add(myModule);
    }

    if (modules.isEmpty()) {
      return new AnalysisScope[]{new AnalysisScope(defaultProject, SOURCE_JAVA_FILES)};
    }
    HashSet<AnalysisScope> result = new HashSet<AnalysisScope>();
    final Module[] allModules = ModuleManager.getInstance(defaultProject).getModules();
    for (int i = 0; i < allModules.length; i++) {
      for (Iterator<Module> iterator = modules.iterator(); iterator.hasNext();) {
        final Module module = iterator.next();
        if (allModules[i].equals(module)) {
          result.add(new AnalysisScope(allModules[i], SOURCE_JAVA_FILES));
          continue;
        }
        if (ModuleManager.getInstance(defaultProject).isModuleDependent(allModules[i], module)) {
          result.add(new AnalysisScope(allModules[i], SOURCE_JAVA_FILES));
        }
      }
    }
    return result.toArray(new AnalysisScope[result.size()]);
  }

  private HashSet<Module> getAllInterstingModules(final ProjectFileIndex fileIndex, final VirtualFile vFile) {
    final HashSet<Module> modules = new HashSet<Module>();
    if (fileIndex.isInLibrarySource(vFile) || fileIndex.isInLibraryClasses(vFile)) {
      final OrderEntry[] orderEntries = fileIndex.getOrderEntriesForFile(vFile);
      for (int j = 0; j < orderEntries.length; j++) {
        modules.add(orderEntries[j].getOwnerModule());
      }
    }
    else {
      modules.add(fileIndex.getModuleForFile(vFile));
    }
    return modules;
  }


  public void accept(final PsiElementVisitor visitor) {
    if (myProject != null) {
      final FileIndex projectFileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
      projectFileIndex.iterateContent(new ContentIterator() {
        public boolean processFile(VirtualFile fileOrDir) {
          if (projectFileIndex.isContentJavaSourceFile(fileOrDir)) {
            PsiFile psiFile = PsiManager.getInstance(myProject).findFile(fileOrDir);
            LOG.assertTrue(psiFile != null);
            psiFile.accept(visitor);
          }
          return true;
        }
      });
    }
    else if (myModule != null) {
      final FileIndex moduleFileIndex = ModuleRootManager.getInstance(myModule).getFileIndex();
      moduleFileIndex.iterateContent(new ContentIterator() {
        public boolean processFile(VirtualFile fileOrDir) {
          if (moduleFileIndex.isContentJavaSourceFile(fileOrDir)) {
            PsiFile psiFile = PsiManager.getInstance(myModule.getProject()).findFile(fileOrDir);
            LOG.assertTrue(psiFile != null);
            psiFile.accept(visitor);
          }
          return true;
        }
      });
    }
    else if (myElement instanceof PsiPackage) {
      PsiPackage pack = (PsiPackage)myElement;
      PsiDirectory[] dirs = pack.getDirectories(GlobalSearchScope.projectScope(myElement.getProject()));
      for (int i = 0; i < dirs.length; i++) {
        dirs[i].accept(visitor);
      }
    }
    else {
      myElement.accept(visitor);
    }
  }

  public boolean isValid() {
    if (myProject != null || myModule != null) return true;
    return myElement.isValid();
  }

  public int getScopeType() {
    return myType;
  }

  public String getDisplayName() {
    switch (myType) {
      case MODULE:
        return "Module " + pathToName(myModule.getModuleFilePath());
      case PROJECT:
        return "Project " + pathToName(myProject.getProjectFilePath());
      case FILE:
        return "File " + ((PsiFile)myElement).getVirtualFile().getPresentableUrl();

      case DIRECTORY:
        return "Directory " + ((PsiDirectory)myElement).getVirtualFile().getPresentableUrl();

      case PACKAGE:
        return "Package " + ((PsiPackage)myElement).getQualifiedName();
    }

    return "";
  }

  private static String pathToName(String path) {
    String name = path;
    if (path != null) {
      File file = new File(path);
      name = file.getName();
    }
    return name;
  }

  public int getFileCount() {
    if (myFilesSet == null) initFilesSet();
    return myFilesSet.size();
  }
}
