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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.jsp.JspFile;
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
  public static final int MODULES = 7;

  private final Project myProject;
  private final List<Module> myModules;
  private final Module myModule;
  private final PsiElement myElement;
  private final int myType;
  private HashSet<VirtualFile> myFilesSet;

  private boolean myIncludeTestSource = true;

  public AnalysisScope(Project project) {
    myProject = project;
    myElement = null;
    myModules = null;
    myModule = null;
    myType = PROJECT;
  }

  public AnalysisScope(Module module) {
    myProject = null;
    myElement = null;
    myModules = null;
    myModule = module;
    myType = MODULE;
  }

  public AnalysisScope(final Module[] modules) {
    myModules = Arrays.asList(modules);
    myModule = null;
    myProject = null;
    myElement = null;
    myType = MODULES;
  }

  public AnalysisScope(PsiDirectory psiDirectory) {
    myProject = null;
    myModules = null;
    myModule = null;
    myElement = psiDirectory;
    if (psiDirectory.getPackage() != null) {
      myType = DIRECTORY;
    }
    else {
      myType = INVALID;
    }
  }

  public AnalysisScope(PsiPackage psiPackage) {
    myProject = null;
    myModule = null;
    myModules = null;
    myElement = psiPackage;
    myType = PACKAGE;
  }

  public AnalysisScope(PsiFile psiFile) {
    myProject = null;
    myElement = psiFile;
    myModule = null;
    myModules = null;
    myType = FILE;
  }

  public void setIncludeTestSource(final boolean includeTestSource) {
    myIncludeTestSource = includeTestSource;
  }

  private PsiElementVisitor createFileSearcher() {
    final FileIndex fileIndex;
    if (myProject != null){
      fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
    } else if (myModule != null){
      fileIndex = ModuleRootManager.getInstance(myModule).getFileIndex();
    } else if (myModules != null && myModules.size() > 0){
      fileIndex = ModuleRootManager.getInstance(myModules.get(0)).getFileIndex();
    } else if (myElement != null){
      fileIndex = ProjectRootManager.getInstance(myElement.getProject()).getFileIndex();
    } else {
      //can't be
      fileIndex = null;
    }
    PsiElementVisitor visitor = new PsiRecursiveElementVisitor() {
      public void visitFile(PsiFile file) {
        if (/*file instanceof PsiJavaFile && */!(file instanceof PsiCompiledElement)) {
          final VirtualFile virtualFile = file.getVirtualFile();
          if (!myIncludeTestSource){
            if (fileIndex == null || fileIndex.isInTestSourceContent(virtualFile)){
              return;
            }
          }
          myFilesSet.add(virtualFile);
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
      myFilesSet = new HashSet<VirtualFile>(1);
      myFilesSet.add(((PsiFile)myElement).getVirtualFile());
    }
    else if (myType == DIRECTORY) {
      myFilesSet = new HashSet<VirtualFile>();
      myElement.accept(createFileSearcher());
    }
    else if (myType == PROJECT || myType == MODULES || myType == MODULE || myType == PACKAGE) {
      myFilesSet = new HashSet<VirtualFile>();
      accept(createFileSearcher());
    }
  }

  public AnalysisScope[] getNarrowedComplementaryScope(Project defaultProject) {
    if (myType == PROJECT) {
      return new AnalysisScope[]{new AnalysisScope(defaultProject)};
    }
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(defaultProject).getFileIndex();
    final HashSet<Module> modules = new HashSet<Module>();
    if (myType == FILE) {
      if (myElement instanceof PsiJavaFile && !(myElement instanceof JspFile)) {
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
          return new AnalysisScope[]{new AnalysisScope(psiJavaFile.getContainingDirectory().getPackage())};
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
    } else if (myType == MODULES){
      modules.addAll(myModules);
    }

    if (modules.isEmpty()) {
      return new AnalysisScope[]{new AnalysisScope(defaultProject)};
    }
    HashSet<AnalysisScope> result = new HashSet<AnalysisScope>();
    final Module[] allModules = ModuleManager.getInstance(defaultProject).getModules();
    for (Iterator<Module> iterator = modules.iterator(); iterator.hasNext();) {
      final Module module = iterator.next();
      Set<Module> modulesToAnalyze = getDirectBackwardDependencies(module, allModules);
      modulesToAnalyze.addAll(getExportBackwardDependencies(module, allModules));
      modulesToAnalyze.add(module);
      result.add(new AnalysisScope(modulesToAnalyze.toArray(new Module[modulesToAnalyze.size()])));
    }
    return result.toArray(new AnalysisScope[result.size()]);
  }


  private static Set<Module> getExportBackwardDependencies(Module fromModule, Module [] allModules) {
    Set<Module> result = new HashSet<Module>();
    for (int i = 0; i < allModules.length; i++) {
      Module module = allModules[i];
      final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
      final OrderEntry[] orderEntries = moduleRootManager.getOrderEntries();
      for (int j = 0; j < orderEntries.length; j++) {
        OrderEntry orderEntry = orderEntries[j];
        if (orderEntry instanceof ModuleOrderEntry && ((ModuleOrderEntry)orderEntry).isExported() && fromModule == ((ModuleOrderEntry)orderEntry).getModule()){
          result.addAll(getDirectBackwardDependencies(module, allModules));
        }
      }
    }
    return result;
  }

  private static Set<Module> getDirectBackwardDependencies(Module module, Module [] allModules) {
    Set<Module> result = new HashSet<Module>();
    for (int i = 0; i < allModules.length; i++) {
      Module dependency = allModules[i];
      if (ArrayUtil.find(ModuleRootManager.getInstance(dependency).getDependencies(), module) > -1){
        result.add(dependency);
      }
    }
    return result;
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
          if (fileOrDir.isDirectory()) return true;
          if (projectFileIndex.isInContent(fileOrDir) && (myIncludeTestSource ? true : !projectFileIndex.isInTestSourceContent(fileOrDir))) {
            PsiFile psiFile = PsiManager.getInstance(myProject).findFile(fileOrDir);
            if (psiFile == null) return true; //skip .class files under src directory
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
          if (fileOrDir.isDirectory()) return true;
          if (moduleFileIndex.isInContent(fileOrDir) && (myIncludeTestSource ? true : !moduleFileIndex.isInTestSourceContent(fileOrDir))) {
            PsiFile psiFile = PsiManager.getInstance(myModule.getProject()).findFile(fileOrDir);
            if (psiFile == null) return true; //skip .class files under src directory
            psiFile.accept(visitor);
          }
          return true;
        }
      });
    }
    else if (myModules != null) {
      for (Iterator<Module> iterator = myModules.iterator(); iterator.hasNext();) {
        final Module module = iterator.next();
        final FileIndex moduleFileIndex = ModuleRootManager.getInstance(module).getFileIndex();
        moduleFileIndex.iterateContent(new ContentIterator() {
          public boolean processFile(VirtualFile fileOrDir) {
            if (fileOrDir.isDirectory()) return true;
            if (moduleFileIndex.isInContent(fileOrDir) && (myIncludeTestSource ? true : !moduleFileIndex.isInTestSourceContent(fileOrDir))) {
              PsiFile psiFile = PsiManager.getInstance(module.getProject()).findFile(fileOrDir);
              if (psiFile == null) return true; //skip .class files under src directory
              psiFile.accept(visitor);
            }
            return true;
          }
        });
      }
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
    if (myProject != null) return true;
    if (myModule != null && !myModule.isDisposed()) return true;
    if (myModules != null){
      for (Module module : myModules) {
        if (module.isDisposed()) return false;
      }
      return true;
    }
    return myElement != null && myElement.isValid();
  }

  public int getScopeType() {
    return myType;
  }

  public String getDisplayName() {
    switch (myType) {
      case MODULE:
        return "Module " + pathToName(myModule.getModuleFilePath());
      case MODULES:
        String displayName = "Module" + (myModules.size() > 1 ? "s " : " ");
        for (Iterator<Module> iterator = myModules.iterator(); iterator.hasNext();) {
          Module module = iterator.next();
          displayName += pathToName(module.getModuleFilePath()) + ", ";
        }
        displayName = displayName.substring(0, displayName.length() - 2);
        return displayName;
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

  public String getShortenName(){
    switch (myType) {
      case MODULE:
        return "Module \'" + myModule.getName() + "\'";
      case MODULES:
        String displayName = "Module" + (myModules.size() > 1 ? "s " : " ");
        for (Iterator<Module> iterator = myModules.iterator(); iterator.hasNext();) {
          Module module = iterator.next();
          displayName += "\'" + module.getName() + "\', ";
        }
        displayName = displayName.substring(0, displayName.length() - 2);
        return displayName;
      case PROJECT:
        return "Project \'" + myProject.getName() + "\'";
      case FILE:
        return "File " + VfsUtil.calcRelativeToProjectPath(((PsiFile)myElement).getVirtualFile(), myElement.getProject());
      case DIRECTORY:
        return "Directory " + VfsUtil.calcRelativeToProjectPath(((PsiDirectory)myElement).getVirtualFile(), myElement.getProject());

      case PACKAGE:
        return "Package " + ((PsiPackage)myElement).getQualifiedName();
    }

    return "";
  }

  private static String pathToName(String path) {
    String name = path;
    if (path != null) {
      File file = new File(path);
      name = FileUtil.getNameWithoutExtension(file);
    }
    return name;
  }

  public int getFileCount() {
    if (myFilesSet == null) initFilesSet();
    return myFilesSet.size();
  }

  public void invalidate(){
    myFilesSet = null;
  }

}
