package com.intellij.openapi.project.impl;

import com.intellij.application.options.PathMacrosImpl;
import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.components.impl.ComponentManagerImpl;
import com.intellij.openapi.components.impl.ProjectPathMacroManager;
import com.intellij.openapi.components.impl.stores.IProjectStore;
import com.intellij.openapi.components.impl.stores.StoresFactory;
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
import org.picocontainer.MutablePicoContainer;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;


/**
 *
 */
public class ProjectImpl extends ComponentManagerImpl implements ProjectEx {
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
  private boolean myDefault;

  protected ProjectImpl(ProjectManagerImpl manager,
                        String filePath,
                        boolean isDefault,
                        boolean isOptimiseTestLoadSpeed,
                        PathMacrosImpl pathMacros) {
    super(ApplicationManager.getApplication());

    myDefault = isDefault;
    PathMacroManager.getInstance(this).setPathMacros(pathMacros);
    getStateStore().setProjectFilePath(filePath);

    myOptimiseTestLoadSpeed = isOptimiseTestLoadSpeed;

    myManager = manager;
  }

  protected void boostrapPicoContainer() {
    Extensions.instantiateArea(PluginManager.AREA_IDEA_PROJECT, this, null);
    super.boostrapPicoContainer();
    getPicoContainer().registerComponentImplementation(StoresFactory.getProjectStoreClass());
    getPicoContainer().registerComponentImplementation(ProjectPathMacroManager.class);
  }

  public IProjectStore getStateStore() {
    return (IProjectStore)super.getStateStore();
  }

  public boolean isSavePathsRelative() {
    return getStateStore().isSavePathsRelative();
  }

  public void setSavePathsRelative(boolean b) {
    getStateStore().setSavePathsRelative(b);
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

    if (realProject) {
      final Application app = ApplicationManager.getApplication();
      final IdeaPluginDescriptor[] plugins = app.getPlugins();
      for (IdeaPluginDescriptor plugin : plugins) {
        if (PluginManager.shouldSkipPlugin(plugin)) continue;
        loadComponentsConfiguration(plugin.getProjectComponents(), plugin, true);
      }
    }
  }

  @NotNull
  public String getProjectFilePath() {
    LOG.warn("DEPRECATED METHOD USAGE: ProjectImpl.getProjectFilePath()", new Throwable());
    return getStateStore().getProjectFilePath();
  }

  /**
   * @deprecated
   */
  @Nullable
  public VirtualFile getProjectFile() {
    LOG.warn("DEPRECATED METHOD USAGE: ProjectImpl.getProjectFile()", new Throwable());
    return getStateStore().getProjectFile();
  }

  @Nullable
  public VirtualFile getBaseDir() {
    return getStateStore().getProjectBaseDir();
  }

  @NotNull
  public String getName() {
    if (isDefault()) return TEMPLATE_PROJECT_NAME;
    if (isDummy()) return DUMMY_PROJECT_NAME;

    String temp = getStateStore().getProjectFileName();
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
  @NonNls
  public String getPresentableUrl() {
    final VirtualFile projectFile = getStateStore().getProjectFile();
    return projectFile != null ? projectFile.getPresentableUrl() : null;
  }

  @NotNull
  @NonNls
  public String getLocationHash() {
    String str = getPresentableUrl();
    if (str == null) str = getName();

    return getName() + Integer.toHexString(str.hashCode());
  }

  /**
   * @deprecated
   */
  @Nullable
  public VirtualFile getWorkspaceFile() {
    LOG.warn("DEPRECATED METHOD USAGE: ProjectImpl.getWorkspaceFile()", new Throwable());
    return getStateStore().getWorkspaceFile();
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

    getStateStore().saveProject();
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
    final ProjectComponent[] components = getComponents(ProjectComponent.class);
    for (ProjectComponent component : components) {
      try {
        component.projectOpened();
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }
  }
  

  private void projectClosed() {
    List<ProjectComponent> components = new ArrayList<ProjectComponent>(Arrays.asList(getComponents(ProjectComponent.class)));
    Collections.reverse(components);
    for (ProjectComponent component : components) {
      try {
        component.projectClosed();
      }
      catch (Throwable e) {
        LOG.error(e);
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

  protected MutablePicoContainer createPicoContainer() {
    return Extensions.getArea(this).getPicoContainer();
  }

  public boolean isDefault() {
    return myDefault;
  }
}
