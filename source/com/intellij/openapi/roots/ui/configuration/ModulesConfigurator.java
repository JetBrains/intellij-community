package com.intellij.openapi.roots.ui.configuration;

import com.intellij.ide.util.projectWizard.AddModuleWizard;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.javaee.J2EEModuleUtil;
import com.intellij.javaee.module.J2EEModuleUtilEx;
import com.intellij.openapi.application.ApplicationManager;
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
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectRootConfigurable;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.util.graph.CachingSemiGraph;
import com.intellij.util.graph.GraphGenerator;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 15, 2003
 */
public class ModulesConfigurator implements ModulesProvider, ModuleEditor.ChangeListener {
  private final Project myProject;

  private boolean myModified = false;

  private ModulesConfigurable myModulesConfigurable;

  private List<ModuleEditor> myModuleEditors = new ArrayList<ModuleEditor>();

  private final Comparator<ModuleEditor> myModuleEditorComparator = new Comparator<ModuleEditor>() {
    final ModulesAlphaComparator myModulesComparator = new ModulesAlphaComparator();

    public int compare(ModuleEditor editor1, ModuleEditor editor2) {
      return myModulesComparator.compare(editor1.getModule(), editor2.getModule());
    }

    public boolean equals(Object o) {
      return false;
    }
  };
  private ModifiableModuleModel myModuleModel;

  public ModulesConfigurator(Project project, ProjectRootConfigurable configurable) {
    myProject = project;
    myModuleModel = ModuleManager.getInstance(myProject).getModifiableModel();
    myModulesConfigurable = new ModulesConfigurable(project, this, configurable.getProjectJdksModel());
  }

