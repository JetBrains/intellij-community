package com.intellij.openapi.roots.ui.configuration;

import com.intellij.compiler.ModuleCompilerUtil;
import com.intellij.facet.Facet;
import com.intellij.facet.impl.ProjectFacetsConfigurator;
import com.intellij.facet.impl.ui.ConfigureFacetsStep;
import com.intellij.ide.util.projectWizard.AddModuleWizard;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.impl.ProjectRootManagerImpl;
import com.intellij.openapi.roots.ui.configuration.actions.ModuleDeleteProvider;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ModuleStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectJdksModel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigrableContext;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.util.Chunk;
import com.intellij.util.NotNullFunction;
import com.intellij.util.graph.CachingSemiGraph;
import com.intellij.util.graph.GraphGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 15, 2003
 */
public class ModulesConfigurator implements ModulesProvider, ModuleEditor.ChangeListener {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.ui.configuration.ModulesConfigurator");
  private final Project myProject;
  //private final ModuleStructureConfigurable myProjectRootConfigurable;

  private boolean myModified = false;

  private ProjectConfigurable myProjectConfigurable;

  private final List<ModuleEditor> myModuleEditors = new ArrayList<ModuleEditor>();

  private final Comparator<ModuleEditor> myModuleEditorComparator = new Comparator<ModuleEditor>() {
    final ModulesAlphaComparator myModulesComparator = new ModulesAlphaComparator();

    public int compare(ModuleEditor editor1, ModuleEditor editor2) {
      return myModulesComparator.compare(editor1.getModule(), editor2.getModule());
    }

    @SuppressWarnings({"EqualsWhichDoesntCheckParameterClass"})
    public boolean equals(Object o) {
      return false;
    }
  };
  private ModifiableModuleModel myModuleModel;
  private ProjectFacetsConfigurator myFacetsConfigurator;

  private StructureConfigrableContext myContext;

  public ModulesConfigurator(Project project, ProjectJdksModel projectJdksModel) {
    myProject = project;
    myModuleModel = ModuleManager.getInstance(myProject).getModifiableModel();
    myProjectConfigurable = new ProjectConfigurable(project, this, projectJdksModel);
  }

  public void setContext(final StructureConfigrableContext context) {
    myContext = context;
    myFacetsConfigurator = createFacetsConfigurator();
  }

  public ProjectFacetsConfigurator getFacetsConfigurator() {
    return myFacetsConfigurator;
  }

