package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.ProjectJdk;
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
import com.intellij.openapi.ui.MasterDetailsComponent;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.util.Alarm;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;

public class StructureConfigrableContext implements Disposable {

  @NonNls public static final String DELETED_LIBRARIES = "lib";
  public static final String NO_JDK = ProjectBundle.message("project.roots.module.jdk.problem.message");

  public final Map<Library, Set<String>> myLibraryDependencyCache = new HashMap<Library, Set<String>>();
  public final Map<ProjectJdk, Set<String>> myJdkDependencyCache = new HashMap<ProjectJdk, Set<String>>();
  public final Map<Module, Map<String, Set<String>>> myValidityCache = new HashMap<Module, Map<String, Set<String>>>();
  public final Map<Library, Boolean> myLibraryPathValidityCache = new HashMap<Library, Boolean>(); //can be invalidated on startup only
  public final Map<Module, Set<String>> myModulesDependencyCache = new HashMap<Module, Set<String>>();

  private ModuleManager myModuleManager;
  public final ModulesConfigurator myModulesConfigurator;
  private boolean myDisposed;

  public final Alarm myUpdateDependenciesAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);
  public final Alarm myReloadProjectAlarm = new Alarm();


  public final Map<String, LibrariesModifiableModel> myLevel2Providers = new THashMap<String, LibrariesModifiableModel>();
  private Project myProject;


  public StructureConfigrableContext(Project project, final ModuleManager moduleManager, final ModulesConfigurator modulesConfigurator) {
    myProject = project;
    myModuleManager = moduleManager;
    myModulesConfigurator = modulesConfigurator;
  }

  @Nullable
  public Set<String> getCachedDependencies(final Object selectedObject, final MasterDetailsComponent.MyNode selectedNode, boolean force) {
    if (selectedObject instanceof Library){
      final Library library = (Library)selectedObject;
      if (myLibraryDependencyCache.containsKey(library)){
        return myLibraryDependencyCache.get(library);
      }
    } else if (selectedObject instanceof ProjectJdk){
      final ProjectJdk projectJdk = (ProjectJdk)selectedObject;
      if (myJdkDependencyCache.containsKey(projectJdk)){
        return myJdkDependencyCache.get(projectJdk);
      }
    } else if (selectedObject instanceof Module) {
      final Module module = (Module)selectedObject;
      if (myModulesDependencyCache.containsKey(module)) {
        return myModulesDependencyCache.get(module);
      }
    }
    final Computable<Set<String>> dependencies = new Computable<Set<String>>(){
      @Nullable
      public Set<String> compute() {
        final Set<String> dependencies = getDependencies(selectedObject, selectedNode);
        if (selectedObject instanceof Library){
          myLibraryDependencyCache.put((Library)selectedObject, dependencies);
        } else if (selectedObject instanceof ProjectJdk){
          final ProjectJdk projectJdk = (ProjectJdk)selectedObject;
          myJdkDependencyCache.put(projectJdk, dependencies);
        } else if (selectedObject instanceof Module){
          myModulesDependencyCache.put((Module)selectedObject, dependencies);
        }
        return dependencies;
      }
    };
    if (force){
      return dependencies.compute();
    } else {
      myUpdateDependenciesAlarm.addRequest(new Runnable(){
        public void run() {
          final Set<String> dep = dependencies.compute();
          SwingUtilities.invokeLater(new Runnable(){
            public void run() {
              if (dep != null && dep.isEmpty() && !myDisposed){
                fireOnCacheChanged();
              }
            }
          });
        }
      }, 100);
      return null;
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
  private Set<String> getDependencies(final Object selectedObject, final MasterDetailsComponent.MyNode node) {
    if (selectedObject instanceof Module) {
      return getDependencies(new Condition<OrderEntry>() {
        public boolean value(final OrderEntry orderEntry) {
          return orderEntry instanceof ModuleOrderEntry && Comparing.equal(((ModuleOrderEntry)orderEntry).getModule(), selectedObject);
        }
      });
    }
    else if (selectedObject instanceof Library) {
      if (((Library)selectedObject).getTable() == null) { //module library navigation
        final Set<String> set = new HashSet<String>();
        set.add(((MasterDetailsComponent.MyNode)node.getParent()).getDisplayName());
        return set;
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
    else if (selectedObject instanceof ProjectJdk) {
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

  public void clearCaches(final Module module, final List<Library> chosen) {
    for (Library library : chosen) {
      myLibraryDependencyCache.remove(library);
    }
    myValidityCache.remove(module);
    fireOnCacheChanged();
  }

  public void clearCaches(final Module module, final ProjectJdk oldJdk, final ProjectJdk selectedModuleJdk) {
    myJdkDependencyCache.remove(oldJdk);
    myJdkDependencyCache.remove(selectedModuleJdk);
    myValidityCache.remove(module);
    fireOnCacheChanged();
  }

  public void clearCaches(final OrderEntry entry) {
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
     boolean valid = library.allPathsValid(OrderRootType.CLASSES) && library.allPathsValid(OrderRootType.JAVADOC) && library.allPathsValid(OrderRootType.SOURCES);
     myLibraryPathValidityCache.put(library, valid ? Boolean.FALSE : Boolean.TRUE);
     if (valid) return;
     SwingUtilities.invokeLater(new Runnable(){
       public void run() {
         if (!myDisposed){
           fireOnCacheChanged();
         }
       }
     });
   }

   private void updateModuleValidityCache(final Module module) {
     if (myValidityCache.containsKey(module)) return; //do not check twice
     final OrderEntry[] entries = myModulesConfigurator.getRootModel(module).getOrderEntries();
     Map<String, Set<String>> problems = null;
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
     myValidityCache.put(module, problems);
     if (problems != null) {
       SwingUtilities.invokeLater(new Runnable(){
         public void run() {
           if (!myDisposed){
             fireOnCacheChanged();
           }
         }
       });
     }
   }

   public boolean isUnused(final Object object, MasterDetailsComponent.MyNode node) {
     if (object == null) return false;
     if (object instanceof Module){
       getCachedDependencies(object, node, false);
       return false;
     }
     if (object instanceof ProjectJdk) {
       return false;
     }
     if (object instanceof Library) {
       final LibraryTable libraryTable = ((Library)object).getTable();
       if (libraryTable == null || libraryTable.getTableLevel() != LibraryTablesRegistrar.PROJECT_LEVEL) {
         return false;
       }
     }
     final Set<String> dependencies = getCachedDependencies(object, node, false);
     return dependencies != null && dependencies.isEmpty();
   }

  private void fireOnCacheChanged() {
    //myTree.repaint();
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
    resetLibraries();
    myModulesConfigurator.resetModuleEditors();
  }
}
