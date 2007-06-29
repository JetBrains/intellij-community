package com.intellij.openapi.roots.ui.configuration;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetModel;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleConfigurationEditor;
import com.intellij.openapi.module.impl.ModuleConfigurationStateImpl;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.FacetEditorFacadeImpl;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ModuleConfigurable;
import com.intellij.openapi.ui.ChooseView;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.TabbedPaneWrapper;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.navigation.History;
import com.intellij.ui.navigation.Place;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import java.awt.*;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Oct 4, 2003
 *         Time: 6:29:56 PM
 */
@SuppressWarnings({"AssignmentToStaticFieldFromInstanceMethod"})
public class ModuleEditor implements Place.Navigator {

  @NonNls private static final String MODULE_VIEW = "module.view";

  private final Project myProject;
  private JPanel myGenericSettingsPanel;
  private ModifiableRootModel myModifiableRootModel; // important: in order to correctly update OrderEntries UI use corresponding proxy for the model
  private static String ourSelectedTabName;
  private TabbedPaneWrapper myTabbedPane;
  private final ModulesProvider myModulesProvider;
  private String myName;
  @Nullable
  private final ModuleBuilder myModuleBuilder;
  private List<ModuleConfigurationEditor> myEditors = new ArrayList<ModuleConfigurationEditor>();
  private ModifiableRootModel myModifiableRootModelProxy;

  private EventDispatcher<ChangeListener> myEventDispatcher = EventDispatcher.create(ChangeListener.class);
  @NonNls private static final String METHOD_COMMIT = "commit";
  private ModuleConfigurable myConfigurable;

  private Wrapper myComponent = new Wrapper();
  private ChooseView myChooseView;

  private FacetEditorFacadeImpl myFacetEditorFacade;
  private ModuleEditor.GeneralView myGeneralView;

  private static final String GENERAL_VIEW = "General";
  private ModuleEditor.ManageFacets myManageFacets;

  private Disposable myRoot = new Disposable() {
    public void dispose() {
    }
  };
  private History myHistory;
  private SwitchView myCurrentView;
  private Map<String, ViewItem> myKey2Item = new HashMap<String, ViewItem>();

  @Nullable
  public ModuleBuilder getModuleBuilder() {
    return myModuleBuilder;
  }

  public void setHistoryFacade(ModuleConfigurable configurable) {
    myConfigurable = configurable;
  }

  public void init(final String selectedTab, final ChooseView chooseView, final FacetEditorFacadeImpl facetEditorFacade, History history) {
    myHistory = history;

    setSelectedTabName(selectedTab);
    myChooseView = chooseView;
    myFacetEditorFacade = facetEditorFacade;

    myGeneralView = new GeneralView();
    myManageFacets = new ManageFacets();

    updateViewChooser();


    myFacetEditorFacade.getFacetConfigurator().getOrCreateModifiableModel(getModule()).addListener(new FacetModel.Listener() {
      public void onChanged() {
        updateViewChooser();
      }
    }, myRoot);
  }

  private void updateViewChooser() {
    myChooseView.clear();

    final FacetModel facetModel = myFacetEditorFacade.getFacetConfigurator().getFacetModel(getModule());
    final Facet[] facets = facetModel.getSortedFacets();

    addView(myGeneralView);

    myChooseView.addSeparator("Facet Settings");

    for (Facet each : facets) {
      addView(new FacetView(each));
    }

    myChooseView.addSeparator(null);
    addView(myManageFacets);

    if (myChooseView.isSelectionValid()) {
      myGeneralView.switchTo();
    }

  }

  private void addView(final ViewItem item) {
    myKey2Item.put(item.getKey(), item);
    myChooseView.addView(item.getKey(), item.getStep(), item.getIcon(), new ChooseView.ViewCheck() {
      public boolean isSelectable(final String key) {
        if (item instanceof SwitchView) {
          return !myChooseView.isSelected(key);
        }
        return true;
      }
    });
  }

  public static interface ChangeListener extends EventListener {
    void moduleStateChanged(ModifiableRootModel moduleRootModel);
  }

  public ModuleEditor(Project project, ModulesProvider modulesProvider, String moduleName, @Nullable ModuleBuilder moduleBuilder) {
    myProject = project;
    myModulesProvider = modulesProvider;
    myName = moduleName;
    myModuleBuilder = moduleBuilder;
  }

