package com.intellij.openapi.roots.ui.configuration;

import com.intellij.compiler.Chunk;
import com.intellij.compiler.ModuleCompilerUtil;
import com.intellij.ide.util.projectWizard.AddModuleWizard;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.javaee.J2EEModuleUtil;
import com.intellij.javaee.module.J2EEModuleUtilEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.roots.ui.configuration.actions.ModuleDeleteProvider;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectRootConfigurable;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.IconLoader;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.graph.CachingSemiGraph;
import com.intellij.util.graph.Graph;
import com.intellij.util.graph.GraphGenerator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.*;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 15, 2003
 */
public class ModulesConfigurator implements ModulesProvider, ModuleEditor.ChangeListener, NamedConfigurable<Project> {

  private final Project myProject;
  private boolean myStartModuleWizardOnShow;

  private static final Icon PROJECT_ICON = IconLoader.getIcon("/nodes/project.png");

  private List<ModuleEditor> myModuleEditors = new ArrayList<ModuleEditor>();
  private LanguageLevelCombo myLanguageLevelCombo;
  private ProjectJdkConfigurable myProjectJdkConfigurable;
  private JRadioButton myRbRelativePaths;

  @SuppressWarnings({"FieldCanBeLocal"})
  private JRadioButton myRbAbsolutePaths;

  private MyJPanel myPanel;

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
  private JLabel myWarningLabel = new JLabel("");
  private ProjectRootConfigurable myProjectRootConfigurable;

  public ModulesConfigurator(Project project, ProjectRootConfigurable projectRootConfigurable) {
    myProject = project;
    myProjectRootConfigurable = projectRootConfigurable;
    myModuleModel = ModuleManager.getInstance(myProject).getModifiableModel();
    init();
  }

  public JComponent createComponent() {
    myProjectJdkConfigurable.createComponent(); //reload changed jdks
    return myPanel;
  }

  private void init() {
    myPanel = new MyJPanel();
    myPanel.setPreferredSize(new Dimension(700, 500));
    myLanguageLevelCombo = new LanguageLevelCombo(myProject);
    myRbRelativePaths = new JRadioButton(ProjectBundle.message("module.paths.outside.module.dir.relative.radio"));
    myRbAbsolutePaths = new JRadioButton(ProjectBundle.message("module.paths.outside.module.dir.absolute.radio"));
    ButtonGroup buttonGroup = new ButtonGroup();
    buttonGroup.add(myRbRelativePaths);
    buttonGroup.add(myRbAbsolutePaths);
    if (((ProjectEx)myProject).isSavePathsRelative()) {
      myRbRelativePaths.setSelected(true);
    }
    else {
      myRbAbsolutePaths.setSelected(true);
    }

    myPanel.add(new JLabel(ProjectBundle.message("module.paths.outside.project.dir.label")), new GridBagConstraints(0,
                                                                                                                    GridBagConstraints.RELATIVE,
                                                                                                                    1, 1, 0.0, 0.0,
                                                                                                                    GridBagConstraints.WEST,
                                                                                                                    GridBagConstraints.NONE,
                                                                                                                    new Insets(2, 0, 0, 0),
                                                                                                                    0, 0));
    myPanel.add(myRbAbsolutePaths, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.WEST,
                                                          GridBagConstraints.NONE, new Insets(2, 0, 0, 0), 0, 0));
    myPanel.add(myRbRelativePaths, new GridBagConstraints(2, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.WEST,
                                                          GridBagConstraints.NONE, new Insets(2, 0, 0, 0), 0, 0));

