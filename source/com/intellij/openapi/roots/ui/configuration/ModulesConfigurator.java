package com.intellij.openapi.roots.ui.configuration;

import com.intellij.ide.IconUtilEx;
import com.intellij.ide.util.projectWizard.AddModuleWizard;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.ToolbarPanel;
import com.intellij.j2ee.J2EEModuleUtil;
import com.intellij.j2ee.module.J2EEModuleUtilEx;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleCircularDependencyException;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.roots.ui.configuration.actions.IconWithTextAction;
import com.intellij.openapi.roots.ui.configuration.actions.ModuleDeleteProvider;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Computable;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.Icons;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.*;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 15, 2003
 */
public class ModulesConfigurator implements ModulesProvider, ModuleEditor.ChangeListener {

  private final Project myProject;
  private final boolean myStartModuleWizardOnShow;

  private List<ModuleEditor> myModuleEditors = new ArrayList<ModuleEditor>();
  private JList myModuleEditorsList;
  private JPanel myModuleContentsPanel;
  private static final String EMPTY_PANEL_ID = "EmptyPanel";
  private ComboBox myLanguageLevelCombo;
  private JRadioButton myRbRelativePaths;
  private JRadioButton myRbAbsolutePaths;

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
  private JPanel myModuleListPanel;
  private static final String DIMENSION_KEY = "#com.intellij.openapi.roots.ui.configuration.ModulesConfigurator";
  private JLabel myWarningLabel;

  public ModulesConfigurator(Project project) {
    this(project, false);
  }

  public ModulesConfigurator(Project project, boolean startModuleWizardOnShow) {
    myProject = project;
    myModuleModel = ModuleManager.getInstance(myProject).getModifiableModel();
    myStartModuleWizardOnShow = startModuleWizardOnShow;
  }