  public void addChangeListener(ChangeListener listener) {
    myEventDispatcher.addListener(listener);
  }

  public void removeChangeListener(ChangeListener listener) {
    myEventDispatcher.removeListener(listener);
  }

  public Module getModule() {
    return myModulesProvider.getModule(myName);
  }

  public ModifiableRootModel getModifiableRootModel() {
    if (myModifiableRootModel == null){
      myModifiableRootModel = ModuleRootManager.getInstance(getModule()).getModifiableModel();
    }
    return myModifiableRootModel;
  }

  public ModifiableRootModel getModifiableRootModelProxy() {
    if (myModifiableRootModelProxy == null) {
      myModifiableRootModelProxy = (ModifiableRootModel)Proxy.newProxyInstance(
        getClass().getClassLoader(), new Class[]{ModifiableRootModel.class}, new ModifiableRootModelInvocationHandler(getModifiableRootModel())
      );
    }
    return myModifiableRootModelProxy;
  }

  public boolean isModified() {
    for (ModuleConfigurationEditor moduleElementsEditor : myEditors) {
      if (moduleElementsEditor.isModified()) {
        return true;
      }
    }
    return false;
  }

  private void createEditors(Module module) {
    ModuleConfigurationEditorProvider[] providers = module.getComponents(ModuleConfigurationEditorProvider.class);
    ModuleConfigurationState state = createModuleConfigurationState();
    List<ModuleLevelConfigurablesEditorProvider> moduleLevelProviders = new ArrayList<ModuleLevelConfigurablesEditorProvider>();
    for (ModuleConfigurationEditorProvider provider : providers) {
      if (provider instanceof ModuleLevelConfigurablesEditorProvider) {
        moduleLevelProviders.add((ModuleLevelConfigurablesEditorProvider)provider);
        continue;
      }
      processEditorsProvider(provider, state);
    }
    for (ModuleLevelConfigurablesEditorProvider provider : moduleLevelProviders) {
      processEditorsProvider(provider, state);
    }
  }

  public ModuleConfigurationState createModuleConfigurationState() {
    return new ModuleConfigurationStateImpl(myProject, myModulesProvider, getModifiableRootModelProxy()
      ,/*myFacetsProvider */ null);
  }

  private void processEditorsProvider(final ModuleConfigurationEditorProvider provider, final ModuleConfigurationState state) {
    final ModuleConfigurationEditor[] editors = provider.createEditors(state);
    myEditors.addAll(Arrays.asList(editors));
  }

