package com.intellij.openapi.module.impl;

import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.impl.ComponentManagerImpl;
import com.intellij.openapi.components.impl.ModulePathMacroManager;
import com.intellij.openapi.components.impl.stores.IComponentStore;
import com.intellij.openapi.components.impl.stores.IModuleStore;
import com.intellij.openapi.components.impl.stores.StoresFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.AreaInstance;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleComponent;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.impl.scopes.ModuleRuntimeClasspathScope;
import com.intellij.openapi.module.impl.scopes.ModuleWithDependenciesScope;
import com.intellij.openapi.module.impl.scopes.ModuleWithDependentsScope;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.impl.ModuleRootManagerImpl;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.*;
import com.intellij.pom.PomModel;
import com.intellij.pom.PomModule;
import com.intellij.pom.core.impl.PomModuleImpl;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.MutablePicoContainer;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author max
 */
public class ModuleImpl extends ComponentManagerImpl implements Module {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.module.impl.ModuleImpl");

  @NotNull private final Project myProject;
  private ModuleType myModuleType = null;
  private MyVirtualFileListener myVirtualFileListener;
  private boolean isModuleAdded;

  private GlobalSearchScope myModuleScope = null;

  @NonNls private static final String MODULE_LAYER = "module-components";
  @NotNull private PomModule myPomModule;
  @NotNull private PomModel myPomModel;
  @NonNls private static final String OPTION_WORKSPACE = "workspace";
  @NonNls public static final String ELEMENT_TYPE = "type";

  private ModuleWithDependenciesScope myModuleWithLibrariesScope;
  private ModuleWithDependenciesScope myModuleWithDependenciesScope;
  private ModuleWithDependenciesScope myModuleWithDependenciesAndLibrariesScope;
  private ModuleWithDependenciesScope myModuleWithDependenciesAndLibrariesNoTestsScope;
  private ModuleWithDependentsScope   myModuleWithDependentsScope;
  private ModuleWithDependentsScope   myModuleTestsWithDependentsScope;
  private ModuleRuntimeClasspathScope myModuleTestsRuntimeClasspathScope;
  private ModuleRuntimeClasspathScope myModuleRuntimeClasspathScope;
  public static final Object MODULE_RENAMING_REQUESTOR = new Object();

  public ModuleImpl(String filePath, Project project, PomModel pomModel) {
    super(project);

    myProject = project;
    myPomModel =  pomModel;
    init(filePath);
  }

  protected void boostrapPicoContainer() {
    Extensions.instantiateArea(PluginManager.AREA_IDEA_MODULE, this, (AreaInstance)getParentComponentManager());
    super.boostrapPicoContainer();
    getPicoContainer().registerComponentImplementation(IComponentStore.class, StoresFactory.getModuleStoreClass());
    getPicoContainer().registerComponentImplementation(ModulePathMacroManager.class);
  }

  @NotNull
  public IModuleStore getStateStore() {
    return (IModuleStore)super.getStateStore();
  }

  private void init(String filePath) {
    myPomModule = new PomModuleImpl(myPomModel, this);

    getStateStore().setModuleFilePath(filePath);

    myVirtualFileListener = new MyVirtualFileListener();
    VirtualFileManager.getInstance().addVirtualFileListener(myVirtualFileListener);
  }

  public void loadModuleComponents() {
    loadComponentsConfiguration(MODULE_LAYER, true);

    final IdeaPluginDescriptor[] plugins = PluginManager.getPlugins();
    for (IdeaPluginDescriptor plugin : plugins) {
      if (PluginManager.shouldSkipPlugin(plugin)) continue;
      loadComponentsConfiguration(plugin.getModuleComponents(), plugin, true);
    }
  }

  protected boolean isComponentSuitable(Map<String,String> options) {
    if (!super.isComponentSuitable(options)) return false;
    if (options == null) return true;

    Set<String> optionNames = options.keySet();
    for (String optionName : optionNames) {
      if (Comparing.equal(OPTION_WORKSPACE, optionName)) continue;
      if (!parseOptionValue(options.get(optionName)).contains( getOptionValue(optionName))) return false;
    }

    return true;
  }

