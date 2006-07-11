package com.intellij.openapi.roots.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.ModuleListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

/**
 * @author dsl
 */
public class ModuleOrderEntryImpl extends OrderEntryBaseImpl implements ModuleOrderEntry, WritableOrderEntry, ClonableOrderEntry {
  @NonNls static final String ENTRY_TYPE = "module";
  private Module myModule;
  private String myModuleName; // non-null if myProject is null
  @NonNls private static final String MODULE_NAME_ATTR = "module-name";
  private final MyModuleListener myListener = new MyModuleListener();
  protected boolean myExported = false;
  @NonNls protected static final String EXPORTED_ATTR = "exported";

  ModuleOrderEntryImpl(Module module, RootModelImpl rootModel) {
    super(rootModel);
    myModule = module;
  }

  ModuleOrderEntryImpl(String moduleName, RootModelImpl rootModel) {
    super(rootModel);
    myModuleName = moduleName;
    myModule = null;
  }

  ModuleOrderEntryImpl(Element element, RootModelImpl rootModel) throws InvalidDataException {
    super(rootModel);
    myExported = element.getAttributeValue(EXPORTED_ATTR) != null;
    final String moduleName = element.getAttributeValue(MODULE_NAME_ATTR);
    if (moduleName == null) {
      throw new InvalidDataException();
    }

    myModule = null;
    myModuleName = moduleName;
    //addListeners(); // Q????
  }

  private ModuleOrderEntryImpl(ModuleOrderEntryImpl that, RootModelImpl rootModel) {
    super(rootModel);
    final Module thatModule = that.myModule;
    if (thatModule != null) {
      if (!thatModule.isDisposed()) {
        myModule = thatModule;
      } else { 
        myModule = null;
        myModuleName = thatModule.getName();
      }
    }
    else {
      myModuleName = that.myModuleName;
    }
    myExported = that.myExported;
    addListeners();
  }

  private boolean myListenersAdded = false;

  private void addListeners() {
    myListenersAdded = true;
    ModuleManager.getInstance(myRootModel.getModule().getProject()).addModuleListener(myListener);
  }

  public Module getOwnerModule() {
    return myRootModel.getModule();
  }

  @NotNull
  public VirtualFile[] getFiles(OrderRootType type) {
    return getFiles(type, new HashSet<Module>());
  }

  @NotNull
  VirtualFile[] getFiles(OrderRootType type, Set<Module> processed) {
    if (myModule != null && !processed.contains(myModule)) {
      processed.add(myModule);
      return ((ModuleRootManagerImpl)ModuleRootManager.getInstance(myModule)).getFilesForOtherModules(type, processed);
    }
    else {
      return VirtualFile.EMPTY_ARRAY;
    }
  }

  @NotNull
  public String[] getUrls(OrderRootType rootType) {
    return getUrls(rootType, new HashSet<Module>());
  }

  public String[] getUrls (OrderRootType rootType, Set<Module> processed) {
    if (myModule != null && !processed.contains(myModule)) {
      processed.add(myModule);
      return ((ModuleRootManagerImpl)ModuleRootManager.getInstance(myModule)).getUrlsForOtherModules(rootType, processed);
    }
    else {
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }
  }


  public boolean isValid() {
    return myModule != null;
  }

  public <R> R accept(RootPolicy<R> policy, R initialValue) {
    return policy.visitModuleOrderEntry(this, initialValue);
  }

  public String getPresentableName() {
    if (myModule != null) {
      return myModule.getName();
    }
    else {
      return myModuleName;
    }
  }

  public boolean isSynthetic() {
    return false;
  }

  public Module getModule() {
    return myModule;
  }

  public void writeExternal(Element rootElement) throws WriteExternalException {
    final Element element = OrderEntryFactory.createOrderEntryElement(ENTRY_TYPE);
    element.setAttribute(MODULE_NAME_ATTR, getModuleName());
    if (myExported) {
      element.setAttribute(EXPORTED_ATTR, "");
    }
    rootElement.addContent(element);
  }

  public String getModuleName() {
    if (myModule != null) {
      return myModule.getName();
    }
    else {
      return myModuleName;
    }
  }

  public OrderEntry cloneEntry(RootModelImpl rootModel,
                               ProjectRootManagerImpl projectRootManager,
                               VirtualFilePointerManager filePointerManager) {
    return new ModuleOrderEntryImpl(this, rootModel);
  }

  protected void dispose() {
    super.dispose();
    if (myListenersAdded) {
      ModuleManager.getInstance(myRootModel.getModule().getProject()).removeModuleListener(myListener);
    }
  }

  private void moduleAdded(Module module) {
    if (Comparing.equal(myModuleName, module.getName())) {
      setModule(module);
    }
  }

  private void setModule(Module module) {
    myModule = module;
    myModuleName = null;
  }

  private void moduleRemoved(Module module) {
    if (myModule == module) {
      unsetModule(module);
    }
  }

  private void unsetModule(Module module) {
    myModuleName = module.getName();
    myModule = null;
  }

  public boolean isExported() {
    return myExported;
  }

  public void setExported(boolean value) {
    myRootModel.assertWritable();
    myExported = value;
  }

  private class MyModuleListener implements ModuleListener {

    public MyModuleListener() {
    }

    public void moduleAdded(Project project, Module module) {
      final ModuleOrderEntryImpl moduleOrderEntry = ModuleOrderEntryImpl.this;
      moduleOrderEntry.moduleAdded(module);
    }

    public void beforeModuleRemoved(Project project, Module module) {
    }

    public void moduleRemoved(Project project, Module module) {
      final ModuleOrderEntryImpl moduleOrderEntry = ModuleOrderEntryImpl.this;
      moduleOrderEntry.moduleRemoved(module);
    }

    public void modulesRenamed(Project project, List<Module> modules) {
      if (myModule != null) return;
      for (Module module : modules) {
        if (module.getName().equals(myModuleName)) {
          setModule(module);
          break;
        }
      }
    }
  }

  protected void projectOpened() {
    addListeners();
  }

  protected void moduleAdded() {
    super.moduleAdded();
    if (myModule == null) {
      final Module module = ModuleManager.getInstance(myRootModel.getModule().getProject()).findModuleByName(myModuleName);
      if (module != null) {
        setModule(module);
      }
    }
  }

  /*
  private static boolean circularDependency(Module module,
                                     final String thisModuleName, final ModuleManager moduleManager) {
    if (module == null) return false;
    final String[] dependencyNames = ModuleRootManager.getInstance(module).getDependencyModuleNames();
    for (int i = 0; i < dependencyNames.length; i++) {
      String dependencyName = dependencyNames[i];
      if (dependencyName.equals(thisModuleName)) return true;
      final Module depModule = moduleManager.findModuleByName(dependencyName);
      if (circularDependency(depModule, thisModuleName, moduleManager)) return true;
    }
    return false;
  }
  */
}
