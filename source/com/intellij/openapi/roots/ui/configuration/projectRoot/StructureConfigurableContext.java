package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.impl.libraries.LibraryImpl;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablePresentation;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.ui.configuration.LibraryTableModifiableModelProvider;
import com.intellij.openapi.roots.ui.configuration.ModuleEditor;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.util.Alarm;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

public class StructureConfigurableContext implements Disposable {
  private static final Logger LOG = Logger.getInstance("#" + StructureConfigurableContext.class.getName());

  @NonNls public static final String DELETED_LIBRARIES = "lib";
  public static final String NO_JDK = ProjectBundle.message("project.roots.module.jdk.problem.message");
  public static final String DUPLICATE_MODULE_NAME = ProjectBundle.message("project.roots.module.duplicate.name.message");

  public final Map<Library, Set<String>> myLibraryDependencyCache = new HashMap<Library, Set<String>>();
  public final Map<Sdk, Set<String>> myJdkDependencyCache = new HashMap<Sdk, Set<String>>();
  public final Map<Module, Map<String, Set<String>>> myValidityCache = new HashMap<Module, Map<String, Set<String>>>();
  public final Map<Library, Boolean> myLibraryPathValidityCache = new HashMap<Library, Boolean>(); //can be invalidated on startup only
  public final Map<Module, Set<String>> myModulesDependencyCache = new HashMap<Module, Set<String>>();

  private ModuleManager myModuleManager;
  public final ModulesConfigurator myModulesConfigurator;
  private boolean myDisposed;

