package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.ui.configuration.actions.BackAction;
import com.intellij.openapi.roots.ui.configuration.actions.ForwardAction;
import com.intellij.openapi.roots.ui.configuration.projectRoot.*;
import com.intellij.openapi.ui.DetailsComponent;
import com.intellij.openapi.ui.MasterDetailsComponent;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.navigation.History;
import com.intellij.ui.navigation.Place;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

@State(
  name = "ProjectStructureConfigurable.UI",
  storages = {
    @Storage(
      id ="other",
      file = "$WORKSPACE_FILE$"
    )}
)
public class ProjectStructureConfigurable implements SearchableConfigurable, PersistentStateComponent<ProjectStructureConfigurable.UIState>, Place.Navigator {

  public static final DataKey<ProjectStructureConfigurable> KEY = DataKey.create("ProjectStructureConfiguration");

  private UIState myUiState = new UIState();
  private Splitter mySplitter;
  private JComponent myToolbarComponent;
  @NonNls private static final String CATEGORY = "category";

  public static class UIState {
    public float proportion;
    public float sideProportion;

    public String lastEditedConfigurable;
  }

  private Project myProject;
  private ModuleManager myModuleManager;

  private History myHistory = new History(this);
  private SidePanel mySidePanel;

  private JPanel myComponent;
  private Wrapper myDetails = new Wrapper();

  private Configurable mySelectedConfigurable;

  private ProjectJdksModel myProjectJdksModel = new ProjectJdksModel();

  private ProjectConfigurable myProjectConfig;
  private ProjectLibrariesConfigurable myProjectLibrariesConfig;
  private GlobalLibrariesConfigurable myGlobalLibrariesConfig;
  private ModuleStructureConfigurable myModulesConfig;

  private boolean myWasIntialized;

  private Map<String, Configurable> myName2Config = new HashMap<String, Configurable>();
  private StructureConfigrableContext myContext;
  private ModulesConfigurator myModuleConfigurator;
  private JdkListConfigurable myJdkListConfig;

  private JLabel myEmptySelection = new JLabel("<html><body><center>Select a setting to view or edit its details here</center></body></html>", JLabel.CENTER);

  public ProjectStructureConfigurable(final Project project, final ModuleManager moduleManager) {
    myProject = project;
    myModuleManager = moduleManager;

    myModuleConfigurator = new ModulesConfigurator(myProject, myProjectJdksModel);
    myContext = new StructureConfigrableContext(myProject, myModuleManager, myModuleConfigurator);
    myModuleConfigurator.setContext(myContext);
  }

  @NonNls
  public String getId() {
    return "project.structure";
  }

  public boolean clearSearch() {
    return false;
  }

  @Nullable
  public Runnable enableSearch(final String option) {
    return null;
  }

  @Nls
  public String getDisplayName() {
    return ProjectBundle.message("project.settings.display.name");
  }

  @Nullable
  public Icon getIcon() {
    return IconLoader.getIcon("/modules/modules.png");
  }

  @Nullable
  @NonNls
  public String getHelpTopic() {
    return null;
  }

  public JComponent createComponent() {
    myComponent = new MyPanel();

    mySplitter = new Splitter(false, .15f);

    initSidePanel();

    final JPanel left = new JPanel(new BorderLayout());

    final DefaultActionGroup toolbar = new DefaultActionGroup();
    toolbar.add(new BackAction(myComponent));
    toolbar.add(new ForwardAction(myComponent));
    myToolbarComponent = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, toolbar, true).getComponent();
    left.add(myToolbarComponent, BorderLayout.NORTH);
    left.add(mySidePanel, BorderLayout.CENTER);

    mySplitter.setFirstComponent(left);
    mySplitter.setSecondComponent(myDetails);

    myComponent.add(mySplitter, BorderLayout.CENTER);

    myWasIntialized = true;