  private static List<String> parseOptionValue(String optionValue) {
    if (optionValue == null) return new ArrayList<String>(0);
    return Arrays.asList(optionValue.split(";"));
  }

  @Nullable
  public VirtualFile getModuleFile() {
    return getStateStore().getModuleFile();
  }

  void rename(String newName) {
    final VirtualFile file = getStateStore().getModuleFile();
    try {
      if (file != null) {
        file.rename(MODULE_RENAMING_REQUESTOR, newName + ModuleFileType.DOT_DEFAULT_EXTENSION);
        getStateStore().setModuleFilePath(VfsUtil.virtualToIoFile(file).getCanonicalPath());
        return;
      }

      // [dsl] we get here if either old file didn't exist or renaming failed
      final File oldFile = new File(getModuleFilePath());
      final File parentFile = oldFile.getParentFile();

      final File newFile = new File(parentFile, newName + ModuleFileType.DOT_DEFAULT_EXTENSION);
      getStateStore().setModuleFilePath(newFile.getCanonicalPath());
    }
    catch (IOException e) {
      LOG.debug(e);
    }
  }

  @NotNull
  public String getModuleFilePath() {
    return getStateStore().getModuleFilePath();
  }

  public synchronized void dispose() {
    isModuleAdded = false;
    disposeComponents();
    VirtualFileManager.getInstance().removeVirtualFileListener(myVirtualFileListener);
    Extensions.disposeArea(this);
    myPomModel = null;
    myPomModule = null;
    super.dispose();
  }