  public final Alarm myUpdateDependenciesAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);
  public final Alarm myReloadProjectAlarm = new Alarm();

  private List<Runnable> myCacheUpdaters = new ArrayList<Runnable>();


  public final Map<String, LibrariesModifiableModel> myLevel2Providers = new THashMap<String, LibrariesModifiableModel>();
  private Project myProject;


  public StructureConfigurableContext(Project project, final ModuleManager moduleManager, final ModulesConfigurator modulesConfigurator) {
    myProject = project;
    myModuleManager = moduleManager;
    myModulesConfigurator = modulesConfigurator;
  }

  @Nullable
  public Set<String> getCachedDependencies(final Object selectedObject, boolean force) {
    if (selectedObject instanceof Library){
      final Library library = (Library)selectedObject;
      if (myLibraryDependencyCache.containsKey(library)){
        return myLibraryDependencyCache.get(library);
      }
    } else if (selectedObject instanceof Sdk){
      final Sdk projectJdk = (Sdk)selectedObject;
      if (myJdkDependencyCache.containsKey(projectJdk)){
        return myJdkDependencyCache.get(projectJdk);
      }
    } else if (selectedObject instanceof Module) {
      final Module module = (Module)selectedObject;
      if (myModulesDependencyCache.containsKey(module)) {
        return myModulesDependencyCache.get(module);
      }
    }
    if (force){
      LOG.assertTrue(ApplicationManager.getApplication().isDispatchThread());
      final Set<String> dep = getDependencies(selectedObject);
      updateCache(selectedObject, dep);
      return dep;
    } else {
      myUpdateDependenciesAlarm.addRequest(new Runnable(){
        public void run() {
          final Set<String> dep = getDependencies(selectedObject);
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              if (!myDisposed) {
                updateCache(selectedObject, dep);
                fireOnCacheChanged();
              }
            }
          });
        }
      }, 100);
      return null;
    }
  }

  private void updateCache(final Object selectedObject, final Set<String> dep) {
    if (selectedObject instanceof Library) {
      myLibraryDependencyCache.put((Library)selectedObject, dep);
    }
    else if (selectedObject instanceof Sdk) {
      myJdkDependencyCache.put((Sdk)selectedObject, dep);
    }
    else if (selectedObject instanceof Module) {
      myModulesDependencyCache.put((Module)selectedObject, dep);
    }
  }

  private Set<String> getDependencies(final Condition<OrderEntry> condition) {
    final Set<String> result = new TreeSet<String>();
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        final Module[] modules = myModulesConfigurator.getModules();
        for (final Module module : modules) {
          final ModuleEditor moduleEditor = myModulesConfigurator.getModuleEditor(module);
          if (moduleEditor != null) {
            final OrderEntry[] entries = moduleEditor.getModifiableRootModel().getOrderEntries();
            for (OrderEntry entry : entries) {
              if (myDisposed) return;
              if (condition.value(entry)) {
                result.add(module.getName());
                break;
              }
            }
          }
        }
      }
    });
    return result;
  }

  @Nullable
  private Set<String> getDependencies(final Object selectedObject) {
    if (selectedObject instanceof Module) {
      return getDependencies(new Condition<OrderEntry>() {
        public boolean value(final OrderEntry orderEntry) {
          return orderEntry instanceof ModuleOrderEntry && Comparing.equal(((ModuleOrderEntry)orderEntry).getModule(), selectedObject);
        }
      });
    }
    else if (selectedObject instanceof Library) {
      Library library = (Library)selectedObject;
      if (library.getTable() == null) { //module library navigation
        HashSet<String> deps = new HashSet<String>();
        Module module = ((LibraryImpl)library).getModule();
        if (module != null) {
          deps.add(module.getName());
        }
        return deps;
      }
      return getDependencies(new Condition<OrderEntry>() {
        @SuppressWarnings({"SimplifiableIfStatement"})
        public boolean value(final OrderEntry orderEntry) {
          if (orderEntry instanceof LibraryOrderEntry){
            final LibraryImpl library = (LibraryImpl)((LibraryOrderEntry)orderEntry).getLibrary();
            if (Comparing.equal(library, selectedObject)) return true;
            return library != null && Comparing.equal(library.getSource(), selectedObject);
          }
          return false;
        }
      });
    }
    else if (selectedObject instanceof Sdk) {
      return getDependencies(new Condition<OrderEntry>() {
        public boolean value(final OrderEntry orderEntry) {
          return orderEntry instanceof JdkOrderEntry && Comparing.equal(((JdkOrderEntry)orderEntry).getJdk(), selectedObject);
        }
      });
    }
    return null;
  }

  public void dispose() {
    myJdkDependencyCache.clear();
    myLibraryDependencyCache.clear();
    myValidityCache.clear();
    myLibraryPathValidityCache.clear();
    myModulesDependencyCache.clear();
    myCacheUpdaters.clear();
    myDisposed = true;
  }

  public void invalidateModules(final Set<String> modules) {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        if (modules != null) {
          for (String module : modules) {
            myValidityCache.remove(myModuleManager.findModuleByName(module));
          }
        }
      }
    });
  }
  public void invalidateModuleName(final Module module) {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        final Map<String, Set<String>> problems = myValidityCache.remove(module);
        if (problems != null) {
          fireOnCacheChanged();            
        }
      }
    });
  }

  public ModulesConfigurator getModulesConfigurator() {
    return myModulesConfigurator;
  }

  public void clearCaches(final Module module, final List<Library> chosen) {
    LOG.assertTrue(ApplicationManager.getApplication().isDispatchThread());
    for (Library library : chosen) {
      myLibraryDependencyCache.remove(library);
    }
    myValidityCache.remove(module);
    fireOnCacheChanged();
  }

  public void clearCaches(final Module module, final Sdk oldJdk, final Sdk selectedModuleJdk) {
    LOG.assertTrue(ApplicationManager.getApplication().isDispatchThread());
    myJdkDependencyCache.remove(oldJdk);
    myJdkDependencyCache.remove(selectedModuleJdk);
    myValidityCache.remove(module);
    fireOnCacheChanged();
  }

  public void clearCaches(final OrderEntry entry) {
    LOG.assertTrue(ApplicationManager.getApplication().isDispatchThread());
    if (entry instanceof ModuleOrderEntry) {
      final Module module = ((ModuleOrderEntry)entry).getModule();
      myValidityCache.remove(module);
      myModulesDependencyCache.remove(module);
    } else if (entry instanceof JdkOrderEntry) {
      invalidateModules(myJdkDependencyCache.remove(((JdkOrderEntry)entry).getJdk()));
    } else if (entry instanceof LibraryOrderEntry) {
      invalidateModules(myLibraryDependencyCache.remove(((LibraryOrderEntry)entry).getLibrary()));
    }
    fireOnCacheChanged();
  }

  public boolean isInvalid(final Object object) {
     if (object instanceof Module){
       final Module module = (Module)object;
       if (myValidityCache.containsKey(module)) return myValidityCache.get(module) != null;
       myUpdateDependenciesAlarm.addRequest(new Runnable(){
         public void run() {
           ApplicationManager.getApplication().runReadAction(new Runnable() {
             public void run() {
               updateModuleValidityCache(module);
             }
           });
         }
       }, 100);
     } else if (object instanceof LibraryEx) {
       final LibraryEx library = (LibraryEx)object;
       if (myLibraryPathValidityCache.containsKey(library)) return myLibraryPathValidityCache.get(library).booleanValue();
       myUpdateDependenciesAlarm.addRequest(new Runnable(){
         public void run() {
           ApplicationManager.getApplication().runReadAction(new Runnable() {
             public void run() {
               updateLibraryValidityCache(library);
             }
           });
         }
       }, 100);
     }
     return false;
   }

   private void updateLibraryValidityCache(final LibraryEx library) {
     if (myLibraryPathValidityCache.containsKey(library)) return; //do not check twice
     final boolean valid = library.allPathsValid(OrderRootType.CLASSES) && library.allPathsValid(JavadocOrderRootType.getInstance()) && library.allPathsValid(OrderRootType.SOURCES);
     SwingUtilities.invokeLater(new Runnable(){
       public void run() {
         if (!myDisposed){
           myLibraryPathValidityCache.put(library, valid ? Boolean.FALSE : Boolean.TRUE);
           fireOnCacheChanged();
         }
       }
     });
   }

   private void updateModuleValidityCache(final Module module) {
     if (myValidityCache.containsKey(module)) return; //do not check twice

     if (myDisposed) return;

     Map<String, Set<String>> problems = null;
     final ModifiableModuleModel moduleModel = myModulesConfigurator.getModuleModel();
     final Module[] all = moduleModel.getModules();
     for (Module each : all) {
       if (each != module && getRealName(each).equals(getRealName(module))) {
         problems = new HashMap<String, Set<String>>();
         problems.put(DUPLICATE_MODULE_NAME, null);
         break;
       }
     }

     final OrderEntry[] entries = myModulesConfigurator.getRootModel(module).getOrderEntries();
     for (OrderEntry entry : entries) {
       if (myDisposed) return;
       if (!entry.isValid()){
         if (problems == null) {
           problems = new HashMap<String, Set<String>>();
         }
         if (entry instanceof JdkOrderEntry && ((JdkOrderEntry)entry).getJdkName() == null) {
           problems.put(NO_JDK, null);
         } else {
           Set<String> deletedLibraries = problems.get(DELETED_LIBRARIES);
           if (deletedLibraries == null){
             deletedLibraries = new HashSet<String>();
             problems.put(DELETED_LIBRARIES, deletedLibraries);
           }
           deletedLibraries.add(entry.getPresentableName());
         }
       }
     }
     final Map<String, Set<String>> finalProblems = problems;
     SwingUtilities.invokeLater(new Runnable() {
       public void run() {
         if (!myDisposed) {
           myValidityCache.put(module, finalProblems);
           fireOnCacheChanged();
         }
       }
     });
   }

  public Module[] getModules() {
    return myModulesConfigurator.getModules();
  }

  public String getRealName(final Module module) {
    final ModifiableModuleModel moduleModel = myModulesConfigurator.getModuleModel();
    String newName = moduleModel.getNewName(module);
    return newName != null ? newName : module.getName();
  }

  public boolean isUnused(final Object object) {
     if (object == null) return false;
     if (object instanceof Module){
       getCachedDependencies(object, false);
       return false;
     }
     if (object instanceof Sdk) {
       return false;
     }
     if (object instanceof Library) {
       final LibraryTable libraryTable = ((Library)object).getTable();
       if (libraryTable == null || !libraryTable.getTableLevel().equals(LibraryTablesRegistrar.PROJECT_LEVEL)) {
         return false;
       }
     }
     final Set<String> dependencies = getCachedDependencies(object, false);
     return dependencies != null && dependencies.isEmpty();
   }

  private void fireOnCacheChanged() {
    final Runnable[] all = myCacheUpdaters.toArray(new Runnable[myCacheUpdaters.size()]);
    for (Runnable each : all) {
      each.run();
    }
  }

  public void addReloadProjectRequest(final Runnable runnable) {
    myReloadProjectAlarm.cancelAllRequests();
    myReloadProjectAlarm.addRequest(runnable, 300, ModalityState.NON_MODAL);
  }


  public void resetLibraries() {
    final LibraryTablesRegistrar tablesRegistrar = LibraryTablesRegistrar.getInstance();

    myLevel2Providers.clear();
    myLevel2Providers.put(LibraryTablesRegistrar.APPLICATION_LEVEL, new LibrariesModifiableModel(tablesRegistrar.getLibraryTable()));
    myLevel2Providers.put(LibraryTablesRegistrar.PROJECT_LEVEL, new LibrariesModifiableModel(tablesRegistrar.getLibraryTable(myProject)));
    for (final LibraryTable table : tablesRegistrar.getCustomLibraryTables()) {
      myLevel2Providers.put(table.getTableLevel(), new LibrariesModifiableModel(table));
    }
  }

  public LibraryTableModifiableModelProvider getGlobalLibrariesProvider(final boolean tableEditable) {
    return createModifiableModelProvider(LibraryTablesRegistrar.APPLICATION_LEVEL, tableEditable);
  }

  public LibraryTableModifiableModelProvider createModifiableModelProvider(final String level, final boolean isTableEditable) {
    final LibraryTable table = LibraryTablesRegistrar.getInstance().getLibraryTableByLevel(level, myProject);
    return new LibraryTableModifiableModelProvider() {
        public LibraryTable.ModifiableModel getModifiableModel() {
          return myLevel2Providers.get(level);
        }

        public String getTableLevel() {
          return table.getTableLevel();
        }

        public LibraryTablePresentation getLibraryTablePresentation() {
          return table.getPresentation();
        }

        public boolean isLibraryTableEditable() {
          return isTableEditable && table.isEditable();
        }
      };
  }

  public LibraryTableModifiableModelProvider getProjectLibrariesProvider(final boolean tableEditable) {
    return createModifiableModelProvider(LibraryTablesRegistrar.PROJECT_LEVEL, tableEditable);
  }


  public List<LibraryTableModifiableModelProvider> getCustomLibrariesProviders(final boolean tableEditable) {
    return ContainerUtil.map2List(LibraryTablesRegistrar.getInstance().getCustomLibraryTables(), new NotNullFunction<LibraryTable, LibraryTableModifiableModelProvider>() {
      @NotNull
      public LibraryTableModifiableModelProvider fun(final LibraryTable libraryTable) {
        return createModifiableModelProvider(libraryTable.getTableLevel(), tableEditable);
      }
    });
  }


  @Nullable
  public Library getLibrary(final String libraryName, final String libraryLevel) {
/* the null check is added only to prevent NPE when called from getLibrary */
    if (myLevel2Providers.isEmpty()) resetLibraries();
    final LibrariesModifiableModel model = myLevel2Providers.get(libraryLevel);
    return model == null ? null : findLibraryModel(libraryName, model);
  }

  @Nullable
  private static Library findLibraryModel(final String libraryName, @NotNull LibrariesModifiableModel model) {
    final Library library = model.getLibraryByName(libraryName);
    return findLibraryModel(library, model);
  }

  @Nullable
  private static Library findLibraryModel(final Library library, LibrariesModifiableModel tableModel) {
    if (tableModel == null) return library;
    if (tableModel.wasLibraryRemoved(library)) return null;
    return tableModel.hasLibraryEditor(library) ? (Library)tableModel.getLibraryEditor(library).getModel() : library;
  }


  public void reset() {
    myDisposed = false;
    resetLibraries();
    myModulesConfigurator.resetModuleEditors();
  }

  public void addCacheUpdateListener(Runnable runnable) {
    myCacheUpdaters.add(runnable);
  }
}