  public JComponent createComponent() {
    final JPanel mainPanel = new MyJPanel();
    mainPanel.setPreferredSize(new Dimension(700, 500));

    myModuleListPanel = new JPanel(new BorderLayout());

    myLanguageLevelCombo = new ComboBox();
    myLanguageLevelCombo.addItem(LanguageLevel.JDK_1_3);
    myLanguageLevelCombo.addItem(LanguageLevel.JDK_1_4);
    myLanguageLevelCombo.addItem(LanguageLevel.JDK_1_5);
    myLanguageLevelCombo.setRenderer(new MyDefaultListCellRenderer());
    myLanguageLevelCombo.setSelectedItem(ProjectRootManagerEx.getInstanceEx(myProject).getLanguageLevel());
    myRbRelativePaths = new JRadioButton("Use relative path");
    myRbAbsolutePaths = new JRadioButton("Use absolute path");
    ButtonGroup buttonGroup = new ButtonGroup();
    buttonGroup.add(myRbRelativePaths);
    buttonGroup.add(myRbAbsolutePaths);
    if (((ProjectEx)myProject).isSavePathsRelative()) {
      myRbRelativePaths.setSelected(true);
    }
    else {
      myRbAbsolutePaths.setSelected(true);
    }


    myModuleEditorsList = new JList(new DefaultListModel());
    myModuleEditorsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myModuleEditorsList.setCellRenderer(new ModulesListCellRenderer());

    final JScrollPane modulesListScrollPane = ScrollPaneFactory.createScrollPane(myModuleEditorsList);
    final Dimension preferredSize = new Dimension(130, 100);
    modulesListScrollPane.setPreferredSize(preferredSize);
    modulesListScrollPane.setMinimumSize(preferredSize);

    myModuleContentsPanel = new JPanel(new CardLayout());
    myModuleContentsPanel.add(new JPanel(), EMPTY_PANEL_ID);

    final DefaultActionGroup moduleActionsGroup = new DefaultActionGroup();

    final AddModuleAction addModuleAction = new AddModuleAction(mainPanel);
    addModuleAction.registerCustomShortcutSet(CommonShortcuts.INSERT, myModuleEditorsList);
    moduleActionsGroup.add(addModuleAction);

    final RemoveModuleAction removeModuleAction = new RemoveModuleAction();
    removeModuleAction.registerCustomShortcutSet(CommonShortcuts.DELETE, myModuleEditorsList);
    moduleActionsGroup.add(removeModuleAction);

    myModuleListPanel.add(new JLabel("Modules:"), BorderLayout.NORTH);
    myModuleListPanel.add(new ToolbarPanel(modulesListScrollPane, moduleActionsGroup), BorderLayout.CENTER);

    final Splitter modulesContentSplitter = new Splitter(false);
    modulesContentSplitter.setHonorComponentsMinimumSize(true);
    modulesContentSplitter.setShowDividerControls(true);
    modulesContentSplitter.setProportion(0.20f);
    modulesContentSplitter.setFirstComponent(myModuleListPanel);
    modulesContentSplitter.setSecondComponent(myModuleContentsPanel);
    mainPanel.add(
      modulesContentSplitter,
      new GridBagConstraints(0, GridBagConstraints.RELATIVE, 4, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(2, 0, 0, 0), 0, 0)
    );

    mainPanel.add(new JLabel("For files outside project file directory:"), new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(2, 0, 0, 0), 0, 0));
    mainPanel.add(myRbAbsolutePaths, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(2, 0, 0, 0), 0, 0));
    mainPanel.add(myRbRelativePaths, new GridBagConstraints(2, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(2, 0, 0, 0), 0, 0));

    final Box horizontalBox = Box.createHorizontalBox();
    horizontalBox.add(new JLabel("Language level for project (effective on restart): "));
    horizontalBox.add(Box.createHorizontalStrut(5));
    horizontalBox.add(myLanguageLevelCombo);
    mainPanel.add(horizontalBox, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 3, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(2, 0, 0, 0), 0, 0));

    myWarningLabel = new JLabel("");
    myWarningLabel.setUI(new MultiLineLabelUI());
    mainPanel.add(myWarningLabel, new GridBagConstraints(3, GridBagConstraints.RELATIVE, 1, 2, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(2, 6, 0, 0), 0, 0) );

    myModuleEditorsList.addListSelectionListener(new ListSelectionListener() {
      int mySelectedIndex = -1;

      public void valueChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) {
          return;
        }
        final ModuleEditor previousEditor = getEditorAt(mySelectedIndex);
        final ModuleEditor selectedEditor = getSelectedEditor();
        if (selectedEditor != null) {
          showModuleEditor(selectedEditor, previousEditor != null ?  previousEditor.getSelectedTabName() : null);
        }
        mySelectedIndex = myModuleEditorsList.getSelectedIndex();
      }

    });

    resetModuleEditors();

    return mainPanel;
  }

  public void dispose() {
    disposeModuleEditors();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        myModuleModel.dispose();
      }
    });
  }

  public Module[] getModules() {
    return myModuleModel.getModules();
  }

  public Module getModule(String name) {
    return myModuleModel.findModuleByName(name);
  }

  public String getSelectedModuleName() {
    final ModuleEditor selectedEditor = getSelectedEditor();
    if (selectedEditor == null) {
      return null;
    }
    return selectedEditor.getModule().getName();
  }

  public String getSelectedTabName() {
    final ModuleEditor selectedEditor = getSelectedEditor();
    if (selectedEditor == null) {
      return null;
    }
    return selectedEditor.getSelectedTabName();
  }

  private ModuleEditor getModuleEditor(Module module) {
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
    try {
      disposeModuleEditors();
    }
    finally {
      myModuleModel = ModuleManager.getInstance(myProject).getModifiableModel();
    }

    resetModuleEditors();

    refreshUI();
  }

  private void disposeModuleEditors() {
    for (final ModuleEditor moduleEditor : myModuleEditors) {
      removeModuleEditorUIComponent(moduleEditor);
      final ModifiableRootModel model = moduleEditor.dispose();
      if (model != null) {
        model.dispose();
      }
    }
  }

  private void resetModuleEditors() {
    for (final ModuleEditor moduleEditor : myModuleEditors) {
      moduleEditor.removeChangeListener(this);
    }
    myModuleEditors.clear();
    final DefaultListModel listModel = (DefaultListModel)myModuleEditorsList.getModel();
    listModel.clear();
    final Module[] modules = myModuleModel.getModules();
    if (modules.length > 0) {
      for (Module module : modules) {
        createModuleEditor(module);
      }
      Collections.sort(myModuleEditors, myModuleEditorComparator);
      for (final ModuleEditor myModuleEditor : myModuleEditors) {
        listModel.addElement(new ModuleEditorWrapper(myModuleEditor));
      }
    }
    updateCircularDependencyWarning();
  }

  private ModuleEditor createModuleEditor(final Module module) {
    final ModuleEditor moduleEditor = new ModuleEditor(myProject, this, module.getName());
    myModuleEditors.add(moduleEditor);
    moduleEditor.addChangeListener(this);
    return moduleEditor;
  }

  public void moduleStateChanged(final ModifiableRootModel moduleRootModel) {
    updateCircularDependencyWarning();
  }

  private void updateCircularDependencyWarning() {
    final ProjectRootManagerEx projectRootManagerEx = ProjectRootManagerEx.getInstanceEx(myProject);
    String warningMessage = "";
    try {
      List<ModifiableRootModel> modelsToCheck = new ArrayList<ModifiableRootModel>(myModuleEditors.size());
      for (final ModuleEditor moduleEditor : myModuleEditors) {
        final ModifiableRootModel model = moduleEditor.getModifiableRootModel();
        if (model != null) {
          modelsToCheck.add(model);
        }
      }
      projectRootManagerEx.checkCircularDependency(modelsToCheck.toArray(new ModifiableRootModel[modelsToCheck.size()]), myModuleModel);
    }
    catch (ModuleCircularDependencyException e) {
      warningMessage = "There is a circular dependency between modules\n\"" + e.getModuleName1() + "\" and \"" + e.getModuleName2() + "\"";
    }
    myWarningLabel.setIcon(warningMessage.length() > 0? Messages.getWarningIcon() : null);
    myWarningLabel.setText(warningMessage);
  }

  public void apply() throws ConfigurationException {
    final ProjectRootManagerEx projectRootManagerEx = ProjectRootManagerEx.getInstanceEx(myProject);

    try {
      final List<ModifiableRootModel> models = new ArrayList<ModifiableRootModel>(myModuleEditors.size());
      for (final ModuleEditor moduleEditor : myModuleEditors) {
        removeModuleEditorUIComponent(moduleEditor);
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
    finally {
      refreshUI();
    }
  }

  private void refreshUI() {
    if (myModuleEditorsList == null) {
      return;
    }
    int selectedModuleIndex = myModuleEditorsList.getSelectedIndex();
    if (selectedModuleIndex < 0 && myModuleEditors.size() > 0) {
      selectedModuleIndex = 0;
    }
    if (selectedModuleIndex >= 0) {
      final ModuleEditor selectedEditor = myModuleEditors.get(selectedModuleIndex);
      showModuleEditor(selectedEditor, null); // this will create new ModifiableRootModel for selected editor as well
    }
    else {
      showModuleEditor(null, null);
    }
  }

  private void removeModuleEditorUIComponent(final ModuleEditor moduleEditor) {
    final String id = moduleEditor.getName();
    if (myShownModuleEditors.contains(id)) {
      myModuleContentsPanel.remove(moduleEditor.getPanel());
      myShownModuleEditors.remove(id);
    }
  }

  private Set<String> myShownModuleEditors = new HashSet<String>();

  private void showModuleEditor(ModuleEditor moduleEditor, final String tabNameToSelect) {
    final String id;
    if (moduleEditor != null) {
      id = moduleEditor.getName();
      if (!myShownModuleEditors.contains(id)) {
        myModuleContentsPanel.add(moduleEditor.getPanel(), id);
        myShownModuleEditors.add(id);
      }
      myModuleEditorsList.setSelectedIndex(myModuleEditors.indexOf(moduleEditor));
      moduleEditor.setSelectedTabName(tabNameToSelect);
    }
    else {
      id = EMPTY_PANEL_ID;
    }
    ((CardLayout)myModuleContentsPanel.getLayout()).show(myModuleContentsPanel, id);
  }

  private ModuleEditor getSelectedEditor() {
    if (myModuleEditors.size() == 0) {
      return null;
    }
    if (myModuleEditors.size() == 1) {
      return myModuleEditors.get(0);
    }
    return getEditorAt(myModuleEditorsList.getSelectedIndex());
  }

  private ModuleEditor getEditorAt(final int selectedIndex) {
    return selectedIndex >= 0 && selectedIndex < myModuleEditors.size() ? myModuleEditors.get(selectedIndex) : null;
  }

  private static class ModuleEditorWrapper {
    private final ModuleEditor myModuleEditor;
    private final String myDisplayName;

    public ModuleEditorWrapper(ModuleEditor moduleEditor) {
      myModuleEditor = moduleEditor;
      myDisplayName = myModuleEditor.getModule().getName();
    }

    public String toString() {
      return myDisplayName;
    }

    public ModuleEditor getModuleEditor() {
      return myModuleEditor;
    }
  }

  private ModuleEditor findModuleEditor(String name) {
    for (final ModuleEditor moduleEditor : myModuleEditors) {
      if (name.equals(moduleEditor.getModule().getName())) {
        return moduleEditor;
      }
    }
    return null;
  }

  private void addModule(final Module module) {
    final ModuleEditor moduleEditor = createModuleEditor(module);
    final ModuleEditor selectedEditor = getSelectedEditor();

    Collections.sort(myModuleEditors, myModuleEditorComparator);
    final int insertIndex = myModuleEditors.indexOf(moduleEditor);
    final String selectedTab = selectedEditor != null ? selectedEditor.getSelectedTabName() : null;
    final DefaultListModel listModel = (DefaultListModel)myModuleEditorsList.getModel();
    listModel.add(insertIndex, new ModuleEditorWrapper(moduleEditor));
    showModuleEditor(moduleEditor, selectedTab);
    myModuleEditorsList.revalidate();
    processModuleCountChanged(myModuleEditors.size() - 1, myModuleEditors.size());
  }

  public void addModule(final ModuleBuilder moduleBuilder) {
    final Exception[] ex = new Exception[] {null};
    final Module module = ApplicationManager.getApplication().runWriteAction(new Computable<Module>() {
      public Module compute() {
        try {
          return moduleBuilder.createModule(myModuleModel);
        }
        catch (Exception e) {
          ex[0] = e;
          return null;
        }
      }
    });
    if (ex[0] != null) {
      Messages.showErrorDialog("Error adding module to project: " + ex[0].getMessage(), "Add Module");
    }
    if (module != null) {
      addModule(module);
    }
  }

  private ModuleBuilder runModuleWizard(Component dialogParent) {
    AddModuleWizard wizard = new AddModuleWizard(dialogParent, myProject, this);
    wizard.show();
    if (wizard.isOK()) {
      return wizard.getModuleBuilder();
    }
    return null;
  }


  private class AddModuleAction extends IconWithTextAction {
    private final Component myDialogParent;

    public AddModuleAction(Component dialogParent) {
      super("Add", "Add module to the project", Icons.ADD_ICON);
      myDialogParent = dialogParent;
    }

    public void actionPerformed(AnActionEvent e) {
      final ModuleBuilder moduleBuilder = runModuleWizard(myDialogParent);
      if (moduleBuilder != null) {
        addModule(moduleBuilder);
        myModuleEditorsList.requestFocus();
      }
    }
  }


  private class RemoveModuleAction extends IconWithTextAction {
    public RemoveModuleAction() {
      super("Remove", "Remove module from the project", Icons.DELETE_ICON);
    }

    public void actionPerformed(AnActionEvent e) {
      try {
        final ModuleEditor selectedEditor = getSelectedEditor();
        String question;
        if (myModuleEditors.size() == 1) {
          question = "Are you sure you want to remove the only module from this project?\nNo files will be deleted on disk.";
        }
        else {
          question = "Remove module \"" + selectedEditor.getModule().getName() + "\" from the project?\nNo files will be deleted on disk.";
        }
        int result = Messages.showYesNoDialog(myModuleEditorsList, question, "Remove Module", Messages.getQuestionIcon());
        if (result != 0) {
          return;
        }
        // do remove
        myModuleEditors.remove(selectedEditor);
        removeModuleEditorUIComponent(selectedEditor);
        final DefaultListModel listModel = (DefaultListModel)myModuleEditorsList.getModel();
        final int selectedIndex = myModuleEditorsList.getSelectedIndex();
        listModel.removeElementAt(selectedIndex);
        // select another
        if (selectedIndex < listModel.getSize()) {
          myModuleEditorsList.setSelectedIndex(selectedIndex);
        }
        else if (listModel.getSize() > 0) {
          myModuleEditorsList.setSelectedIndex(0);
        }
        final ModuleEditor newSelectedEditor = getSelectedEditor();
        final String selectedTabName = selectedEditor.getSelectedTabName();
        showModuleEditor(newSelectedEditor, selectedTabName);
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
        myModuleEditorsList.revalidate();
        processModuleCountChanged(myModuleEditors.size() + 1, myModuleEditors.size());
      }
      finally {
        myModuleEditorsList.requestFocus();
      }
    }

    public void update(AnActionEvent e) {
      final ModuleEditor selectedEditor = getSelectedEditor();
      e.getPresentation().setEnabled(selectedEditor != null);
    }
  }

  private void processModuleCountChanged(int oldCount, int newCount) {
    //updateTitle();
    for (ModuleEditor moduleEditor : myModuleEditors) {
      moduleEditor.moduleCountChanged(oldCount, newCount);
    }
  }

  public boolean selectModule(String moduleNameToSelect, String tabToSelect) {
    final ModuleEditor editor = findModuleEditor(moduleNameToSelect);
    if (editor != null) {
      showModuleEditor(editor, tabToSelect);
      return true;
    }
    return false;
  }

  public void selectFirstModule() {
    if (myModuleEditors.size() > 0) {
      myModuleEditorsList.setSelectedIndex(0);
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


  public static boolean showDialog(Project project, String moduleToSelect, String tabNameToSelect, boolean startAddModuleWizard) {
    return ShowSettingsUtil.getInstance().editConfigurable(
      project, DIMENSION_KEY, new ModulesConfigurable(project, moduleToSelect, tabNameToSelect, startAddModuleWizard)
    );
  }

  public String getHelpTopic() {
    final ModuleEditor selectedEditor = getSelectedEditor();
    if (selectedEditor == null) {
      return null;
    }
    return selectedEditor.getHelpTopic();
  }

  private static class ModulesListCellRenderer extends DefaultListCellRenderer {
    public Component getListCellRendererComponent(JList list,
                                                  Object value,
                                                  int index,
                                                  boolean isSelected,
                                                  boolean cellHasFocus) {
      final JLabel rendererComponent = (JLabel)super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      ModuleEditorWrapper moduleEditorWrapper = (ModuleEditorWrapper)value;
      final ModuleType moduleType = moduleEditorWrapper.getModuleEditor().getModule().getModuleType();
      rendererComponent.setIcon(IconUtilEx.getModuleTypeIcon(moduleType, 0));
      return rendererComponent;
    }
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

  private static class MyDefaultListCellRenderer extends DefaultListCellRenderer {
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      setText(((LanguageLevel)value).getPresentableText());
      return this;
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
      final String _message = "Language level has been changed.\nReload project \"" + myProject.getName() + "\"?";
      if (Messages.showYesNoDialog(myModuleListPanel, _message, "Modules", Messages.getQuestionIcon()) == 0) {
        ProjectManagerEx.getInstanceEx().reloadProject(myProject);
      }
    }
  }
}