  public void projectOpened() {
    for (ModuleComponent component : getComponents(ModuleComponent.class)) {
      try {
        component.projectOpened();
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
  }

  public void projectClosed() {
    List<ModuleComponent> components = new ArrayList<ModuleComponent>(Arrays.asList(getComponents(ModuleComponent.class)));
    Collections.reverse(components);

    for (ModuleComponent component : components) {
      try {
        component.projectClosed();
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
  }

  @NotNull
  public ModuleType getModuleType() {
    LOG.assertTrue(myModuleType != null, "Module type not initialized yet");
    return myModuleType;
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  private final Map<String, String> myFileToModuleName = new HashMap<String, String>();

  @NotNull
  public String getName() {
    final String fileName = getStateStore().getModuleFileName();
    String moduleName = myFileToModuleName.get(fileName); // TODO[wtf]: What this map is for???
    if (moduleName == null) {
      moduleName = moduleNameByFileName(fileName);
      myFileToModuleName.put(fileName, moduleName);
    }
    return moduleName;
  }

  public boolean isSavePathsRelative() {
    return getStateStore().isSavePathsRelative();
  }

  public void moduleAdded() {
    isModuleAdded = true;
    for (ModuleComponent component : getComponents(ModuleComponent.class)) {
      component.moduleAdded();
    }
  }

  public void setModuleType(ModuleType type) {
    myModuleType = type;
    setOption(ELEMENT_TYPE, type.getId());
  }

  public void setOption(@NotNull String optionName, @NotNull String optionValue) {
    getStateStore().setOption(optionName, optionValue);
  }

  public void clearOption(@NotNull String optionName) {
    getStateStore().clearOption(optionName);
  }

  public String getOptionValue(@NotNull String optionName) {
    return getStateStore().getOptionValue(optionName);
  }

  @NotNull
  public PomModule getPom() {
    return myPomModule;
  }

  public GlobalSearchScope getModuleScope() {
    if (myModuleScope == null) {
      myModuleScope = new ModuleWithDependenciesScope(this, false, false, true);
    }

    return myModuleScope;
  }

  public GlobalSearchScope getModuleWithLibrariesScope() {
    if (myModuleWithLibrariesScope == null) {
      myModuleWithLibrariesScope = new ModuleWithDependenciesScope(this, true, false, true);
    }
    return myModuleWithLibrariesScope;
  }

  public GlobalSearchScope getModuleWithDependenciesScope() {
    if (myModuleWithDependenciesScope == null) {
      myModuleWithDependenciesScope = new ModuleWithDependenciesScope(this, false, true, true);
    }
    return myModuleWithDependenciesScope;
  }

  public GlobalSearchScope getModuleWithDependenciesAndLibrariesScope(boolean includeTests) {
    if (includeTests) {
      if (myModuleWithDependenciesAndLibrariesScope == null) {
        myModuleWithDependenciesAndLibrariesScope = new ModuleWithDependenciesScope(this, true, true, true);
      }
      return myModuleWithDependenciesAndLibrariesScope;
    }
    else {
      if (myModuleWithDependenciesAndLibrariesNoTestsScope == null) {
        myModuleWithDependenciesAndLibrariesNoTestsScope = new ModuleWithDependenciesScope(this, true, true, false);
      }
      return myModuleWithDependenciesAndLibrariesNoTestsScope;
    }
  }

  public GlobalSearchScope getModuleWithDependentsScope() {
    if (myModuleWithDependentsScope == null) {
      myModuleWithDependentsScope = new ModuleWithDependentsScope(this, false);
    }
    return myModuleWithDependentsScope;
  }

  public GlobalSearchScope getModuleTestsWithDependentsScope() {
    if (myModuleTestsWithDependentsScope == null) {
      myModuleTestsWithDependentsScope = new ModuleWithDependentsScope(this, true);
    }
    return myModuleTestsWithDependentsScope;
  }

  public GlobalSearchScope getModuleRuntimeScope(boolean includeTests) {
    if (includeTests) {
      if (myModuleTestsRuntimeClasspathScope == null) {
        myModuleTestsRuntimeClasspathScope = new ModuleRuntimeClasspathScope(this, true);
      }
      return myModuleTestsRuntimeClasspathScope;
    }
    else {
      if (myModuleRuntimeClasspathScope == null) {
        myModuleRuntimeClasspathScope = new ModuleRuntimeClasspathScope(this, false);
      }
      return myModuleRuntimeClasspathScope;
    }
  }

  @NotNull
  public LanguageLevel getEffectiveLanguageLevel() {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    LanguageLevel level = getLanguageLevel();
    if (level != null) return level;
    return PsiManager.getInstance(myProject).getEffectiveLanguageLevel();
  }
  
  @Nullable
  public LanguageLevel getLanguageLevel() {
    return ((ModuleRootManagerImpl)ModuleRootManager.getInstance(this)).getLanguageLevel();
  }

  public void clearScopesCache() {
    myModuleWithLibrariesScope = null;
    myModuleWithDependenciesScope = null;
    myModuleWithDependenciesAndLibrariesScope = null;
    myModuleWithDependenciesAndLibrariesNoTestsScope = null;
    myModuleWithDependentsScope = null;
    myModuleTestsWithDependentsScope = null;
    myModuleTestsRuntimeClasspathScope = null;
    myModuleRuntimeClasspathScope = null;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    return "Module:" + getName();
  }

  private static String moduleNameByFileName(String fileName) {
    if (fileName.endsWith(ModuleFileType.DOT_DEFAULT_EXTENSION)) {
      return fileName.substring(0, fileName.length() - ModuleFileType.DOT_DEFAULT_EXTENSION.length());
    }
    else {
      return fileName;
    }
  }

  public <T> T[] getExtensions(final ExtensionPointName<T> extensionPointName) {
    return Extensions.getArea(this).getExtensionPoint(extensionPointName).getExtensions();
  }

  private class MyVirtualFileListener extends VirtualFileAdapter {
    public void propertyChanged(VirtualFilePropertyEvent event) {
      if (!isModuleAdded) return;
      final Object requestor = event.getRequestor();
      if (MODULE_RENAMING_REQUESTOR.equals(requestor)) return;
      if (!VirtualFile.PROP_NAME.equals(event.getPropertyName())) return;
      final VirtualFile moduleFile = getModuleFile();
      if (moduleFile == null) return;
      if (moduleFile.equals(event.getFile())) {
        ModuleManagerImpl.getInstanceImpl(getProject()).fireModuleRenamedByVfsEvent(ModuleImpl.this);
      }
    }

  }

  protected MutablePicoContainer createPicoContainer() {
    return Extensions.getArea(this).getPicoContainer();
  }


  public void setSavePathsRelative(final boolean b) {
    getStateStore().setSavePathsRelative(b);
  }
}
