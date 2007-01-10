package com.intellij.openapi.project.impl;

import com.intellij.application.options.ExpandMacroToPathMap;
import com.intellij.application.options.PathMacrosImpl;
import com.intellij.application.options.ReplacePathToMacroMap;
import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.components.impl.ComponentManagerImpl;
import com.intellij.openapi.components.impl.stores.BaseFileConfigurable;
import com.intellij.openapi.components.impl.stores.IProjectStore;
import com.intellij.openapi.components.impl.stores.StoreFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.impl.ModuleManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.PomModel;
import com.intellij.psi.PsiBundle;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;


/**
 *
 */
public class ProjectImpl extends BaseFileConfigurable implements ProjectEx {
  private static final Logger LOG = Logger.getInstance("#com.intellij.project.impl.ProjectImpl");

  private ProjectManagerImpl myManager;

  private MyProjectManagerListener myProjectManagerListener;
  private boolean myDummy;

  private ArrayList<String> myConversionProblemsStorage = new ArrayList<String>();

  @NonNls private static final String PROJECT_LAYER = "project-components";
  private PomModel myModel = null;

  public boolean myOptimiseTestLoadSpeed;
  private GlobalSearchScope myAllScope;
  private GlobalSearchScope myProjectScope;
  @NonNls private static final String TEMPLATE_PROJECT_NAME = "Default (Template) Project";
  @NonNls private static final String DUMMY_PROJECT_NAME = "Dummy (Mock) Project";
  final IProjectStore myProjectStore;

  protected ProjectImpl(ProjectManagerImpl manager,
                        String filePath,
                        boolean isDefault,
                        boolean isOptimiseTestLoadSpeed,
                        PathMacrosImpl pathMacros) {
    super(StoreFactory.createProjectStore(), isDefault, pathMacros);

    myProjectStore = getStateStore();
    myProjectStore.setProject(this);
    myProjectStore.setProjectFilePath(filePath);

    myOptimiseTestLoadSpeed = isOptimiseTestLoadSpeed;

    Extensions.instantiateArea(PluginManager.AREA_IDEA_PROJECT, this, null);

    getPicoContainer().registerComponentInstance(Project.class, this);

    myManager = manager;
  }


  public IProjectStore getStateStore() {
    return (IProjectStore)super.getStateStore();
  }

  public boolean isSavePathsRelative() {
    return getStateStore().isSavePathsRelative();
  }

  public ReplacePathToMacroMap getMacroReplacements() {
    return getStateStore().getMacroReplacements();
  }

  public ExpandMacroToPathMap getExpandMacroReplacements() {
    return getStateStore().getExpandMacroReplacements();
  }

  public boolean isDummy() {
    return myDummy;
  }

  public boolean isOpen() {
    return ProjectManagerEx.getInstanceEx().isProjectOpened(this);
  }

  public boolean isInitialized() {
    return isOpen() && !isDisposed() && StartupManagerEx.getInstanceEx(this).startupActivityPassed();
  }

  @NotNull
  public PomModel getModel() {
    final PomModel pomModel = myModel != null ? myModel : (myModel = getComponent(PomModel.class));
    assert pomModel != null;

    return pomModel;
  }

  public GlobalSearchScope getAllScope() {
    if (myAllScope == null) {
      final ProjectRootManager projectRootManager = getComponent(ProjectRootManager.class);
      myAllScope = new GlobalSearchScope() {
        final ProjectFileIndex myProjectFileIndex = projectRootManager.getFileIndex();

        public boolean contains(VirtualFile file) {
          return true;
        }

        public int compare(VirtualFile file1, VirtualFile file2) {
          List<OrderEntry> entries1 = myProjectFileIndex.getOrderEntriesForFile(file1);
          List<OrderEntry> entries2 = myProjectFileIndex.getOrderEntriesForFile(file2);
          if (entries1.size() != entries2.size()) return 0;

          int res = 0;
          for (OrderEntry entry1 : entries1) {
            Module module = entry1.getOwnerModule();
            ModuleFileIndex moduleFileIndex = ModuleRootManager.getInstance(module).getFileIndex();
            OrderEntry entry2 = moduleFileIndex.getOrderEntryForFile(file2);
            if (entry2 == null) {
              return 0;
            }
            else {
              int aRes = entry2.compareTo(entry1);
              if (aRes == 0) return 0;
              if (res == 0) {
                res = aRes;
              }
              else if (res != aRes) {
                return 0;
              }
            }
          }

          return res;
        }

        public boolean isSearchInModuleContent(Module aModule) {
          return true;
        }

        public boolean isSearchInLibraries() {
          return true;
        }

        public String getDisplayName() {
          return PsiBundle.message("psi.search.scope.project.and.libraries");
        }

        public String toString() {
          return getDisplayName();
        }
      };
    }
    return myAllScope;
  }