  public void disposeUIResources() {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        for (final ModuleEditor moduleEditor : myModuleEditors) {
          final ModifiableRootModel model = moduleEditor.dispose();
          if (model != null) {
            model.dispose();
          }
        }
        myModuleEditors.clear();

        myModuleModel.dispose();

        myFacetsConfigurator.disposeEditors();
      }
    });

  }

  public ProjectConfigurable getModulesConfigurable() {
    return myProjectConfigurable;
  }

  public Module[] getModules() {
    return myModuleModel.getModules();
  }

  @Nullable
  public Module getModule(String name) {
    final Module moduleByName = myModuleModel.findModuleByName(name);
    if (moduleByName != null) {
      return moduleByName;
    }
    return myModuleModel.getModuleToBeRenamed(name); //if module was renamed
  }

  @Nullable
  public ModuleEditor getModuleEditor(Module module) {
    for (final ModuleEditor moduleEditor : myModuleEditors) {
      if (module.equals(moduleEditor.getModule())) {
        return moduleEditor;
      }
    }
    return null;
  }

  public ModuleRootModel getRootModel(Module module) {
    final ModuleEditor editor = getModuleEditor(module);
    ModuleRootModel rootModel = null;
    if (editor != null) {
      rootModel = editor.getModifiableRootModel();
    }
    if (rootModel == null) {
      rootModel = ModuleRootManager.getInstance(module);
    }

    return rootModel;
  }


  public void resetModuleEditors() {
    myModuleModel = ModuleManager.getInstance(myProject).getModifiableModel();

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        for (final ModuleEditor moduleEditor : myModuleEditors) {
          moduleEditor.removeChangeListener(ModulesConfigurator.this);
        }
        myModuleEditors.clear();
        final Module[] modules = myModuleModel.getModules();
        if (modules.length > 0) {
          for (Module module : modules) {
            createModuleEditor(module, null, null);
          }
          Collections.sort(myModuleEditors, myModuleEditorComparator);
        }
      }
    });
    myFacetsConfigurator.resetEditors();
    myModified = false;
  }

  private void createModuleEditor(final Module module, ModuleBuilder moduleBuilder, final @Nullable ConfigureFacetsStep facetsStep) {
    final ModuleEditor moduleEditor = new ModuleEditor(myProject, this, module.getName(), moduleBuilder);
    if (facetsStep != null) {
      myFacetsConfigurator.registerEditors(module, facetsStep);
    }
    myModuleEditors.add(moduleEditor);
    moduleEditor.addChangeListener(this);
  }

  public void moduleStateChanged(final ModifiableRootModel moduleRootModel) {
    myProjectConfigurable.updateCircularDependencyWarning();
  }

  public GraphGenerator<ModifiableRootModel> createGraphGenerator() {
    final Map<Module, ModifiableRootModel> models = new HashMap<Module, ModifiableRootModel>();
    for (ModuleEditor moduleEditor : myModuleEditors) {
      models.put(moduleEditor.getModule(), moduleEditor.getModifiableRootModel());
    }
    return createGraphGenerator(models);
  }

  private static GraphGenerator<ModifiableRootModel> createGraphGenerator(final Map<Module, ModifiableRootModel> models) {
    return GraphGenerator.create(CachingSemiGraph.create(new GraphGenerator.SemiGraph<ModifiableRootModel>() {
      public Collection<ModifiableRootModel> getNodes() {
        return models.values();
      }

      public Iterator<ModifiableRootModel> getIn(final ModifiableRootModel model) {
        final Module[] modules = model.getModuleDependencies();
        final List<ModifiableRootModel> dependencies = new ArrayList<ModifiableRootModel>();
        for (Module module : modules) {
          dependencies.add(models.get(module));
        }
        return dependencies.iterator();
      }
    }));
  }

  public void apply() throws ConfigurationException {
    final ProjectRootManagerImpl projectRootManager = ProjectRootManagerImpl.getInstanceImpl(myProject);

    final ConfigurationException[] ex = new ConfigurationException[1];

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        try {
          final List<ModifiableRootModel> models = new ArrayList<ModifiableRootModel>(myModuleEditors.size());
          for (final ModuleEditor moduleEditor : myModuleEditors) {
            final ModifiableRootModel model = moduleEditor.applyAndDispose();
            if (model != null) {
              models.add(model);
            }
          }
          myFacetsConfigurator.applyEditors();

          final ModifiableRootModel[] rootModels = models.toArray(new ModifiableRootModel[models.size()]);
          projectRootManager.multiCommit(myModuleModel, rootModels);
          myFacetsConfigurator.commitFacets();

        }
        catch (ConfigurationException e) {
          ex[0] = e;
        }
        finally {
          myFacetsConfigurator.disposeEditors();
          myFacetsConfigurator = createFacetsConfigurator();
          myModuleModel = ModuleManager.getInstance(myProject).getModifiableModel();
          for (Module module : myModuleModel.getModules()) {
            if (!module.isDisposed()) {
              final ModuleEditor moduleEditor = getModuleEditor(module);
              if (moduleEditor != null) {
                final ModuleBuilder builder = moduleEditor.getModuleBuilder();
                if (builder != null) {
                  builder.addSupport(module);
                }
              }
            }
          }
        }
      }
    });

    if (ex[0] != null) {
      throw ex[0];
    }

    ApplicationManager.getApplication().saveAll();

    myModified = false;
  }

  private ProjectFacetsConfigurator createFacetsConfigurator() {
    return new ProjectFacetsConfigurator(myContext, new NotNullFunction<Module, ModuleConfigurationState>() {
      @NotNull
      public ModuleConfigurationState fun(final Module module) {
        return getModuleEditor(module).createModuleConfigurationState();
      }
    });
  }

  public void setModified(final boolean modified) {
    myModified = modified;
  }

  public ModifiableModuleModel getModuleModel() {
    return myModuleModel;
  }

  public boolean deleteModule(final Module module) {
    return doRemoveModule(getModuleEditor(module));
  }


  @Nullable
  public Module addModule(Component parent) {
    if (myProject.isDefault()) return null;
    final Pair<ModuleBuilder, ConfigureFacetsStep> pair = runModuleWizard(parent);
    final ModuleBuilder builder = pair.getFirst();
    if (builder != null) {
      final Module module = createModule(builder);
      if (module != null) {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            createModuleEditor(module, builder, pair.getSecond());
          }
        });
      }
      return module;
    }
    return null;
  }

  private Module createModule(final ModuleBuilder builder) {
    final Exception[] ex = new Exception[]{null};
    final Module module = ApplicationManager.getApplication().runWriteAction(new Computable<Module>() {
      @SuppressWarnings({"ConstantConditions"})
      public Module compute() {
        try {
          return builder.createModule(myModuleModel);
        }
        catch (Exception e) {
          ex[0] = e;
          return null;
        }
      }
    });
    if (ex[0] != null) {
      Messages.showErrorDialog(ProjectBundle.message("module.add.error.message", ex[0].getMessage()),
                               ProjectBundle.message("module.add.error.title"));
    }
    return module;
  }

  void addModule(final ModuleBuilder moduleBuilder, final ConfigureFacetsStep facetsStep) {
    final Module module = createModule(moduleBuilder);
    if (module != null) {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          createModuleEditor(module, moduleBuilder, facetsStep);
          Collections.sort(myModuleEditors, myModuleEditorComparator);
        }
      });
      processModuleCountChanged(myModuleEditors.size() - 1, myModuleEditors.size());
    }
  }

  Pair<ModuleBuilder, ConfigureFacetsStep> runModuleWizard(Component dialogParent) {
    AddModuleWizard wizard = new AddModuleWizard(dialogParent, myProject, this);
    wizard.show();
    final ConfigureFacetsStep facetEditorsStep = wizard.getFacetEditorsStep();
    if (wizard.isOK()) {
      return Pair.create(wizard.getModuleBuilder(), facetEditorsStep);
    }

    return Pair.create(null, null);
  }


  private boolean doRemoveModule(ModuleEditor selectedEditor) {

    String question;
    if (myModuleEditors.size() == 1) {
      question = ProjectBundle.message("module.remove.last.confirmation");
    }
    else {
      question = ProjectBundle.message("module.remove.confirmation", selectedEditor.getModule().getName());
    }
    int result =
      Messages.showYesNoDialog(myProject, question, ProjectBundle.message("module.remove.confirmation.title"), Messages.getQuestionIcon());
    if (result != 0) {
      return false;
    }
    // do remove
    myModuleEditors.remove(selectedEditor);

    // destroyProcess removed module
    final Module moduleToRemove = selectedEditor.getModule();
    // remove all dependencies on the module that is about to be removed
    List<ModifiableRootModel> modifiableRootModels = new ArrayList<ModifiableRootModel>();
    for (final ModuleEditor moduleEditor : myModuleEditors) {
      final ModifiableRootModel modifiableRootModel = moduleEditor.getModifiableRootModelProxy();
      modifiableRootModels.add(modifiableRootModel);
    }
    // destroyProcess editor
    final ModifiableRootModel model = selectedEditor.dispose();
    ModuleDeleteProvider.removeModule(moduleToRemove, model, modifiableRootModels, myModuleModel);
    processModuleCountChanged(myModuleEditors.size() + 1, myModuleEditors.size());
    return true;
  }


  private void processModuleCountChanged(int oldCount, int newCount) {
    for (ModuleEditor moduleEditor : myModuleEditors) {
      moduleEditor.moduleCountChanged(oldCount, newCount);
    }
  }

  public void processModuleCompilerOutputChanged(String baseUrl) {
    for (ModuleEditor moduleEditor : myModuleEditors) {
      moduleEditor.updateCompilerOutputPathChanged(baseUrl, moduleEditor.getName());
    }
  }

  public boolean isModified() {
    if (myModuleModel.isChanged()) {
      return true;
    }
    for (ModuleEditor moduleEditor : myModuleEditors) {
      if (moduleEditor.isModified()) {
        return true;
      }
    }
    return myModified || myFacetsConfigurator.isModified();
  }


  public static boolean showFacetSettingsDialog(@NotNull final Facet facet,
                                                final @Nullable String tabNameToSelect) {
    final Project project = facet.getModule().getProject();
    final ProjectStructureConfigurable config = ProjectStructureConfigurable.getInstance(project);
    return ShowSettingsUtil.getInstance().editConfigurable(project, config, new Runnable() {
      public void run() {
        final ModuleStructureConfigurable modulesConfig = config.getModulesConfig();
        config.select(facet.getModule().getName(), ManageFacetsEditor.DISPLAY_NAME).doWhenDone(new Runnable() {
          public void run() {
            modulesConfig.setStartModuleWizard(false);
            modulesConfig.selectFacetTab(facet, tabNameToSelect);
          }
        });
      }
    });
  }

  public static boolean showDialog(Project project,
                                   @Nullable final String moduleToSelect,
                                   final String tabNameToSelect,
                                   final boolean showModuleWizard) {
    final ProjectStructureConfigurable config = ProjectStructureConfigurable.getInstance(project);
    return ShowSettingsUtil.getInstance().editConfigurable(project, config, new Runnable() {
      public void run() {
        final ModuleStructureConfigurable modulesConfig = config.getModulesConfig();
        config.select(moduleToSelect, tabNameToSelect).doWhenDone(new Runnable() {
          public void run() {
            modulesConfig.setStartModuleWizard(showModuleWizard);
            SwingUtilities.invokeLater(new Runnable() {
              public void run() {
                modulesConfig.setStartModuleWizard(false);
              }
            });
          }
        });
      }
    });
  }

  public void moduleRenamed(final String oldName, final String name) {
    for (ModuleEditor moduleEditor : myModuleEditors) {
      if (Comparing.strEqual(moduleEditor.getName(), oldName)) {
        moduleEditor.setModuleName(name);
        moduleEditor.updateCompilerOutputPathChanged(ModuleStructureConfigurable.getInstance(myProject).getCompilerOutputUrl(), name);
        return;
      }
    }
  }

  /**
   * @return pair of modules which become circular after adding dependency, or null if all remains OK
   */
  @Nullable
  public static Pair<Module, Module> addingDependencyFormsCircularity(final Module currentModule, Module toDependOn) {
    assert currentModule != toDependOn;
    // whatsa lotsa of @&#^%$ codes-a!

    final Map<Module, ModifiableRootModel> models = new LinkedHashMap<Module, ModifiableRootModel>();
    Project project = currentModule.getProject();
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
      models.put(module, model);
    }
    ModifiableRootModel currentModel = models.get(currentModule);
    ModifiableRootModel toDependOnModel = models.get(toDependOn);
    Collection<Chunk<ModifiableRootModel>> nodesBefore = buildChunks(models);
    for (Chunk<ModifiableRootModel> chunk : nodesBefore) {
      if (chunk.containsNode(toDependOnModel) && chunk.containsNode(currentModel)) return null; // they circular already
    }

    try {
      currentModel.addModuleOrderEntry(toDependOn);
      Collection<Chunk<ModifiableRootModel>> nodesAfter = buildChunks(models);
      for (Chunk<ModifiableRootModel> chunk : nodesAfter) {
        if (chunk.containsNode(toDependOnModel) && chunk.containsNode(currentModel)) {
          Iterator<ModifiableRootModel> nodes = chunk.getNodes().iterator();
          return Pair.create(nodes.next().getModule(), nodes.next().getModule());
        }
      }
    }
    finally {
      for (ModifiableRootModel model : models.values()) {
        model.dispose();
      }
    }
    return null;
  }

  private static Collection<Chunk<ModifiableRootModel>> buildChunks(final Map<Module, ModifiableRootModel> models) {
    return ModuleCompilerUtil.toChunkGraph(createGraphGenerator(models)).getNodes();
  }
}
