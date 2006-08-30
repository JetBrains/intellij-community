package com.intellij.openapi.roots.ui.configuration;

import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.javaee.JavaeeModuleProperties;
import com.intellij.javaee.module.ModuleContainer;
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
import com.intellij.ui.TabbedPaneWrapper;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NonNls;
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
public class ModuleEditor {
  private final Project myProject;
  private JPanel myPanel;
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

  @Nullable
  public ModuleBuilder getModuleBuilder() {
    return myModuleBuilder;
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
    ModuleConfigurationState state = new ModuleConfigurationStateImpl(myProject, module, myModulesProvider, myModifiableRootModelProxy);
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

  private void processEditorsProvider(final ModuleConfigurationEditorProvider provider, final ModuleConfigurationState state) {
    final ModuleConfigurationEditor[] editors = provider.createEditors(state);
    myEditors.addAll(Arrays.asList(editors));
  }

  private JPanel createPanel() {
    getModifiableRootModel(); //initialize model if needed
    getModifiableRootModelProxy();

    myPanel = new ModuleEditorPanel();

    createEditors(getModule());

    JPanel northPanel = new JPanel(new GridBagLayout());

    myPanel.add(northPanel, BorderLayout.NORTH);

    myTabbedPane = new TabbedPaneWrapper();
    for (ModuleConfigurationEditor editor : myEditors) {
      myTabbedPane.addTab(editor.getDisplayName(), editor.getIcon(), editor.createComponent(), null);
      editor.reset();
    }
    setSelectedTabName(ourSelectedTabName);

    myPanel.add(myTabbedPane.getComponent(), BorderLayout.CENTER);
    myTabbedPane.addChangeListener(new javax.swing.event.ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        ourSelectedTabName = getSelectedTabName();
      }
    });

    return myPanel;
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
    if (myPanel == null) {
      myPanel = createPanel();
    }
    return myPanel;
  }

  public void moduleCountChanged(int oldCount, int newCount) {
    updateOrderEntriesInEditors();
  }

  public void updateOrderEntriesInEditors() {
    for (final ModuleConfigurationEditor myEditor : myEditors) {
      myEditor.moduleStateChanged();
    }
    myEventDispatcher.getMulticaster().moduleStateChanged(getModifiableRootModelProxy());
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


      myPanel = null;
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

    if (myModifiableRootModel != null) {
      final Module module = myModifiableRootModel.getModule();
      if (module.getModuleType().isJ2EE()) {
        final JavaeeModuleProperties properties = JavaeeModuleProperties.getInstance(module);
        if (properties != null) {
          final ModuleContainer modifiableModel = properties.getModifiableModel();
          if (modifiableModel instanceof JavaeeModuleProperties && !((JavaeeModuleProperties)modifiableModel).isDisposed()) { //start edit was call
            properties.commit(myModifiableRootModel);
          }
        }
      }
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

  public void setSelectedTabName(String name) {
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
}