  public GlobalSearchScope getProjectScope() {
    if (myProjectScope == null) {
      final ProjectRootManager projectRootManager = getComponent(ProjectRootManager.class);
      myProjectScope = new GlobalSearchScope() {
        private final ProjectFileIndex myFileIndex = projectRootManager.getFileIndex();

        public boolean contains(VirtualFile file) {
          return myFileIndex.isInContent(file);
        }

        public int compare(VirtualFile file1, VirtualFile file2) {
          return 0;
        }

        public boolean isSearchInModuleContent(Module aModule) {
          return true;
        }

        public boolean isSearchInLibraries() {
          return false;
        }

        public String getDisplayName() {
          return PsiBundle.message("psi.search.scope.project");
        }

        public String toString() {
          return getDisplayName();
        }
      };
    }
    return myProjectScope;
  }

  public void setDummy(boolean isDummy) {
    myDummy = isDummy;
  }

  public ArrayList<String> getConversionProblemsStorage() {
    return myConversionProblemsStorage;
  }

  public void loadProjectComponents() {
    boolean realProject = !isDummy();
    loadComponentsConfiguration(PROJECT_LAYER, realProject);

    if (realProject && PluginManager.shouldLoadPlugins()) {
      final Application app = ApplicationManager.getApplication();
      final IdeaPluginDescriptor[] plugins = app.getPlugins();
      for (IdeaPluginDescriptor plugin : plugins) {
        if (!PluginManager.shouldLoadPlugin(plugin)) continue;
        loadComponentsConfiguration(plugin.getProjectComponents(), plugin, true);
      }
    }
  }

  @NotNull
  public String getProjectFilePath() {
    return myProjectStore.getProjectFilePath();
  }

  @Nullable
  public VirtualFile getProjectFile() {
    return myProjectStore.getProjectFile();
  }

  @NotNull
  public String getName() {
    if (isDefault()) return TEMPLATE_PROJECT_NAME;
    if (isDummy()) return DUMMY_PROJECT_NAME;

    String temp = myProjectStore.getProjectFileName();
    if (temp.endsWith(ProjectFileType.DOT_DEFAULT_EXTENSION)) {
      temp = temp.substring(0, temp.length() - ProjectFileType.DOT_DEFAULT_EXTENSION.length());
    }
    final int i = temp.lastIndexOf(File.separatorChar);
    if (i >= 0) {
      temp = temp.substring(i + 1, temp.length() - i + 1);
    }
    return temp;
  }

  @Nullable
  public VirtualFile getWorkspaceFile() {
    return myProjectStore.getWorkspaceFile();
  }

  protected ComponentManagerImpl getParentComponentManager() {
    return (ComponentManagerImpl)ApplicationManager.getApplication();
  }

  public boolean isOptimiseTestLoadSpeed() {
    return myOptimiseTestLoadSpeed;
  }

  public void setOptimiseTestLoadSpeed(final boolean optimiseTestLoadSpeed) {
    myOptimiseTestLoadSpeed = optimiseTestLoadSpeed;
  }


  public void init() {
    super.init();

    ((ModuleManagerImpl)ModuleManager.getInstance(this)).loadModules();
    myProjectManagerListener = new MyProjectManagerListener();
    myManager.addProjectManagerListener(this, myProjectManagerListener);
  }

  public void save() {
    if (ApplicationManagerEx.getApplicationEx().isDoNotSave()) return; //no need to save
    ShutDownTracker.getInstance().registerStopperThread(Thread.currentThread());

    myProjectStore.saveProject();
  }

  public synchronized void dispose() {
    LOG.assertTrue(!isDisposed());
    if (myProjectManagerListener != null) {
      myManager.removeProjectManagerListener(this, myProjectManagerListener);
    }

    disposeComponents();
    Extensions.disposeArea(this);
    myManager = null;
    myModel = null;
    myProjectManagerListener = null;
    super.dispose();
  }

  private void projectOpened() {
    final Object[] components = getComponents(false);
    for (Object component : components) {
      if (component instanceof ProjectComponent) {
        try {
          ProjectComponent projectComponent = (ProjectComponent)component;
          projectComponent.projectOpened();
        }
        catch (Throwable e) {
          LOG.error(e);
        }
      }
    }
  }

  private void projectClosed() {
    final Object[] components = getComponents(false);
    for (int i = components.length - 1; i >= 0; i--) {
      if (components[i] instanceof ProjectComponent) {
        try {
          ProjectComponent component = (ProjectComponent)components[i];
          component.projectClosed();
        }
        catch (Throwable e) {
          LOG.error(e);
        }
      }
    }
  }

  private class MyProjectManagerListener implements ProjectManagerListener {
    public void projectOpened(Project project) {
      LOG.assertTrue(project == ProjectImpl.this);
      ProjectImpl.this.projectOpened();
    }

    public void projectClosed(Project project) {
      LOG.assertTrue(project == ProjectImpl.this);
      ProjectImpl.this.projectClosed();
    }

    public boolean canCloseProject(Project project) {
      return true;
    }

    public void projectClosing(Project project) {
    }
  }
}