    return myComponent;
  }

  private void initSidePanel() {
    mySidePanel = new SidePanel(this, myHistory);
    mySidePanel.addSeparator("Project Settings");
    addProjectConfig();
    addModulesConfig();
    addProjectLibrariesConfig();
    mySidePanel.addSeparator("Platform Settings");
    addJdkListConfig();
    addGlobalLibrariesConfig();
  }

  private void addJdkListConfig() {
    myJdkListConfig = JdkListConfigurable.getInstance(myProject);
    init(myJdkListConfig);
  }

  private void addProjectConfig() {
    myProjectConfig = new ProjectConfigurable(myProject, myModuleConfigurator, myProjectJdksModel);
    init(myProjectConfig);
  }

  private void addProjectLibrariesConfig() {
    myProjectLibrariesConfig = ProjectLibrariesConfigurable.getInstance(myProject);
    init(myProjectLibrariesConfig);
  }

  private void addGlobalLibrariesConfig() {
    myGlobalLibrariesConfig = GlobalLibrariesConfigurable.getInstance(myProject);
    init(myGlobalLibrariesConfig);
  }

  private void addModulesConfig() {
    myModulesConfig = ModuleStructureConfigurable.getInstance(myProject);
    init(myModulesConfig);
  }

  public boolean isModified() {
    for (Configurable each : myName2Config.values()) {
      if (each.isModified()) return true;
    }

    return false;
  }

  public void apply() throws ConfigurationException {
    for (Configurable each : myName2Config.values()) {
      if (each.isModified()) {
        each.apply();
      }
    }
  }

  public void reset() {
    myProjectJdksModel.reset(myProject);
    myContext.reset();

    Configurable toSelect = null;
    for (Configurable each : myName2Config.values()) {
      if (myUiState.lastEditedConfigurable != null && myUiState.lastEditedConfigurable.equals(each.getDisplayName())) {
        toSelect = each;
      }
      each.reset();
      if (each instanceof MasterDetailsComponent) {
        ((MasterDetailsComponent)each).setHistory(myHistory);
      }
    }

    myHistory.clear();

    if (toSelect == null && myName2Config.size() > 0) {
      toSelect = myName2Config.values().iterator().next();
    }

    removeSelected();

    navigateTo(toSelect != null ? createPlaceFor(toSelect) : null);

    if (myUiState.proportion > 0) {
      mySplitter.setProportion(myUiState.proportion);
    }
  }

  public UIState getState() {
    return myUiState;
  }

  public void loadState(final UIState state) {
    myUiState = state;
  }

  public void disposeUIResources() {
    if (!myWasIntialized) return;

    myUiState.proportion = mySplitter.getProportion();
    saveSideProportion();

    for (Configurable each : myName2Config.values()) {
      each.disposeUIResources();
    }

    myWasIntialized = false;
  }

  public History getHistory() {
    return myHistory;
  }

  public void setHistory(final History history) {
    myHistory = history;
  }

  public void queryPlace(@NotNull final Place place) {
    place.putPath(CATEGORY, mySelectedConfigurable);
    Place.queryFurther(mySelectedConfigurable, place);
  }

  public ActionCallback select(@Nullable final String moduleToSelect, @Nullable final String tabNameToSelect) {
    Place place = new Place().putPath(CATEGORY, myModulesConfig);
    if (moduleToSelect != null) {
      final Module module = ModuleManager.getInstance(myProject).findModuleByName(moduleToSelect);
      assert module != null;
      place = place.putPath(ModuleStructureConfigurable.MODULE_TREE_OBJECT, module)
        .putPath(ModuleEditor.MODULE_VIEW_KEY, ModuleEditor.GENERAL_VIEW)
        .putPath(ModuleEditor.MODULE_VIEW_GENERAL_TAB, tabNameToSelect);
    }
    return navigateTo(place);
  }


  public ActionCallback navigateTo(@Nullable final Place place) {
    final Configurable toSelect = (Configurable)place.getPath(CATEGORY);

    if (mySelectedConfigurable != toSelect) {
      saveSideProportion();
      removeSelected();

      if (toSelect != null) {
        final JComponent c = toSelect.createComponent();
        myDetails.setContent(c);
        JComponent toFocus = IdeFocusTraversalPolicy.getPreferredFocusedComponent(c);
        if (toFocus == null) {
          toFocus = c;
        }
        c.requestFocus();
      }

      mySelectedConfigurable = toSelect;
      if (mySelectedConfigurable != null) {
        myUiState.lastEditedConfigurable = mySelectedConfigurable.getDisplayName();
      }

      if (toSelect instanceof MasterDetailsComponent) {
        final MasterDetailsComponent masterDetails = (MasterDetailsComponent)toSelect;
        if (myUiState.sideProportion > 0) {
          masterDetails.getSplitter().setProportion(myUiState.sideProportion);
        }
        masterDetails.setHistory(myHistory);
      }

      if (toSelect instanceof DetailsComponent.Facade) {
        ((DetailsComponent.Facade)toSelect).getDetailsComponent().setBannerMinHeight(myToolbarComponent.getPreferredSize().height);
      }
    }


    final ActionCallback result = new ActionCallback();
    Place.goFurther(toSelect, place).markDone(result);

    myDetails.revalidate();
    myDetails.repaint();

    if (toSelect != null) {
      mySidePanel.select(createPlaceFor(toSelect));
    }

    if (!myHistory.isNavigatingNow() && mySelectedConfigurable != null) {
      myHistory.pushQueryPlace();
    }

    return result;
  }

  private void saveSideProportion() {
    if (mySelectedConfigurable instanceof MasterDetailsComponent) {
      myUiState.sideProportion = ((MasterDetailsComponent)mySelectedConfigurable).getSplitter().getProportion();
    }
  }

  private void removeSelected() {
    myDetails.removeAll();
    mySelectedConfigurable = null;
    myUiState.lastEditedConfigurable = null;

    myDetails.add(myEmptySelection, BorderLayout.CENTER);
  }

  public static ProjectStructureConfigurable getInstance(final Project project) {
    return ShowSettingsUtil.getInstance().findProjectConfigurable(project, ProjectStructureConfigurable.class);
  }

  public ProjectJdksModel getProjectJdksModel() {
    return myProjectJdksModel;
  }

  public JdkListConfigurable getJdkConfig() {
    return myJdkListConfig;
  }

  public ProjectLibrariesConfigurable getProjectLibrariesConfig() {
    return myProjectLibrariesConfig;
  }

  public GlobalLibrariesConfigurable getGlobalLibrariesConfig() {
    return myGlobalLibrariesConfig;
  }

  public ModuleStructureConfigurable getModulesConfig() {
    return myModulesConfig;
  }

  public ProjectConfigurable getProjectConfig() {
    return myProjectConfig;
  }

  private void init(Configurable configurable) {
    if (configurable instanceof BaseStructureConfigurable) {
      ((BaseStructureConfigurable)configurable).init(myContext);
    }

    myName2Config.put(configurable.getDisplayName(), configurable);

    mySidePanel.addPlace(createPlaceFor(configurable), new Presentation(configurable.getDisplayName()));
  }

  private Place createPlaceFor(final Configurable configurable) {
    return new Place().putPath(CATEGORY, configurable);
  }


  public StructureConfigrableContext getContext() {
    return myContext;
  }

  private class MyPanel extends JPanel implements DataProvider {
    public MyPanel() {
      super(new BorderLayout());
    }

    @Nullable
    public Object getData(@NonNls final String dataId) {
      return KEY.getName().equals(dataId) ? ProjectStructureConfigurable.this : null;
    }

    public Dimension getPreferredSize() {
      return new Dimension(1024, 768);
    }
  }

  public BaseLibrariesConfigurable getConfigurableFor(final Library library) {
    if (LibraryTablesRegistrar.PROJECT_LEVEL.equals(library.getTable().getTableLevel())) {
      return myProjectLibrariesConfig;
    } else {
      return myGlobalLibrariesConfig;
    }
  }

}