  public void disposeUIResources() {
    for (final ModuleEditor moduleEditor : myModuleEditors) {
      final ModifiableRootModel model = moduleEditor.dispose();
      if (model != null) {
        model.dispose();
      }
    }
    myModuleEditors.clear();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        myModuleModel.dispose();
      }
    });

  }

  public ModulesConfigurable getModulesConfigurable() {
    return myModulesConfigurable;
  }

  public Module[] getModules() {
    return myModuleModel.getModules();
  }

  public Module getModule(String name) {
    return myModuleModel.findModuleByName(name);
  }

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

    for (final ModuleEditor moduleEditor : myModuleEditors) {
      moduleEditor.removeChangeListener(this);
    }
    myModuleEditors.clear();
    final Module[] modules = myModuleModel.getModules();
    if (modules.length > 0) {
      for (Module module : modules) {
        createModuleEditor(module, null);
      }
      Collections.sort(myModuleEditors, myModuleEditorComparator);
    }
    myModified = false;
  }

  private ModuleEditor createModuleEditor(final Module module, ModuleBuilder moduleBuilder) {
    final ModuleEditor moduleEditor = new ModuleEditor(myProject, this, module.getName(), moduleBuilder);
    myModuleEditors.add(moduleEditor);
    moduleEditor.addChangeListener(this);
    return moduleEditor;
  }

  public void moduleStateChanged(final ModifiableRootModel moduleRootModel) {
    myModulesConfigurable.updateCircularDependencyWarning();
  }

  public GraphGenerator<ModifiableRootModel> createGraphGenerator() {
    final List<ModifiableRootModel> result = new ArrayList<ModifiableRootModel>();
    for (ModuleEditor moduleEditor : myModuleEditors) {
      result.add(moduleEditor.getModifiableRootModel());
    }
    return GraphGenerator.create(CachingSemiGraph.create(new GraphGenerator.SemiGraph<ModifiableRootModel>() {
      public Collection<ModifiableRootModel> getNodes() {
        return result;
      }

      public Iterator<ModifiableRootModel> getIn(final ModifiableRootModel model) {
        final Module[] modules = model.getModuleDependencies();
        final List<ModifiableRootModel> dependencies = new ArrayList<ModifiableRootModel>();
        for (Module module : modules) {
          dependencies.add(getModuleEditor(module).getModifiableRootModel());
        }
        return dependencies.iterator();
      }
    }));
  }

  public void apply() throws ConfigurationException {
    final ProjectRootManagerImpl projectRootManager = ProjectRootManagerImpl.getInstanceImpl(myProject);

    final List<ModifiableRootModel> models = new ArrayList<ModifiableRootModel>(myModuleEditors.size());
    for (final ModuleEditor moduleEditor : myModuleEditors) {
      final ModifiableRootModel model = moduleEditor.applyAndDispose();
      if (model != null) {
        models.add(model);
      }
    }

    J2EEModuleUtilEx.checkJ2EEModulesAcyclic(models);

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        try {
          final ModifiableRootModel[] rootModels = models.toArray(new ModifiableRootModel[models.size()]);
          projectRootManager.multiCommit(myModuleModel, rootModels);
        }
        finally {
          myModuleModel = ModuleManager.getInstance(myProject).getModifiableModel();

          final ArrayList<ModuleEditor> editors = new ArrayList<ModuleEditor>(myModuleEditors);
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              for (final ModuleEditor moduleEditor : editors) {
                final Module module = moduleEditor.getModule();
                final ModuleBuilder builder = moduleEditor.getModuleBuilder();
                if (builder != null) {
                  builder.addSupport(module);
                }
              }
            }
          });
        }
      }
    });

    if (!J2EEModuleUtilEx.checkDependentModulesOutputPathConsistency(myProject, J2EEModuleUtil.getAllJ2EEModules(myProject), true)) {
      throw new ConfigurationException(null);
    }

    ApplicationManager.getApplication().saveAll();

    myModified = false;
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


  public Module addModule(Component parent) {
    final ModuleBuilder builder = runModuleWizard(parent);
    if (builder != null) {
      final Module module = createModule(builder);
      if (module != null) {
        createModuleEditor(module, builder);
      }
      return module;
    }
    return null;
  }

  private Module createModule(final ModuleBuilder builder) {
    final Exception[] ex = new Exception[]{null};
    final Module module = ApplicationManager.getApplication().runWriteAction(new Computable<Module>() {
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

  Module addModule(final ModuleBuilder moduleBuilder) {
    final Module module = createModule(moduleBuilder);
    if (module != null) {
      createModuleEditor(module, moduleBuilder);
      Collections.sort(myModuleEditors, myModuleEditorComparator);
      processModuleCountChanged(myModuleEditors.size() - 1, myModuleEditors.size());
      return module;
    }
    return null;
  }

  ModuleBuilder runModuleWizard(Component dialogParent) {
    AddModuleWizard wizard = new AddModuleWizard(dialogParent, myProject, this);
    wizard.show();
    if (wizard.isOK()) {
      return wizard.getModuleBuilder();
    }
    return null;
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
      if (moduleToRemove.equals(moduleEditor.getModule())) {
        continue; // skip self
      }
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
    //updateTitle();
    for (ModuleEditor moduleEditor : myModuleEditors) {
      moduleEditor.moduleCountChanged(oldCount, newCount);
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
    return myModified ||
           !J2EEModuleUtilEx.checkDependentModulesOutputPathConsistency(myProject, J2EEModuleUtil.getAllJ2EEModules(myProject), false);
  }


  public static boolean showDialog(Project project, final String moduleToSelect, final String tabNameToSelect, final boolean show) {
    final ProjectRootConfigurable projectRootConfigurable = ProjectRootConfigurable.getInstance(project);
    return ShowSettingsUtil.getInstance().editConfigurable(project, projectRootConfigurable, new Runnable() {
      public void run() {
        projectRootConfigurable.selectModuleTab(moduleToSelect, tabNameToSelect);
        projectRootConfigurable.setStartModuleWizard(show);
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            projectRootConfigurable.setStartModuleWizard(false);
          }
        });
      }
    });
  }
}
