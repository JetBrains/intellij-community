package com.intellij.openapi.module.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.components.LoadCancelledException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.*;
import com.intellij.openapi.project.ModuleListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.EventDispatcher;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import com.intellij.util.graph.CachingSemiGraph;
import com.intellij.util.graph.DFSTBuilder;
import com.intellij.util.graph.Graph;
import com.intellij.util.graph.GraphGenerator;
import com.intellij.application.options.PathMacros;
import com.intellij.pom.PomModel;
import org.jdom.Element;
import org.jdom.JDOMException;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author max
 */
public class ModuleManagerImpl extends ModuleManager implements ProjectComponent, JDOMExternalizable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.module.impl.ModuleManagerImpl");
  private final EventDispatcher<ModuleListener> myModuleEventDispatcher = EventDispatcher.create(ModuleListener.class);
  private final Project myProject;
  private ModuleModelImpl myModuleModel = new ModuleModelImpl();
  private Map<Module, String> myModuleGroup;
  private PomModel myPomModel;

  private final ModuleRootListener myModuleRootListener = new ModuleRootListener() {
    public void beforeRootsChange(ModuleRootEvent event) {
      cleanCachedStuff();
    }

    public void rootsChanged(ModuleRootEvent event) {
      cleanCachedStuff();
    }
  };
  public static final String COMPONENT_NAME = "ProjectModuleManager";

  public static ModuleManagerImpl getInstanceImpl(Project project) {
    return (ModuleManagerImpl)getInstance(project);
  }

  private void cleanCachedStuff() {
    myCachedModuleComparator = null;
    myCachedSortedModules = null;
  }

  public ModuleManagerImpl(Project project, ProjectRootManager projectRootManager, PomModel pomModel) {
    myProject = project;
    projectRootManager.addModuleRootListener(myModuleRootListener);
    myPomModel = pomModel;
  }


  public String getComponentName() {
    return COMPONENT_NAME;
  }

  public void initComponent() {
  }

  public void disposeComponent() {
    myModuleModel.disposeModel();
    ProjectRootManager.getInstance(myProject).removeModuleRootListener(myModuleRootListener);
  }

  public static final class ModulePath {
    private final String myPath;
    private final String myModuleGroup;

    public ModulePath(String path, String moduleGroup) {
      myPath = path;
      myModuleGroup = moduleGroup;
    }

    public String getPath() {
      return myPath;
    }

    public String getModuleGroup() {
      return myModuleGroup;
    }
  }

  public static ModulePath[] getPathsToModuleFiles(Element element) {
    final List<ModulePath> paths = new ArrayList<ModulePath>();
    final Element modules = element.getChild("modules");
    if (modules != null) {
      for (Iterator iterator = modules.getChildren("module").iterator(); iterator.hasNext();) {
        Element moduleElement = (Element)iterator.next();
        final String fileUrlValue = moduleElement.getAttributeValue("fileurl");
        final String filepath;
        if (fileUrlValue != null) {
          filepath = VirtualFileManager.extractPath(fileUrlValue).replace('/', File.separatorChar);
        }
        else {
          // [dsl] support for older formats
          filepath = moduleElement.getAttributeValue("filepath").replace('/', File.separatorChar);
        }
        final String group = moduleElement.getAttributeValue("group");
        paths.add(new ModulePath(filepath, group));
      }
    }
    return paths.toArray(new ModulePath[paths.size()]);
  }

  public void readExternal(final Element element) throws InvalidDataException {
    final ModulePath[] paths = getPathsToModuleFiles(element);
    if (paths.length > 0) {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          for (int idx = 0; idx < paths.length; idx++) {
            final ModulePath modulePath = paths[idx];
            try {
              final Module module = myModuleModel.loadModuleInternal(modulePath.getPath());
              final String group = modulePath.getModuleGroup();
              if (group != null) {
                setModuleGroup(module, group);
              }
            }
            catch (final IOException e) {
              ApplicationManager.getApplication().invokeLater(new Runnable() {
                public void run() {
                  Messages.showMessageDialog("Cannot load module: " + e.getMessage(), "Cannot Load Module",
                                             Messages.getErrorIcon());
                }
              });
            }
            catch (JDOMException e) {
              ApplicationManager.getApplication().invokeLater(new Runnable() {
                public void run() {
                  Messages.showMessageDialog("Corruped module file: " + modulePath, "Cannot Load Module",
                                             Messages.getErrorIcon());
                }
              });
            }
            catch (InvalidDataException e) {
              ApplicationManager.getApplication().invokeLater(new Runnable() {
                public void run() {
                  Messages.showMessageDialog("Corruped module data at: " + modulePath, "Cannot Load Module",
                                             Messages.getErrorIcon());
                }
              });
            }
            catch (final ModuleWithNameAlreadyExists moduleWithNameAlreadyExists) {
              ApplicationManager.getApplication().invokeLater(new Runnable() {
                public void run() {
                  Messages.showMessageDialog(moduleWithNameAlreadyExists.getMessage(), "Cannot Load Module",
                                             Messages.getErrorIcon());
                }
              });
            }
            catch (final LoadCancelledException e) {
              ApplicationManager.getApplication().invokeLater(new Runnable() {
                public void run() {
                  int response = Messages.showDialog("Cancelled loading of module from:" + modulePath.getPath() + "\n" +
                                                     "Cancelled by component: " + e.getIssuer().getComponentName() + "\n" +
                                                     "Reason is: " + e.getMessage(),
                                                     "Module Loading Cancelled", new String[]{"Try to load &later", "&Remove from project"}, 0,
                                                     Messages.getErrorIcon());
                  if (response == 1) {
                    myModuleModel.myPath2CancelledModelMap.remove(modulePath.getPath());
                  }
                }
              });
            }
          }
        }
      });
    }
  }

  public ModifiableModuleModel getModifiableModel() {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    return new ModuleModelImpl(myModuleModel);
  }


  public void writeExternal(Element element) throws WriteExternalException {
    Element modules = new Element("modules");
    final Collection<Module> collection = getModulesToWrite();
    ArrayList<Module> sorted = new ArrayList<Module>(collection);
    Collections.sort(sorted, new Comparator<Module>() {
      public int compare(Module module, Module module1) {
        return module.getName().compareTo(module1.getName());
      }
    });
    for (Iterator iterator = sorted.iterator(); iterator.hasNext();) {
      ModuleImpl module = (ModuleImpl)iterator.next();
      if (module.isDefault()) continue;
      Element moduleElement = new Element("module");
      final String moduleFilePath = module.getModuleFilePath().replace(File.separatorChar, '/');
      final String url = VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, moduleFilePath);
      moduleElement.setAttribute("fileurl", url);
      // [dsl] support for older builds
      moduleElement.setAttribute("filepath", moduleFilePath);

      String group = getModuleGroup(module);
      if (group != null) {
        moduleElement.setAttribute("group", group);
      }

      modules.addContent(moduleElement);
    }

    element.addContent(modules);
  }

  private Collection<Module> getModulesToWrite() {
    Collection<Module> actual = new ArrayList<Module>();
    actual.addAll(myModuleModel.myPath2ModelMap.values());

    Iterator<String> cancelled = myModuleModel.myPath2CancelledModelMap.keySet().iterator();
    while (cancelled.hasNext()) {
      String cancelledPath = cancelled.next();
      if (!myModuleModel.myPath2ModelMap.containsKey(cancelledPath)) {
        actual.add(myModuleModel.myPath2CancelledModelMap.get(cancelledPath));
      }
    }
    return actual;
  }

  private void fireModuleAdded(Module module) {
    myModuleEventDispatcher.getMulticaster().moduleAdded(myProject, module);
  }

  private void fireModuleRemoved(Module module) {
    myModuleEventDispatcher.getMulticaster().moduleRemoved(myProject, module);
  }

  private void fireBeforeModuleRemoved(Module module) {
    myModuleEventDispatcher.getMulticaster().beforeModuleRemoved(myProject, module);
  }


  public void addModuleListener(ModuleListener listener) {
    myModuleEventDispatcher.addListener(listener);
  }

  public void removeModuleListener(ModuleListener listener) {
    myModuleEventDispatcher.removeListener(listener);
  }

  public void dispatchPendingEvent(ModuleListener listener) {
    myModuleEventDispatcher.dispatchPendingEvent(listener);
  }

  public Module newModule(String filePath) {
    final ModifiableModuleModel modifiableModel = getModifiableModel();
    final Module module = modifiableModel.newModule(filePath);
    modifiableModel.commitAssertingNoCircularDependency();
    return module;
  }

  public Module newModule(String filePath, ModuleType moduleType) {
    final ModifiableModuleModel modifiableModel = getModifiableModel();
    final Module module = modifiableModel.newModule(filePath, moduleType);
    modifiableModel.commitAssertingNoCircularDependency();
    return module;

  }

  public Module loadModule(String filePath) throws InvalidDataException,
                                                   IOException,
                                                   JDOMException,
                                                   ModuleWithNameAlreadyExists,
                                                   ModuleCircularDependencyException {
    final ModifiableModuleModel modifiableModel = getModifiableModel();
    final Module module = modifiableModel.loadModule(filePath);
    modifiableModel.commit();
    return module;
  }

  public void disposeModule(final Module module) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        final ModifiableModuleModel modifiableModel = getModifiableModel();
        modifiableModel.disposeModule(module);
        modifiableModel.commitAssertingNoCircularDependency();
      }
    });
  }

  public Module[] getModules() {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    return myModuleModel.getModules();
  }

  private Module[] myCachedSortedModules = null;

  public Module[] getSortedModules() {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    ProjectRootManager.getInstance(myProject).dispatchPendingEvent(myModuleRootListener);
    if (myCachedSortedModules == null) {
      myCachedSortedModules = myModuleModel.getSortedModules();
    }
    return myCachedSortedModules;
  }

  public Module findModuleByName(String name) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    return myModuleModel.findModuleByName(name);
  }

  private Comparator<Module> myCachedModuleComparator = null;

  public Comparator<Module> moduleDependencyComparator() {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    ProjectRootManager.getInstance(myProject).dispatchPendingEvent(myModuleRootListener);
    if (myCachedModuleComparator == null) {
      myCachedModuleComparator = myModuleModel.moduleDependencyComparator();
    }
    return myCachedModuleComparator;
  }

  public Graph<Module> moduleGraph() {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    return myModuleModel.moduleGraph();
  }

  public Module[] getModuleDependentModules(Module module) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    return myModuleModel.getModuleDependentModules(module);
  }

  public boolean isModuleDependent(Module module, Module onModule) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    return myModuleModel.isModuleDependent(module, onModule);
  }

  public void projectOpened() {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        final Module[] modules = myModuleModel.getModules();
        for (int i = 0; i < modules.length; i++) {
          ModuleImpl module = (ModuleImpl)modules[i];
          module.moduleAdded();
          fireModuleAdded(module);
        }
      }
    });
    myModuleModel.projectOpened();
  }

  public void projectClosed() {
    myModuleModel.projectClosed();
  }

  public void commitModelWithRunnable(ModifiableModuleModel model, Runnable runnable) {
    ((ModuleModelImpl)model).commitWithRunnable(runnable);
  }

  class ModuleModelImpl implements ModifiableModuleModel {
    private Map<String, Module> myPath2ModelMap = new HashMap<String, Module>();
    private Map<String, Module> myPath2CancelledModelMap = new HashMap<String, Module>();

    private List<Module> myModulesToDispose = new ArrayList<Module>();
    private Map<Module, String> myModulesToNewNamesMap = new HashMap<Module, String>();
    private Map<String, Module> myNewNamesToModulesMap = new HashMap<String, Module>();
    private boolean myIsWritable;

    ModuleModelImpl() {
      myIsWritable = false;
    }

    ModuleModelImpl(ModuleModelImpl that) {
      myPath2ModelMap.putAll(that.myPath2ModelMap);
      myIsWritable = true;
      myPomModel = myProject.getModel();
    }

    private void assertWritable() {
      LOG.assertTrue(myIsWritable, "Attempt to modify commited ModifiableModuleModel");
    }

    public Module[] getModules() {
      Collection<Module> modules = myPath2ModelMap.values();
      return modules.toArray(new Module[modules.size()]);
    }

    private Module[] getSortedModules() {
      Module[] allModules = getModules();
      Arrays.sort(allModules, moduleDependencyComparator());
      return allModules;
    }

    public Module newModule(String filePath) {
      assertWritable();
      return newModule(filePath, ModuleType.JAVA);
    }

    public void renameModule(Module module, String newName) throws ModuleWithNameAlreadyExists {
      final Module oldModule = getModuleByNewName(newName);
      if (oldModule != null) {
        throw new ModuleWithNameAlreadyExists(newName);
      }
      final String oldName = myModulesToNewNamesMap.get(module);
      myModulesToNewNamesMap.put(module, newName);
      myNewNamesToModulesMap.remove(oldName);
      myNewNamesToModulesMap.put(newName, module);
    }

    public Module getModuleToBeRenamed(String newName) {
      return myNewNamesToModulesMap.get(newName);
    }

    public Module getModuleByNewName(String newName) {
      final Module moduleToBeRenamed = getModuleToBeRenamed(newName);
      if (moduleToBeRenamed != null) return moduleToBeRenamed;
      final Module moduleWithOldName = findModuleByName(newName);
      if (myModulesToNewNamesMap.get(moduleWithOldName) == null) {
        return moduleWithOldName;
      }
      else {
        return null;
      }
    }

    public String getNewName(Module module) {
      return myModulesToNewNamesMap.get(module);
    }

    public Module newModule(String filePath, ModuleType moduleType) {
      assertWritable();
      try {
        String canonicalPath = new File(filePath.replace('/', File.separatorChar)).getCanonicalPath();
        if (canonicalPath != null) {
          filePath = canonicalPath;
        }
      }
      catch (IOException e) {
      }

      ModuleImpl module = getModuleByFilePath(filePath);
      if (module == null) {
        module = new ModuleImpl(filePath, myProject, myPomModel, PathMacros.getInstance());
        module.setModuleType(moduleType);
        module.loadModuleComponents();
        initModule(module, false);
      }
      return module;
    }

    private ModuleImpl getModuleByFilePath(String filePath) {
      final Collection<Module> modules = myPath2ModelMap.values();
      for (Iterator<Module> iterator = modules.iterator(); iterator.hasNext();) {
        Module module = iterator.next();
        if (filePath.equals(module.getModuleFilePath())) {
          return (ModuleImpl)module;
        }
      }
      return null;
    }

    public Module loadModule(String filePath) throws InvalidDataException,
                                                     IOException,
                                                     JDOMException,
                                                     ModuleWithNameAlreadyExists {
      assertWritable();
      return loadModuleInternal(filePath);

    }

    private Module loadModuleInternal(String filePath) throws ModuleWithNameAlreadyExists,
                                                              JDOMException,
                                                              IOException,
                                                              InvalidDataException,
                                                              LoadCancelledException {
      final File moduleFile = new File(filePath);
      try {
        String canonicalPath = moduleFile.getCanonicalPath();
        if (canonicalPath != null) {
          filePath = canonicalPath;
        }
      }
      catch (IOException e) {
      }

      final String name = moduleFile.getName();
      if (name.endsWith(".iml")) {
        final String moduleName = name.substring(0, name.length() - 4);
        final Module[] modules = getModules();
        for (int i = 0; i < modules.length; i++) {
          Module module = modules[i];
          if (module.getName().equals(moduleName)) {
            throw new ModuleWithNameAlreadyExists(moduleName);
          }
        }
      }
      if (!moduleFile.exists()) {
        throw new IOException("File " + moduleFile.getPath() + " does not exist");
      }
      ModuleImpl module = getModuleByFilePath(filePath);
      if (module == null) {
        module = new ModuleImpl(filePath, myProject, myPomModel, PathMacros.getInstance());
        module.loadSavedConfiguration();
        module.loadModuleComponents();
        initModule(module, true);
      }
      return module;
    }

    private void initModule(ModuleImpl module, boolean saveToCancelled) throws LoadCancelledException {
      String path = module.getModuleFilePath();
      try {
        myPath2ModelMap.put(path, module);
        module.init();
      }
      catch (LoadCancelledException e) {
        myPath2ModelMap.remove(path);
        if (saveToCancelled) {
          myPath2CancelledModelMap.put(path, module);
        }
        throw e;
      }
    }

    public void disposeModule(Module module) {
      assertWritable();
      if (myPath2ModelMap.values().contains(module)) {
        myPath2ModelMap.values().remove(module);
        myModulesToDispose.add(module);
      }
    }

    public Module findModuleByName(String name) {
      final Module[] allModules = getModules();
      for (int i = 0; i < allModules.length; i++) {
        Module module = allModules[i];
        if (module.getName().equals(name)) {
          return module;
        }
      }
      return null;
    }

    private Comparator<Module> moduleDependencyComparator() {
      DFSTBuilder<Module> builder = new DFSTBuilder<Module>(moduleGraph());
      return builder.comparator();
    }

    private Graph<Module> moduleGraph() {
      final Graph<Module> graph = GraphGenerator.create(CachingSemiGraph.create(new GraphGenerator.SemiGraph<Module>() {
        public Collection<Module> getNodes() {
          return Arrays.asList(getModules());
        }

        public Iterator<Module> getIn(Module m) {
          Module[] dependentModules = ModuleRootManager.getInstance(m).getDependencies();
          return Arrays.asList(dependentModules).iterator();
        }
      }));
      return graph;
    }

    private Module[] getModuleDependentModules(Module module) {
      List<Module> result = new ArrayList<Module>();
      Module[] modules = getModules();
      for (int i = 0; i < modules.length; i++) {
        Module aModule = modules[i];
        if (isModuleDependent(aModule, module)) {
          result.add(aModule);
        }
      }
      return result.toArray(new Module[result.size()]);
    }

    private boolean isModuleDependent(Module module, Module onModule) {
      final OrderEntry[] orderEntries = ModuleRootManager.getInstance(module).getOrderEntries();
      for (int j = 0; j < orderEntries.length; j++) {
        OrderEntry entry = orderEntries[j];
        if (entry instanceof ModuleOrderEntry) {
          if (((ModuleOrderEntry)entry).getModule() == onModule) {
            return true;
          }
        }
      }
      return false;
    }

    public void commitAssertingNoCircularDependency() {
      try {
        commit();
      }
      catch (ModuleCircularDependencyException e) {
        LOG.error(e);
      }
    }

    public void commit() throws ModuleCircularDependencyException {
      ProjectRootManagerEx.getInstanceEx(myProject).multiCommit(this, new ModifiableRootModel[0]);
    }

    public void commitWithRunnable(Runnable runnable) {
      ModuleManagerImpl.this.commitModel(this, runnable);
      myIsWritable = false;
      clearRenamingStuff();
    }

    private void clearRenamingStuff() {
      myModulesToNewNamesMap.clear();
      myNewNamesToModulesMap.clear();
      myNewNamesToModulesMap.clear();
    }

    public void dispose() {
      assertWritable();
      ApplicationManager.getApplication().assertWriteAccessAllowed();
      final List<Module> list = Arrays.asList(ModuleManagerImpl.this.myModuleModel.getModules());
      final Module[] thisModules = getModules();
      for (int i = 0; i < thisModules.length; i++) {
        ModuleImpl thisModule = (ModuleImpl)thisModules[i];
        if (!list.contains(thisModule)) {
          thisModule.dispose();
        }
      }
      for (int i = 0; i < myModulesToDispose.size(); i++) {
        ModuleImpl module = (ModuleImpl)myModulesToDispose.get(i);
        if (!list.contains(module)) {
          module.dispose();
        }
      }
      clearRenamingStuff();
    }

    public boolean isChanged() {
      if (!myIsWritable) return false;
      Set<Module> thisModules = new HashSet<Module>(myPath2ModelMap.values());
      Set<Module> thatModules = new HashSet<Module>(ModuleManagerImpl.this.myModuleModel.myPath2ModelMap.values());
      return !thisModules.equals(thatModules);
    }

    private void disposeModel() {
      final Collection collection = myPath2ModelMap.values();
      for (Iterator iterator = collection.iterator(); iterator.hasNext();) {
        ModuleImpl module = (ModuleImpl)iterator.next();
        module.dispose();
      }
      myPath2ModelMap.clear();
    }

    public void projectOpened() {
      final Collection<Module> collection = myPath2ModelMap.values();
      for (Iterator iterator = collection.iterator(); iterator.hasNext();) {
        ModuleImpl module = (ModuleImpl)iterator.next();
        module.projectOpened();
      }
    }

    public void projectClosed() {
      final Collection<Module> collection = myPath2ModelMap.values();
      for (Iterator iterator = collection.iterator(); iterator.hasNext();) {
        ModuleImpl module = (ModuleImpl)iterator.next();
        module.projectClosed();
      }
    }

  }

  private void commitModel(ModuleModelImpl moduleModel, Runnable runnable) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    final Collection<Module> oldModules = myModuleModel.myPath2ModelMap.values();
    final Collection<Module> newModules = moduleModel.myPath2ModelMap.values();
    List<Module> removedModules = new ArrayList<Module>(oldModules);
    removedModules.removeAll(newModules);
    List<Module> addedModules = new ArrayList<Module>(newModules);
    addedModules.removeAll(oldModules);

    ProjectRootManagerEx.getInstanceEx(myProject).beforeRootsChange(false);

    try {
      for (int i = 0; i < removedModules.size(); i++) {
        ModuleImpl module = (ModuleImpl)removedModules.get(i);
        fireBeforeModuleRemoved(module);
        cleanCachedStuff();
      }

      List<Module> neverAddedModules = new ArrayList<Module>(moduleModel.myModulesToDispose);
      neverAddedModules.removeAll(myModuleModel.myPath2ModelMap.values());
      for (Iterator<Module> iterator = neverAddedModules.iterator(); iterator.hasNext();) {
        ModuleImpl module = (ModuleImpl)iterator.next();
        module.dispose();
      }

      myModuleModel = moduleModel;

      if (runnable != null) {
        runnable.run();
      }

      for (int i = 0; i < removedModules.size(); i++) {
        ModuleImpl module = (ModuleImpl)removedModules.get(i);
        fireModuleRemoved(module);
        cleanCachedStuff();
        module.dispose();
        cleanCachedStuff();
      }

      for (int i = 0; i < addedModules.size(); i++) {
        ModuleImpl module = (ModuleImpl)addedModules.get(i);
        module.moduleAdded();
        cleanCachedStuff();
        fireModuleAdded(module);
        cleanCachedStuff();
      }
      final Map<Module, String> modulesToNewNamesMap = moduleModel.myModulesToNewNamesMap;
      final Set<Module> modulesToBeRenamed = modulesToNewNamesMap.keySet();
      final List<Module> modules = new ArrayList<Module>();
      for (Iterator<Module> iterator = modulesToBeRenamed.iterator(); iterator.hasNext();) {
        ModuleImpl module = (ModuleImpl)iterator.next();
        modules.add(module);
        module.rename(modulesToNewNamesMap.get(module));
        cleanCachedStuff();
      }
      fireModulesRenamed(modules);
      cleanCachedStuff();
    }
    finally {
      ProjectRootManagerEx.getInstanceEx(myProject).rootsChanged(false);
    }
  }

  private void fireModulesRenamed(List<Module> modules) {
    if (modules.size() > 0) {
      myModuleEventDispatcher.getMulticaster().modulesRenamed(myProject, modules);
    }
  }

  void fireModuleRenamedByVfsEvent(Module module) {
    ProjectRootManagerEx.getInstanceEx(myProject).beforeRootsChange(false);
    try {
      fireModulesRenamed(Collections.singletonList(module));
    }
    finally {
      ProjectRootManagerEx.getInstanceEx(myProject).rootsChanged(false);
    }
  }

  public String getModuleGroup(Module module) {
    return myModuleGroup == null ? null : myModuleGroup.get(module);
  }

  public void setModuleGroup(Module module, String group) {
    if (myModuleGroup == null) {
      myModuleGroup = new HashMap<Module, String>();
    }
    if (group == null) {
      myModuleGroup.remove(module);
    }
    else {
      myModuleGroup.put(module, group);
    }
  }
}