    final Box horizontalBox = Box.createHorizontalBox();
    horizontalBox.add(new JLabel(ProjectBundle.message("module.project.language.level")));
    horizontalBox.add(Box.createHorizontalStrut(5));
    horizontalBox.add(myLanguageLevelCombo);
    myPanel.add(horizontalBox, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 3, 1, 0.0, 0.0, GridBagConstraints.WEST,
                                                      GridBagConstraints.NONE, new Insets(2, 0, 0, 0), 0, 0));

    myProjectJdkConfigurable = new ProjectJdkConfigurable(myProject, myProjectRootConfigurable);
    myPanel.add(myProjectJdkConfigurable.createComponent(), new GridBagConstraints(0, GridBagConstraints.RELATIVE, 3, 1, 0.0, 0.0, GridBagConstraints.WEST,
                                                                                   GridBagConstraints.NONE, new Insets(2, 0, 0, 0), 0, 0));


    myWarningLabel.setUI(new MultiLineLabelUI());
    myPanel.add(myWarningLabel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 3, 1, 1.0, 1.0, GridBagConstraints.WEST,
                                                       GridBagConstraints.BOTH, new Insets(2, 6, 0, 0), 0, 0));



    resetModuleEditors();
  }

  public void disposeUIResources() {
    disposeModuleEditors();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        myModuleModel.dispose();
      }
    });
    myProjectJdkConfigurable.disposeUIResources();
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

  public void reset() {
    myModuleModel = ModuleManager.getInstance(myProject).getModifiableModel();

    resetModuleEditors();
    if (myProjectJdkConfigurable != null) myProjectJdkConfigurable.reset();
  }

  private void disposeModuleEditors() {
    for (final ModuleEditor moduleEditor : myModuleEditors) {
      final ModifiableRootModel model = moduleEditor.dispose();
      if (model != null) {
        model.dispose();
      }
    }
    myModuleEditors.clear();
  }

  private void resetModuleEditors() {
    for (final ModuleEditor moduleEditor : myModuleEditors) {
      moduleEditor.removeChangeListener(this);
    }
    myModuleEditors.clear();
    final Module[] modules = myModuleModel.getModules();
    if (modules.length > 0) {
      for (Module module : modules) {
        createModuleEditor(module);
      }
      Collections.sort(myModuleEditors, myModuleEditorComparator);
    }
    updateCircularDependencyWarning();
  }

  private ModuleEditor createModuleEditor(final Module module) {
    final ModuleEditor moduleEditor = new ModuleEditor(myProject, this, module.getName(), myProjectRootConfigurable);
    myModuleEditors.add(moduleEditor);
    moduleEditor.addChangeListener(this);
    return moduleEditor;
  }

  public void moduleStateChanged(final ModifiableRootModel moduleRootModel) {
    updateCircularDependencyWarning();
  }

  private synchronized void updateCircularDependencyWarning() {
    final List<ModifiableRootModel> result = new ArrayList<ModifiableRootModel>();
    for (ModuleEditor moduleEditor : myModuleEditors) {
      result.add(moduleEditor.getModifiableRootModel());
    }
    final GraphGenerator<ModifiableRootModel> graphGenerator = GraphGenerator.create(CachingSemiGraph.create(new GraphGenerator.SemiGraph<ModifiableRootModel>() {
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
    final Graph<Chunk<ModifiableRootModel>> graph = ModuleCompilerUtil.toChunkGraph(graphGenerator);
    final Collection<Chunk<ModifiableRootModel>> chunks = graph.getNodes();
    String cycles = "";
    int count = 0;
    for (Chunk<ModifiableRootModel> chunk : chunks) {
      final Set<ModifiableRootModel> modules = chunk.getNodes();
      String cycle = "";
      for (ModifiableRootModel model : modules) {
        cycle += ", " + model.getModule().getName();
      }
      if (modules.size() > 1) {
        @NonNls final String br = "<br>";
        cycles += br + cycle.substring(2);
        count ++;
      }
    }
    @NonNls final String leftBrace = "<html>";
    @NonNls final String rightBrace = "</html>";
    String warningMessage = leftBrace + (count > 0 ? ProjectBundle.message("module.circular.dependency.warning", cycles, count) : "") + rightBrace;
    myWarningLabel.setIcon(warningMessage.length() > 0 ? Messages.getWarningIcon() : null);
    myWarningLabel.setText(warningMessage);
    myWarningLabel.repaint();
  }

  public void apply() throws ConfigurationException {
    final ProjectRootManagerEx projectRootManagerEx = ProjectRootManagerEx.getInstanceEx(myProject);

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
          final LanguageLevel newLevel = (LanguageLevel)myLanguageLevelCombo.getSelectedItem();
          projectRootManagerEx.setLanguageLevel(newLevel);
          ((ProjectEx)myProject).setSavePathsRelative(myRbRelativePaths.isSelected());
          try {
            myProjectJdkConfigurable.apply();
          }
          catch (ConfigurationException e) {
            //cant't be
          }
          final ModifiableRootModel[] rootModels = models.toArray(new ModifiableRootModel[models.size()]);
          projectRootManagerEx.multiCommit(myModuleModel, rootModels);
        }
        finally {
          myModuleModel = ModuleManager.getInstance(myProject).getModifiableModel();
        }
      }
    });

    if (!J2EEModuleUtilEx.checkDependentModulesOutputPathConsistency(myProject, J2EEModuleUtil.getAllJ2EEModules(myProject), true)) {
      throw new ConfigurationException(null);
    }

    ApplicationManager.getApplication().saveAll();

  }

  public void deleteModule(final Module module) {
    doRemoveModule(getModuleEditor(module));
  }

  public void setDisplayName(final String name) {
    //do nothing
  }

  public Project getEditableObject() {
    return myProject;
  }

  public String getBannerSlogan() {
    return myProject.getName();
  }

  public String getDisplayName() {
    return myProject.getName();
  }

  public Icon getIcon() {
    return PROJECT_ICON;
  }

  @Nullable
  @NonNls
  public String getHelpTopic() { //todo help 
    return null;
  }


  public Module addModule(Component parent) {
    final ModuleBuilder builder = runModuleWizard(parent);
    if (builder != null) {
      final Module module = createModule(builder);
      if (module != null) {
        createModuleEditor(module);
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

  private Module addModule(final ModuleBuilder moduleBuilder) {
    final Module module = createModule(moduleBuilder);
    if (module != null) {
      createModuleEditor(module);
      Collections.sort(myModuleEditors, myModuleEditorComparator);
      processModuleCountChanged(myModuleEditors.size() - 1, myModuleEditors.size());
      return module;
    }
    return null;
  }

  private ModuleBuilder runModuleWizard(Component dialogParent) {
    AddModuleWizard wizard = new AddModuleWizard(dialogParent, myProject, this);
    wizard.show();
    if (wizard.isOK()) {
      return wizard.getModuleBuilder();
    }
    return null;
  }


  private void doRemoveModule(ModuleEditor selectedEditor) {

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
      return;
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
    if (myLanguageLevelCombo != null) {
      if (!ProjectRootManagerEx.getInstanceEx(myProject).getLanguageLevel().equals(myLanguageLevelCombo.getSelectedItem())) {
        return true;
      }
    }

    if (myProjectJdkConfigurable != null){
      if (myProjectJdkConfigurable.isModified()) return true;
    }

    if (myRbRelativePaths != null) {
      if (((ProjectEx)myProject).isSavePathsRelative() != myRbRelativePaths.isSelected()) {
        return true;
      }
    }
    if (!J2EEModuleUtilEx.checkDependentModulesOutputPathConsistency(myProject, J2EEModuleUtil.getAllJ2EEModules(myProject), false)) {
      return true;
    }

    return false;
  }


  public static boolean showDialog(Project project, final String moduleToSelect, final String tabNameToSelect, final boolean show) {
    final ProjectRootConfigurable projectRootConfigurable = ProjectRootConfigurable.getInstance(project);
    return ShowSettingsUtil.getInstance().editConfigurable(project, projectRootConfigurable, new Runnable(){
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

  public void setStartModuleWizardOnShow(final boolean show) {
    myStartModuleWizardOnShow = show;
  }

  private class MyJPanel extends JPanel {
    public MyJPanel() {
      super(new GridBagLayout());
    }

    public void addNotify() {
      super.addNotify();
      if (myStartModuleWizardOnShow) {
        final Window parentWindow = (Window)SwingUtilities.getAncestorOfClass(Window.class, this);
        parentWindow.addWindowListener(new WindowAdapter() {
          public void windowActivated(WindowEvent e) {
            parentWindow.removeWindowListener(this);
            SwingUtilities.invokeLater(new Runnable() {
              public void run() {
                final ModuleBuilder moduleBuilder = runModuleWizard(parentWindow);
                if (moduleBuilder != null) {
                  addModule(moduleBuilder);
                }
              }
            });
          }
        });
      }
    }
  }

  private class ReloadProjectRequest implements Runnable {
    private final LanguageLevel myOriginalLanguageLevel;

    public ReloadProjectRequest(final LanguageLevel originalLanguageLevel) {
      myOriginalLanguageLevel = originalLanguageLevel;
    }

    public void start() {
      final ProjectRootManagerEx projectRootManagerEx = ProjectRootManagerEx.getInstanceEx(myProject);
      if (!myOriginalLanguageLevel.equals(projectRootManagerEx.getLanguageLevel())) {
        ApplicationManager.getApplication().invokeLater(this, ModalityState.current());
      }
    }

    public void run() {
      final String _message = ProjectBundle.message("module.project.language.level.changed.reload.prompt", myProject.getName());
      if (Messages.showYesNoDialog(myProject, _message, ProjectBundle.message("modules.title"), Messages.getQuestionIcon()) == 0) {
        ProjectManagerEx.getInstanceEx().reloadProject(myProject);
      }
    }
  }
}