  private JPanel createPanel() {
    getModifiableRootModel(); //initialize model if needed
    getModifiableRootModelProxy();

    myGenericSettingsPanel = new ModuleEditorPanel();

    createEditors(getModule());

    JPanel northPanel = new JPanel(new GridBagLayout());

    myGenericSettingsPanel.add(northPanel, BorderLayout.NORTH);

    myTabbedPane = new TabbedPaneWrapper();

    for (ModuleConfigurationEditor editor : myEditors) {
      myTabbedPane.addTab(editor.getDisplayName(), editor.getIcon(), editor.createComponent(), null);
      editor.reset();
    }
    setSelectedTabName(ourSelectedTabName);

    myGenericSettingsPanel.add(myTabbedPane.getComponent(), BorderLayout.CENTER);
    myTabbedPane.addChangeListener(new javax.swing.event.ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        ourSelectedTabName = getSelectedTabName();
        pushHistory();
      }
    });

    return myGenericSettingsPanel;
  }

  private void pushHistory() {
    myHistory.pushPlaceForElement("general.tab", myTabbedPane.getTitleAt(myTabbedPane.getSelectedIndex()));
  }

  public ActionCallback navigateTo(@Nullable final Place place) {
    final ModuleEditor.ViewItem item = myKey2Item.get(place.getPath(MODULE_VIEW));
    if (item instanceof SwitchView) {
      ((SwitchView)item).navigateTo(place);
    }

    return new ActionCallback.Done();
  }

  public void queryPlace(@NotNull final Place place) {
    if (myCurrentView != null) {
      myCurrentView.queryPlace(place);
    }
  }

  public static String getSelectedTab(){
    return ourSelectedTabName;
  }

  private int getEditorTabIndex(final String editorName) {
    if (myTabbedPane != null && editorName != null) {
      final int tabCount = myTabbedPane.getTabCount();
      for (int idx = 0; idx < tabCount; idx++) {
        if (editorName.equals(myTabbedPane.getTitleAt(idx))) {
          return idx;
        }
      }
    }
    return -1;
  }

  public JPanel getPanel() {
    if (myGenericSettingsPanel == null) {
      myGenericSettingsPanel = createPanel();
    }

    return myComponent;
  }

  public void setSelectedView(SwitchView view, final boolean showName) {
    myCurrentView = view;


    myComponent.setContent(view.getComponent());

    if (myConfigurable != null) {
      myConfigurable.setNameFieldShown(showName);
    }

    myChooseView.setSelected(view.getKey());

    myHistory.pushQueryPlace();

    myComponent.revalidate();
    myComponent.repaint();
  }

  public void moduleCountChanged(int oldCount, int newCount) {
    updateOrderEntriesInEditors();
  }

  public void updateOrderEntriesInEditors() {
    if (getModule() != null) { //module with attached module libraries was deleted
      getPanel();  //init editor if needed
      for (final ModuleConfigurationEditor myEditor : myEditors) {
        myEditor.moduleStateChanged();
      }
      myEventDispatcher.getMulticaster().moduleStateChanged(getModifiableRootModelProxy());
    }
  }

  public void updateCompilerOutputPathChanged(String baseUrl, String moduleName){
    getPanel();  //init editor if needed
    for (final ModuleConfigurationEditor myEditor : myEditors) {
      if (myEditor instanceof ModuleElementsEditor) {
        ((ModuleElementsEditor)myEditor).moduleCompileOutputChanged(baseUrl, moduleName);
      }
    }
  }

  public ModifiableRootModel dispose() {
    try {
      for (final ModuleConfigurationEditor myEditor : myEditors) {
        myEditor.disposeUIResources();
      }

      myEditors.clear();

      if (myTabbedPane != null) {
        ourSelectedTabName = getSelectedTabName();
        myTabbedPane = null;
      }


      myGenericSettingsPanel = null;

      Disposer.dispose(myRoot);

      return myModifiableRootModel;
    }
    finally {
      myModifiableRootModel = null;
      myModifiableRootModelProxy = null;
    }
  }

  public ModifiableRootModel applyAndDispose() throws ConfigurationException {
    for (ModuleConfigurationEditor editor : myEditors) {
      editor.saveData();
      editor.apply();
    }

    return dispose();
  }

  public String getName() {
    return myName;
  }

  @Nullable
  public String getSelectedTabName() {
    return myTabbedPane == null || myTabbedPane.getSelectedIndex() == -1 ? null : myTabbedPane.getTitleAt(myTabbedPane.getSelectedIndex());
  }

  public void setSelectedTabName(@Nullable String name) {
    if (name != null) {
      getPanel();
      final int editorTabIndex = getEditorTabIndex(name);
      if (editorTabIndex >= 0 && editorTabIndex < myTabbedPane.getTabCount()) {
        myTabbedPane.setSelectedIndex(editorTabIndex);
        ourSelectedTabName = name;
      }
    }
  }

  private class ModifiableRootModelInvocationHandler implements InvocationHandler {
    private final ModifiableRootModel myDelegateModel;
    @NonNls private final Set<String> myCheckedNames = new HashSet<String>(
      Arrays.asList(
        new String[]{"addOrderEntry", "addLibraryEntry", "addInvalidLibrary", "addModuleOrderEntry", "addInvalidModuleEntry",
                     "removeOrderEntry", "setJdk", "inheritJdk", "inheritCompilerOutputPath", "setExcludeOutput"}
      ));

    ModifiableRootModelInvocationHandler(ModifiableRootModel model) {
      myDelegateModel = model;
    }

    public Object invoke(Object object, Method method, Object[] params) throws Throwable {
      final boolean needUpdate = myCheckedNames.contains(method.getName());
      try {
        final Object result = method.invoke(myDelegateModel, unwrapParams(params));
        if (result instanceof LibraryTable) {
          return Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{LibraryTable.class},
                                        new LibraryTableInvocationHandler((LibraryTable)result));
        }
        return result;
      }
      finally {
        if (needUpdate) {
          updateOrderEntriesInEditors();
        }
      }
    }
  }

  private class LibraryTableInvocationHandler implements InvocationHandler {
    private final LibraryTable myDelegateTable;
    @NonNls private final Set<String> myCheckedNames = new HashSet<String>(Arrays.asList(new String[]{"removeLibrary", /*"createLibrary"*/}));

    LibraryTableInvocationHandler(LibraryTable table) {
      myDelegateTable = table;
    }

    public Object invoke(Object object, Method method, Object[] params) throws Throwable {
      final boolean needUpdate = myCheckedNames.contains(method.getName());
      try {
        final Object result = method.invoke(myDelegateTable, unwrapParams(params));
        if (result instanceof Library) {
          return Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{Library.class},
                                        new LibraryInvocationHandler((Library)result));
        }
        else if (result instanceof LibraryTable.ModifiableModel) {
          return Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{LibraryTable.ModifiableModel.class},
                                        new LibraryTableModelInvocationHandler((LibraryTable.ModifiableModel)result));
        }
        if (result instanceof Library[]) {
          Library[] libraries = (Library[])result;
          for (int idx = 0; idx < libraries.length; idx++) {
            Library library = libraries[idx];
            libraries[idx] =
            (Library)Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{Library.class},
                                            new LibraryInvocationHandler(library));
          }
        }
        return result;
      }
      finally {
        if (needUpdate) {
          updateOrderEntriesInEditors();
        }
      }
    }

  }

  private class LibraryInvocationHandler implements InvocationHandler, ProxyDelegateAccessor {
    private final Library myDelegateLibrary;

    LibraryInvocationHandler(Library delegateLibrary) {
      myDelegateLibrary = delegateLibrary;
    }

    public Object invoke(Object object, Method method, Object[] params) throws Throwable {
      final Object result = method.invoke(myDelegateLibrary, unwrapParams(params));
      if (result instanceof Library.ModifiableModel) {
        return Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{Library.ModifiableModel.class},
                                      new LibraryModifiableModelInvocationHandler((Library.ModifiableModel)result));
      }
      return result;
    }

    public Object getDelegate() {
      return myDelegateLibrary;
    }
  }

  private class LibraryModifiableModelInvocationHandler implements InvocationHandler {
    private final Library.ModifiableModel myDelegateModel;

    LibraryModifiableModelInvocationHandler(Library.ModifiableModel delegateModel) {
      myDelegateModel = delegateModel;
    }

    public Object invoke(Object object, Method method, Object[] params) throws Throwable {
      final boolean needUpdate = METHOD_COMMIT.equals(method.getName());
      try {
        return method.invoke(myDelegateModel, unwrapParams(params));
      }
      finally {
        if (needUpdate) {
          updateOrderEntriesInEditors();
        }
      }
    }
  }

  private class LibraryTableModelInvocationHandler implements InvocationHandler {
    private final LibraryTable.ModifiableModel myDelegateModel;

    LibraryTableModelInvocationHandler(LibraryTable.ModifiableModel delegateModel) {
      myDelegateModel = delegateModel;
    }

    public Object invoke(Object object, Method method, Object[] params) throws Throwable {
      final boolean needUpdate = METHOD_COMMIT.equals(method.getName());
      try {
        Object result = method.invoke(myDelegateModel, unwrapParams(params));
        if (result instanceof Library[]) {
          Library[] libraries = (Library[])result;
          for (int idx = 0; idx < libraries.length; idx++) {
            Library library = libraries[idx];
            libraries[idx] =
            (Library)Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{Library.class},
                                            new LibraryInvocationHandler(library));
          }
        }
        if (result instanceof Library) {
          result =
          Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{Library.class},
                                 new LibraryInvocationHandler((Library)result));
        }
        return result;
      }
      finally {
        if (needUpdate) {
          updateOrderEntriesInEditors();
        }
      }
    }
  }

  public static interface ProxyDelegateAccessor {
    Object getDelegate();
  }

  private static Object[] unwrapParams(Object[] params) {
    if (params == null || params.length == 0) {
      return params;
    }
    final Object[] unwrappedParams = new Object[params.length];
    for (int idx = 0; idx < params.length; idx++) {
      Object param = params[idx];
      if (param != null && Proxy.isProxyClass(param.getClass())) {
        final InvocationHandler invocationHandler = Proxy.getInvocationHandler(param);
        if (invocationHandler instanceof ProxyDelegateAccessor) {
          param = ((ProxyDelegateAccessor)invocationHandler).getDelegate();
        }
      }
      unwrappedParams[idx] = param;
    }
    return unwrappedParams;
  }

  public String getHelpTopic() {
    if (myTabbedPane == null || myEditors.isEmpty()) {
      return null;
    }
    final ModuleConfigurationEditor moduleElementsEditor = myEditors.get(myTabbedPane.getSelectedIndex());
    return moduleElementsEditor.getHelpTopic();
  }

  public void setModuleName(final String name) {
    myName = name;
  }

  private class ModuleEditorPanel extends JPanel implements DataProvider{
    public ModuleEditorPanel() {
      super(new BorderLayout());
    }

    public Object getData(String dataId) {
      if (dataId.equals(DataConstantsEx.MODULE_CONTEXT)) {
        return getModule();
      }
      return null;
    }

  }

  abstract class ViewItem {
    String myText;
    Icon myIcon;

    public ViewItem(final String text, final Icon icon) {
      myText = text;
      myIcon = icon;
    }

    abstract PopupStep getStep();

    Icon getIcon() {
      return myIcon;
    }

    abstract String getKey();

  }

  class ManageFacets extends ViewItem {
    public ManageFacets() {
      super("Manage Facets...", new EmptyIcon(16));
    }

    PopupStep getStep() {
      return new BaseListPopupStep(myText, new Object[0]) {
        public PopupStep onChosen(final Object selectedValue, final boolean finalChoice) {
          return FINAL_CHOICE;
        }
      };
    }

    String getKey() {
      return null;
    }
  }

  abstract class SwitchView extends ViewItem {

    String myKey;
    private boolean myShowModuleName;

    protected SwitchView(String key, final String text, final Icon icon, boolean showModuleName) {
      super(text, icon);
      myKey = key;
      myShowModuleName = showModuleName;
    }


    abstract JComponent getComponent();

    PopupStep getStep() {
      return new BaseListPopupStep(myText, new Object[0]) {
        public PopupStep onChosen(final Object selectedValue, final boolean finalChoice) {
          setSelectedView(SwitchView.this, myShowModuleName);
          return PopupStep.FINAL_CHOICE;
        }
      };
    }

    void switchTo() {
      getStep().onChosen(myText, true);
    }

    String getKey() {
      return myKey;
    }

    public void queryPlace(Place place) {
      place.putPath(MODULE_VIEW, myKey);
    }

    public void navigateTo(final Place place) {
      switchTo();
    }
  }

  class GeneralView extends SwitchView {
    public GeneralView() {
      super(GENERAL_VIEW, "General Module Settings", IconLoader.getIcon("/fileTypes/java.png"), true);
    }

    JComponent getComponent() {
      return myGenericSettingsPanel;
    }

    public void queryPlace(final Place place) {
      super.queryPlace(place);
      place.putPath("module.view.general.tab", getSelectedTabName());
    }

    public void navigateTo(final Place place) {
      super.navigateTo(place);
      setSelectedTabName((String)place.getPath("module.view.general.tab"));
    }
  }

  class FacetView extends SwitchView {

    private Facet myFacet;

    public FacetView(Facet facet) {
      super(facet.getType().getStringId(), myFacetEditorFacade.getFacetConfigurator().getOrCreateModifiableModel(getModule()).getFacetName(facet), facet.getType().getIcon(), false);
      myFacet = facet;
    }

    public void queryPlace(final Place place) {
      super.queryPlace(place);
      Place.queryFurther(getEditorComponent(), place);
    }

    public void navigateTo(final Place place) {
      super.navigateTo(place);
      Place.goFurther(getEditorComponent(), place);
    }

    JComponent getComponent() {
      return getEditorComponent();
    }

    private JComponent getEditorComponent() {
      return myFacetEditorFacade.getFacetConfigurator().getOrCreateEditor(myFacet).getComponent();
    }
  }
}
