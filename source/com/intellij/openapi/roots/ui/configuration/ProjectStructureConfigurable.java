package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
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
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.navigation.History;
import com.intellij.ui.navigation.Place;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

@State(
  name = "ProjectStructureConfigurable.UI",
  storages = {
    @Storage(
      id ="other",
      file = "$WORKSPACE_FILE$"
    )}
)
public class ProjectStructureConfigurable implements SearchableConfigurable, HistoryAware.Facade, PersistentStateComponent<ProjectStructureConfigurable.UIState> {

  public static final DataKey<ProjectStructureConfigurable> KEY = DataKey.create("ProjectStructureConfiguration");

  private UIState myUiState = new UIState();
  private Splitter mySplitter;
  private JComponent myToolbarComponent;

  public static class UIState {
    public float proportion;
    public float sideProportion;

    public String lastEditedConfigurable;
  }

  private Project myProject;
  private ModuleManager myModuleManager;

  private History myHistory = new History();
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
  private boolean myHistoryNavigatedNow;

  private Map<Configurable, ConfigPlace> myConfig2Place = new HashMap<Configurable, ConfigPlace>();
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
    mySidePanel = new SidePanel(this);
    mySidePanel.addSeparator("Project Settings");
    mySidePanel.addPlace(createProjectConfig());
    mySidePanel.addPlace(createModulesConfig());
    mySidePanel.addPlace(createProjectLibrariesConfig());
    mySidePanel.addSeparator("Platform Settings");
    mySidePanel.addPlace(createJdkListConfig());
    mySidePanel.addPlace(createGlobalLibrariesConfig());
  }

  private ConfigPlace createJdkListConfig() {
    myJdkListConfig = JdkListConfigurable.getInstance(myProject);
    return new ConfigPlace(myJdkListConfig);
  }

  private ConfigPlace createProjectConfig() {
    myProjectConfig = new ProjectConfigurable(myProject, myModuleConfigurator, myProjectJdksModel);
    return new ConfigPlace(myProjectConfig);
  }

  private ConfigPlace createProjectLibrariesConfig() {
    myProjectLibrariesConfig = ProjectLibrariesConfigurable.getInstance(myProject);
    return new ConfigPlace(myProjectLibrariesConfig);
  }

  private ConfigPlace createGlobalLibrariesConfig() {
    myGlobalLibrariesConfig = GlobalLibrariesConfigurable.getInstance(myProject);
    return new ConfigPlace(myGlobalLibrariesConfig);
  }

  private ConfigPlace createModulesConfig() {
    myModulesConfig = ModuleStructureConfigurable.getInstance(myProject);
    return new ConfigPlace(myModulesConfig);
  }

  public boolean isModified() {
    for (Configurable each : getConfigurables()) {
      if (each.isModified()) return true;
    }

    return false;
  }

  public void apply() throws ConfigurationException {
    for (Configurable each : getConfigurables()) {
      if (each.isModified()) {
        each.apply();
      }
    }
  }

  public void reset() {
    myProjectJdksModel.reset(myProject);
    myContext.reset();

    Configurable toSelect = null;
    for (Configurable each : getConfigurables()) {
      if (myUiState.lastEditedConfigurable != null && myUiState.lastEditedConfigurable.equals(each.getDisplayName())) {
        toSelect = each;
      }
      each.reset();
    }

    myHistory.clear();

    if (toSelect == null && getConfigurables().size() > 0) {
      toSelect = getConfigurables().get(0);
    }

    removeSelected();

    select(toSelect);

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

    for (Configurable each : getConfigurables()) {
      each.disposeUIResources();
    }

    myWasIntialized = false;
  }

  private List<Configurable> getConfigurables() {
    List<Configurable> result = new ArrayList<Configurable>();
    final Collection<Place> places = mySidePanel.getPlaces();
    for (Iterator<Place> iterator = places.iterator(); iterator.hasNext();) {
      result.add(((Place<NamedConfigurable>)iterator.next()).getObject());
    }
    return result;
  }

  public boolean isHistoryNavigatedNow() {
    return myHistoryNavigatedNow;
  }

  public History getHistory() {
    return myHistory;
  }

  public ActionCallback select(final Configurable configurable) {
    if (mySelectedConfigurable == configurable) return new ActionCallback.Done();

    saveSideProportion();

    removeSelected();

    if (configurable != null) {
      final JComponent c = configurable.createComponent();
      myDetails.setContent(c);
      JComponent toFocus = IdeFocusTraversalPolicy.getPreferredFocusedComponent(c);
      if (toFocus == null) {
        toFocus = c;
      }
      c.requestFocus();
    }

    mySelectedConfigurable = configurable;
    if (mySelectedConfigurable != null) {
      myUiState.lastEditedConfigurable = mySelectedConfigurable.getDisplayName();
    }

    if (configurable instanceof MasterDetailsComponent) {
      final MasterDetailsComponent masterDetails = (MasterDetailsComponent)configurable;
      if (myUiState.sideProportion > 0) {
        masterDetails.getSplitter().setProportion(myUiState.sideProportion);
      }
    }

    if (configurable instanceof DetailsComponent.Facade) {
      ((DetailsComponent.Facade)configurable).getDetailsComponent().setBannerMinHeight(myToolbarComponent.getPreferredSize().height);
    }

    myDetails.revalidate();
    myDetails.repaint();

    final Place place = myConfig2Place.get(configurable);
    if (!isHistoryNavigatedNow() && configurable != null) {
      getHistory().pushPlace(place);
    }

    mySidePanel.select(place);

    return new ActionCallback.Done();
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

  private class ConfigPlace extends Place<Configurable> {

    private Configurable myConfig;

    public ConfigPlace(final Configurable config) {
      super(config);
      myConfig = config;
      final Presentation p = new Presentation(config.getDisplayName());
      setPresentation(p);
      setObject(config);
      if (config instanceof HistoryAware.Config) {
        ((HistoryAware.Config)config).setHistoryFacade(ProjectStructureConfigurable.this);
      }

      if (config instanceof BaseStructureConfigurable) {
        ((BaseStructureConfigurable)config).init(myContext);
      }

      myConfig2Place.put(config, this);
    }

    public void goThere() {
      myHistoryNavigatedNow = true;
      select(myConfig).doWhenDone(new Runnable() {
        public void run() {
          myHistoryNavigatedNow = false;
        }
      });
    }
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
    if (LibraryTablesRegistrar.PROJECT_LEVEL.equals(library.getTable())) {
      return myProjectLibrariesConfig;
    } else {
      return myGlobalLibrariesConfig;
    }
  }

}
