package com.intellij.ide.util.newProjectWizard;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.importProject.LibraryDescriptor;
import com.intellij.ide.util.importProject.ModuleDescriptor;
import com.intellij.ide.util.importProject.ModuleInsight;
import com.intellij.ide.util.projectWizard.ProjectBuilder;
import com.intellij.ide.util.projectWizard.SourcePathsBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.*;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Jul 17, 2007
 */
public class ProjectFromSourcesBuilder extends ProjectBuilder implements SourcePathsBuilder {
  private List<Pair<String, String>> mySourcePaths = Collections.emptyList();
  private String myContentRootPath;
  private List<LibraryDescriptor> myChosenLibraries = Collections.emptyList();
  private Set<LibraryDescriptor> myChosenLibrariesSet;
  private List<ModuleDescriptor> myChosenModules = Collections.emptyList();
  private final ModuleInsight myModuleInsight;

  public ProjectFromSourcesBuilder(final ModuleInsight moduleInsight) {
    myModuleInsight = moduleInsight;
  }

  public void setContentEntryPath(final String contentRootPath) {
    myContentRootPath = contentRootPath;
  }

  public String getContentEntryPath() {
    return myContentRootPath;
  }

  public void addSourcePath(final Pair<String, String> sourcePathInfo) {
    mySourcePaths.add(sourcePathInfo);
  }

  /**
   * @param paths list of pairs [SourcePath, PackagePrefix]
   */
  public void setSourcePaths(List<Pair<String,String>> paths) {
    mySourcePaths = paths;
  }

  public List<Pair<String, String>> getSourcePaths() {
    return mySourcePaths;
  }

  public void commit(final Project project) {
    final LibraryTable projectLibraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project);
    final Map<LibraryDescriptor, Library> projectLibs = new HashMap<LibraryDescriptor, Library>();
    
    // create project-level libraries
    Exception e = ApplicationManager.getApplication().runWriteAction(new Computable<Exception>() {
      public Exception compute() {
        try {
          for (LibraryDescriptor lib : myChosenLibraries) {
            final Collection<File> files = lib.getJars();
            if (files.size() > 1) {
              final Library projectLib = projectLibraryTable.createLibrary(lib.getName());
              final Library.ModifiableModel model = projectLib.getModifiableModel();
              for (File file : files) {
                model.addRoot(VfsUtil.getUrlForLibraryRoot(file), OrderRootType.CLASSES);
              }
              model.commit();
              projectLibs.put(lib, projectLib);
            }
          }
          return null;
        }
        catch (Exception e) {
          return e;
        }
      }
    });
    if (e != null) {
      Messages.showErrorDialog(IdeBundle.message("error.adding.module.to.project", e.getMessage()), IdeBundle.message("title.add.module"));
    }
    
    
    // create modules and set up dependencies
    final Map<String, String> sourceRootToPrefixMap = new HashMap<String, String>();
    for (Pair<String, String> pair : getSourcePaths()) {
      sourceRootToPrefixMap.put(FileUtil.toSystemIndependentName(pair.getFirst()), pair.getSecond());
    }
    final Map<ModuleDescriptor, Module> descriptorToModuleMap = new HashMap<ModuleDescriptor, Module>();
    for (final ModuleDescriptor moduleDescriptor : myChosenModules) {
      Exception ex = ApplicationManager.getApplication().runWriteAction(new Computable<Exception>() {
        public Exception compute() {
          try {
            final Module module = createModule(project, moduleDescriptor, sourceRootToPrefixMap, projectLibs);
            descriptorToModuleMap.put(moduleDescriptor, module);
            return null;
          }
          catch (Exception e) {
            return e;
          }
        }
      });
      if (ex != null) {
        Messages.showErrorDialog(IdeBundle.message("error.adding.module.to.project", ex.getMessage()), IdeBundle.message("title.add.module"));
      }
    }
    
    // setup dependencies between modules
    for (final ModuleDescriptor descriptor : myChosenModules) {
      Exception ex = ApplicationManager.getApplication().runWriteAction(new Computable<Exception>() {
        public Exception compute() {
          try {
            final Module module = descriptorToModuleMap.get(descriptor);
            if (module != null) {
              final ModifiableRootModel rootModel = ModuleRootManager.getInstance(module).getModifiableModel();
              for (ModuleDescriptor dependentDescriptor : descriptor.getDependencies()) {
                final Module dependentModule = descriptorToModuleMap.get(dependentDescriptor);
                if (dependentModule != null) {
                  rootModel.addModuleOrderEntry(dependentModule);
                }
              }
              rootModel.commit();
            }

            return null;
          }
          catch (Exception e) {
            return e;
          }
        }
      });
      if (ex != null) {
        Messages.showErrorDialog(IdeBundle.message("error.adding.module.to.project", ex.getMessage()), IdeBundle.message("title.add.module"));
      }
    }
  }

  @NotNull
  public Module createModule(final Project project, final ModuleDescriptor descriptor, final Map<String, String> sourceRootToPrefixMap, final Map<LibraryDescriptor, Library> projectLibs) 
    throws InvalidDataException, IOException, ModuleWithNameAlreadyExists, JDOMException, ConfigurationException, ModuleCircularDependencyException {
    
    ModifiableModuleModel moduleModel = ModuleManager.getInstance(project).getModifiableModel();
    final String name = descriptor.getName();
    final String moduleFilePath;
    final Set<File> contentRoots = descriptor.getContentRoots();
    if (contentRoots.size() > 0) {
      moduleFilePath = contentRoots.iterator().next().getPath() + File.separator + name + ".iml";
    }
    else {
      throw new InvalidDataException("Module " + name + " has no content roots and will not be created");
    }
    
    final Module module = moduleModel.newModule(moduleFilePath, ModuleType.JAVA);
    final ModifiableRootModel modifiableModel = ModuleRootManager.getInstance(module).getModifiableModel();
    setupRootModel(descriptor, modifiableModel, sourceRootToPrefixMap, projectLibs);
    modifiableModel.commit();

    module.setSavePathsRelative(true); // default setting

    moduleModel.commit();
    return module;
  }

  private void setupRootModel(final ModuleDescriptor descriptor, final ModifiableRootModel rootModel, final Map<String, String> sourceRootToPrefixMap, final Map<LibraryDescriptor, Library> projectLibs) {
    rootModel.setExcludeOutput(true);
    rootModel.inheritJdk();

    final Set<File> contentRoots = descriptor.getContentRoots();
    for (File contentRoot : contentRoots) {
      final LocalFileSystem lfs = LocalFileSystem.getInstance();
      VirtualFile moduleContentRoot = lfs.refreshAndFindFileByPath(FileUtil.toSystemIndependentName(contentRoot.getPath()));
      if (moduleContentRoot != null) {
        final ContentEntry contentEntry = rootModel.addContentEntry(moduleContentRoot);
        final Set<File> sourceRoots = descriptor.getSourceRoots(contentRoot);
        for (File srcRoot : sourceRoots) {
          final String srcpath = FileUtil.toSystemIndependentName(srcRoot.getPath());
          final VirtualFile sourceRoot = lfs.refreshAndFindFileByPath(srcpath);
          if (sourceRoot != null) {
            final String packagePrefix = sourceRootToPrefixMap.get(srcpath);
            if (packagePrefix == null || "".equals(packagePrefix)) {
              contentEntry.addSourceFolder(sourceRoot, false);
            }
            else {
              contentEntry.addSourceFolder(sourceRoot, false, packagePrefix);
            }
          }
        }
      }
    }

    rootModel.inheritCompilerOutputPath(true);

    final LibraryTable moduleLibraryTable = rootModel.getModuleLibraryTable();
    for (LibraryDescriptor libDescriptor : myModuleInsight.getLibraryDependencies(descriptor)) {
      if (!isLibraryChosen(libDescriptor)) {
        continue;
      }
      final Library projectLib = projectLibs.get(libDescriptor);
      if (projectLib != null) {
        rootModel.addLibraryEntry(projectLib);
      }
      else {
        // add as module library
        final Collection<File> jars = libDescriptor.getJars();
        for (File file : jars) {
          Library library = moduleLibraryTable.createLibrary();
          Library.ModifiableModel modifiableModel = library.getModifiableModel();
          modifiableModel.addRoot(VfsUtil.getUrlForLibraryRoot(file), OrderRootType.CLASSES);
          modifiableModel.commit();
        }
      }
    }

  }

  public void setLibraries(final List<LibraryDescriptor> libraries) {
    myChosenLibraries = (libraries == null) ? Collections.<LibraryDescriptor>emptyList() : libraries;
    myChosenLibrariesSet = null;
  }

  public List<LibraryDescriptor> getLibraries() {
    return myChosenLibraries;
  }

  public void setModules(final List<ModuleDescriptor> modules) {
    myChosenModules = (modules == null) ? Collections.<ModuleDescriptor>emptyList() : modules;
  }

  public List<ModuleDescriptor> getModules() {
    return myChosenModules;
  }
  
  private boolean isLibraryChosen(LibraryDescriptor lib) {
    Set<LibraryDescriptor> available = myChosenLibrariesSet;
    if (available == null) {
      available = new HashSet<LibraryDescriptor>(myChosenLibraries);
      myChosenLibrariesSet = available;
    }
    return available.contains(lib);
  }
}
