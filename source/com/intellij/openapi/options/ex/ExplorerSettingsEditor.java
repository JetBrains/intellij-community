package com.intellij.openapi.options.ex;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.ShowSettingsUtilImpl;
import com.intellij.ide.ui.search.DefaultSearchableConfigurable;
import com.intellij.ide.ui.search.SearchUtil;
import com.intellij.ide.ui.search.SearchableOptionsRegistrar;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.options.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Map;
import java.util.Set;

public class ExplorerSettingsEditor extends DialogWrapper {
  /** When you visit the same editor next time you see the same selected configurable. */
  private static final TObjectIntHashMap<String> ourGroup2LastConfigurableIndex = new TObjectIntHashMap<String>();
  private static String ourLastGroup;

  private final Project myProject;
  private int myKeySelectedConfigurableIndex;

  private final ConfigurableGroup[] myGroups;

  /** Configurable which is currently selected. */
  private Configurable mySelectedConfigurable;
  private ConfigurableGroup mySelectedGroup;
  private JPanel myOptionsPanel;

  private final Map<Configurable, JComponent> myInitializedConfigurables2Component;
  private final Dimension myPreferredSize;
  private final Map<Configurable, Dimension> myConfigurable2PrefSize;
  private JButton myHelpButton;
  private JPanel myComponentPanel;
  private SearchUtil.SearchTextField mySearchField;
  private Set<Configurable> myOptionContainers = null;
  private Alarm myShowHintAlarm = new Alarm();
  private JTree myTree;
  @NonNls final DefaultMutableTreeNode myRoot = new DefaultMutableTreeNode("Root");
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.options.ex.ExplorerSettingsEditor");

  private JBPopup [] myPopup = new JBPopup[1];
  public ExplorerSettingsEditor(Project project, ConfigurableGroup[] group) {
    super(project, true);
    myProject = project;
    myPreferredSize = new Dimension(800, 600);
    myGroups = group;

    if (myGroups.length == 0) {
      throw new IllegalStateException("number of configurables must be more then zero");
    }

    myInitializedConfigurables2Component = new HashMap<Configurable, JComponent>();
    myConfigurable2PrefSize = new HashMap<Configurable, Dimension>();

    init();
  }

  protected String getDimensionServiceKey() {
    return "#com.intellij.openapi.options.ex.ExplorerSettingsEditor";
  }

  protected final void init() {
    super.init();

    int lastGroup = 0;
    for (int i = 0; i < myGroups.length; i++) {
      ConfigurableGroup group = myGroups[i];
      if (Comparing.equal(group.getShortName(), ourLastGroup)) {
        lastGroup = i;
        break;
      }
    }

    selectGroup(lastGroup);
  }

  private void selectGroup(int groupIdx) {
    final String shortName = myGroups[groupIdx].getShortName();
    int lastIndex = ourGroup2LastConfigurableIndex.get(shortName);
    if (lastIndex == -1) lastIndex = 0;
    selectGroup(groupIdx,lastIndex);
  }
  private void selectGroup(int groupIdx, int indexToSelect) {
    rememberLastUsedPage();

    mySelectedGroup = myGroups[groupIdx];
    ourLastGroup = mySelectedGroup.getShortName();

    final DefaultMutableTreeNode groupNode = (DefaultMutableTreeNode)myRoot.getChildAt(groupIdx);
    myTree.expandPath(new TreePath(groupNode.getPath()));
    TreeUtil.selectNode(myTree, groupNode.getChildAt(indexToSelect));

    Configurable[] configurables = mySelectedGroup.getConfigurables();
    Configurable toSelect = configurables[indexToSelect];

    selectConfigurable(toSelect, indexToSelect);

    requestFocusForMainPanel();
  }

  private void rememberLastUsedPage() {
    if (mySelectedGroup != null) {
      Configurable[] configurables = mySelectedGroup.getConfigurables();
      int index = -1;
      for (int i = 0; i < configurables.length; i++) {
        Configurable configurable = configurables[i];
        if (configurable == mySelectedConfigurable) {
          index = i;
          break;
        }
      }
      ourGroup2LastConfigurableIndex.put(mySelectedGroup.getShortName(), index);
    }
  }

  private void updateTitle() {
    if (mySelectedConfigurable == null) {
      setTitle(OptionsBundle.message("settings.panel.title"));
    }
    else {
      String displayName = mySelectedConfigurable.getDisplayName();
      setTitle(mySelectedGroup.getDisplayName() + " - " + (displayName != null ? displayName.replace('\n', ' ') : ""));
      myHelpButton.setEnabled(mySelectedConfigurable.getHelpTopic() != null);
    }
  }

  /**
   * @return false if failed
   */
  protected boolean apply() {
    if (mySelectedConfigurable == null || !mySelectedConfigurable.isModified()) {
      return true;
    }

    try {
      mySelectedConfigurable.apply();
      return true;
    }
    catch (ConfigurationException e) {
      if (e.getMessage() != null) {
        Messages.showMessageDialog(e.getMessage(), e.getTitle(), Messages.getErrorIcon());
      }
      return false;
    }
  }

  public final void dispose() {
    if (myPopup[0] != null) {
      myPopup[0].cancel();
    }
    myAlarm.cancelAllRequests();
    rememberLastUsedPage();

    for (ConfigurableGroup myGroup : myGroups) {
      Configurable[] configurables = myGroup.getConfigurables();
      for (Configurable configurable : configurables) {
        if (myInitializedConfigurables2Component.containsKey(configurable)){ //do not dispose resources if components weren't initialized
          configurable.disposeUIResources();
        }
      }
    }
    myInitializedConfigurables2Component.clear();
    super.dispose();
  }

  public JComponent getPreferredFocusedComponent() {
    return myComponentPanel;
  }

  protected final JComponent createCenterPanel() {
    myComponentPanel = new JPanel(new BorderLayout());

    // myOptionPanel contains all configurables. When it updates its UI we also need to update
    // UIs of all created but not currently visible configurables.

    myOptionsPanel = new JPanel(new BorderLayout()) {
      public void updateUI() {
        super.updateUI();
        for (Configurable configurable : myInitializedConfigurables2Component.keySet()) {
          if (configurable.equals(mySelectedConfigurable)) { // don't update visible component (optimization)
            continue;
          }
          JComponent component = myInitializedConfigurables2Component.get(configurable);
          SwingUtilities.updateComponentTreeUI(component);
        }
      }
    };

    initTree();
    initToolbar();
    final JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myTree);
    scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

    TreeUtil.expandAll(myTree);
    final Dimension preferredSize = new Dimension(myTree.getPreferredSize().width + 20,
                                                  scrollPane.getPreferredSize().height);
    scrollPane.setPreferredSize(preferredSize);
    scrollPane.setMinimumSize(preferredSize);
    TreeUtil.collapseAll(myTree, 1);

    final JPanel leftPane = new JPanel(new BorderLayout());
    leftPane.setBorder(BorderFactory.createRaisedBevelBorder());
    leftPane.add(scrollPane, BorderLayout.CENTER);
    myComponentPanel.add(leftPane, BorderLayout.WEST);

    myOptionsPanel.setBorder(BorderFactory.createEmptyBorder(15, 5, 2, 5));
    myComponentPanel.add(myOptionsPanel, BorderLayout.CENTER);

    myOptionsPanel.setPreferredSize(myPreferredSize);

    myComponentPanel.setFocusable(true);
    myComponentPanel.addKeyListener(new KeyAdapter() {
      public void keyPressed(KeyEvent e) {
        Configurable[] configurables = mySelectedGroup.getConfigurables();
        int index = myKeySelectedConfigurableIndex;
        if (index == -1) return;
        int keyCode = e.getKeyCode();
        if (keyCode == KeyEvent.VK_UP) {
          index--;
          if (index == -1){
            final int groupIdx = ArrayUtil.find(myGroups, mySelectedGroup);
            if (groupIdx > 0){
              selectGroup(groupIdx - 1, myGroups[groupIdx - 1].getConfigurables().length - 1);
              return;
            }
          }
        }
        else if (keyCode == KeyEvent.VK_DOWN) {
          index++;
          if (index == configurables.length){
            final int groupIdx = ArrayUtil.find(myGroups, mySelectedGroup);
            if (groupIdx < myGroups.length - 1){
              selectGroup(groupIdx + 1, 0);
              return;
            }
          }
        }
        else {
          Configurable configurableFromMnemonic = ControlPanelMnemonicsUtil.getConfigurableFromMnemonic(e, myGroups);
          if (configurableFromMnemonic == null) return;
          int keyGroupIndex = -1;
          ConfigurableGroup keyGroup = null;
          int keyIndexInGroup = 0;
          for (int i = 0; i < myGroups.length; i++) {
            ConfigurableGroup group = myGroups[i];
            int ingroupIdx = ArrayUtil.find(group.getConfigurables(), configurableFromMnemonic);
            if (ingroupIdx != -1) {
              keyGroupIndex = i;
              keyGroup = group;
              keyIndexInGroup = ingroupIdx;
              break;
            }
          }
          if (mySelectedGroup != keyGroup) {
            selectGroup(keyGroupIndex, keyIndexInGroup);
            return;
          }
          index = ControlPanelMnemonicsUtil.getIndexFromKeycode(keyCode, mySelectedGroup == myGroups[0]);
        }
        if (index == -1 || index >= configurables.length) return;
        final TreeNode groupNode = myRoot.getChildAt(ArrayUtil.find(myGroups, mySelectedGroup));
        TreeUtil.selectPath(myTree, new TreePath(new TreeNode[]{ myRoot, groupNode, groupNode.getChildAt(index)}));
      }
    });
    return myComponentPanel;
  }

  private void initTree() {
    myTree = new JTree(myRoot){
      public Dimension getPreferredScrollableViewportSize() {
        Dimension size = super.getPreferredScrollableViewportSize();
        size = new Dimension(size.width + 10, size.height);
        return size;
      }
    };
    //noinspection NonStaticInitializer
    myTree.setCellRenderer(new ColoredTreeCellRenderer() {
      {
        setFocusBorderAroundIcon(true);
      }
      public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        if (value instanceof DefaultMutableTreeNode){
          Object userObject = ((DefaultMutableTreeNode)value).getUserObject();
          if (userObject instanceof Pair){
            final Pair configurableWithMnemonics = ((Pair)userObject);
            final Configurable configurable = (Configurable)configurableWithMnemonics.first;
            setIcon(configurable.getIcon());
            append(configurable.getDisplayName().replaceAll("\n", " "), SimpleTextAttributes.REGULAR_ATTRIBUTES);
            append(" ( " + configurableWithMnemonics.second + " )", SimpleTextAttributes.GRAYED_ATTRIBUTES);
          } else if (userObject instanceof String){
            setIcon(null);
            append((String)userObject, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
          }
        }
      }
    });

    myTree.addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        final Object node = myTree.getLastSelectedPathComponent();
        if (node instanceof DefaultMutableTreeNode) {
          final DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)node;
          final Object userObject = treeNode.getUserObject();
          if (userObject instanceof Pair){
            final Pair configurableWithMnemonic = (Pair)userObject;
            final Configurable configurable = (Configurable)configurableWithMnemonic.first;
            final TreeNode[] nodes = treeNode.getPath();
            LOG.assertTrue(nodes != null && nodes.length > 0 && nodes[1] != null);
            final int groupIdx = myRoot.getIndex(nodes[1]);
            selectConfigurableLater(configurable, ArrayUtil.find(myGroups[groupIdx].getConfigurables(), configurable));
            rememberLastUsedPage();
            mySelectedGroup = myGroups[groupIdx];
            ourLastGroup = mySelectedGroup.getShortName();
          }
        }
      }
    });
    myTree.setRowHeight(32);
    TreeUtil.installActions(myTree);
    UIUtil.setLineStyleAngled(myTree);
    myTree.setShowsRootHandles(true);
    myTree.setRootVisible(false);
  }

  protected JComponent createNorthPanel() {
    final JPanel panel = new JPanel(new GridBagLayout());
    mySearchField = new SearchUtil.SearchTextField();
    mySearchField.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        final SearchableOptionsRegistrar optionsRegistrar = SearchableOptionsRegistrar.getInstance();
        final @NonNls String searchPattern = mySearchField.getText();
        if (searchPattern != null && searchPattern.length() > 0) {
          myOptionContainers = optionsRegistrar.getConfigurables(myGroups, searchPattern, CodeStyleSettingsManager.getInstance(myProject).USE_PER_PROJECT_SETTINGS);
        } else {
          myOptionContainers = null;
        }
        myShowHintAlarm.cancelAllRequests();
        myShowHintAlarm.addRequest(new Runnable() {
          public void run() {
            SearchUtil.showHintPopup(mySearchField,
                                     optionsRegistrar,
                                     myPopup,
                                     myShowHintAlarm,
                                     myProject);
          }
        }, 300, ModalityState.defaultModalityState());
        initToolbar();
        TreeUtil.expandAll(myTree);
        if (mySelectedConfigurable instanceof DefaultSearchableConfigurable){
          selectOption((DefaultSearchableConfigurable)mySelectedConfigurable);
        }
        myComponentPanel.revalidate();
        myComponentPanel.repaint();
      }
    });
    final GridBagConstraints gc = new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.EAST, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0);
    panel.add(Box.createHorizontalBox(), gc);

    gc.gridx++;
    gc.weightx = 0;
    gc.fill = GridBagConstraints.NONE;
    panel.add(new JLabel(IdeBundle.message("search.textfield.title")), gc);

    gc.gridx++;
    final int height = mySearchField.getPreferredSize().height;
    mySearchField.setPreferredSize(new Dimension(100, height));
    panel.add(mySearchField, gc);

    return panel;
  }

  private void requestFocusForMainPanel() {
    myComponentPanel.requestFocus();
  }

  private void initToolbar() {
    myRoot.removeAllChildren();
    char mnemonicStartChar = '1';
    for (ConfigurableGroup group : myGroups) {
      DefaultMutableTreeNode groupNode = new DefaultMutableTreeNode(group.getDisplayName());
      final Configurable[] configurables = group.getConfigurables();
      for (int i = 0; i < configurables.length; i++){
        Configurable configurable = configurables[i];
        if (myOptionContainers == null || myOptionContainers.contains(configurable)) {
          groupNode.add(new DefaultMutableTreeNode(Pair.create(configurable, (char)(mnemonicStartChar + i))));
        }
      }
      mnemonicStartChar = 'A';
      myRoot.add(groupNode);
    }
    ((DefaultTreeModel)myTree.getModel()).reload();
  }

  private final Alarm myAlarm = new Alarm();
  private void selectConfigurableLater(final Configurable configurable, final int index) {
    myAlarm.cancelAllRequests();
    myAlarm.addRequest(new Runnable() {
      public void run() {
        selectConfigurable(configurable, index);
      }
    }, 400);
    myKeySelectedConfigurableIndex = index;

    myComponentPanel.repaint();
  }

  /**
   * Selects configurable with specified <code>class</code>. If there is no configurable of <code>class</code>
   * then the method does nothing.
   */
  private void selectConfigurable(Configurable configurable, int index) {
    // If nothing to be selected then clear panel with configurable's options.
    if (configurable == null) {
      mySelectedConfigurable = null;
      myKeySelectedConfigurableIndex = 0;
      updateTitle();
      myOptionsPanel.removeAll();
      validate();
      repaint();
      return;
    }

    // Save changes if any
    Dimension currentOptionsSize = myOptionsPanel.getSize();

    if (mySelectedConfigurable != null && mySelectedConfigurable.isModified()) {
      int exitCode = Messages.showYesNoDialog(OptionsBundle.message("options.page.modified.save.message.text"),
                                              OptionsBundle.message("options.save.changes.message.title"),
                                              Messages.getQuestionIcon());
      if (exitCode == 0) {
        try {
          mySelectedConfigurable.apply();
        }
        catch (ConfigurationException exc) {
          if (exc.getMessage() != null) {
            Messages.showMessageDialog(exc.getMessage(), exc.getTitle(), Messages.getErrorIcon());
          }
          return;
        }
      }
    }

    if (mySelectedConfigurable != null) {
      Dimension savedPrefferedSize = myConfigurable2PrefSize.get(mySelectedConfigurable);
      if (savedPrefferedSize != null) {
        myConfigurable2PrefSize.put(mySelectedConfigurable, new Dimension(currentOptionsSize));
      }
    }

    // Show new configurable
    myComponentPanel.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

    myOptionsPanel.removeAll();

    mySelectedConfigurable = configurable;
    myKeySelectedConfigurableIndex = index;
    JComponent component = myInitializedConfigurables2Component.get(configurable);
    if (component == null) {
      if (configurable instanceof SearchableConfigurable){
        configurable = new DefaultSearchableConfigurable((SearchableConfigurable)configurable);
      }
      component = configurable.createComponent();
      myInitializedConfigurables2Component.put(configurable, component);
    }

    Dimension compPrefSize;
    if (myConfigurable2PrefSize.containsKey(configurable)) {
      compPrefSize = myConfigurable2PrefSize.get(configurable);
    }
    else {
      compPrefSize = component.getPreferredSize();
      myConfigurable2PrefSize.put(configurable, compPrefSize);
    }
    int widthDelta = Math.max(compPrefSize.width - currentOptionsSize.width, 0);
    int heightDelta = Math.max(compPrefSize.height - currentOptionsSize.height, 0);
    myOptionsPanel.add(component, BorderLayout.CENTER);
    if (widthDelta > 0 || heightDelta > 0) {
      setSize(getSize().width + widthDelta, getSize().height + heightDelta);
      centerRelativeToParent();
    }

    configurable.reset();

    updateTitle();
    validate();
    repaint();

    requestFocusForMainPanel();
    myComponentPanel.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
    if (configurable instanceof DefaultSearchableConfigurable){
      selectOption((DefaultSearchableConfigurable)configurable);
    }
  }

  private void selectOption(final DefaultSearchableConfigurable searchableConfigurable) {
    searchableConfigurable.clearSearch();
    if (myOptionContainers == null || myOptionContainers.isEmpty()) return; //do not highlight current editor when nothing can be selected
    @NonNls final String filter = mySearchField.getText();
    if (filter != null && filter.length() > 0 ){
      searchableConfigurable.enableSearch(filter);
    }
  }

  protected final Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), new ApplyAction(), getHelpAction()};
  }

  protected JButton createJButtonForAction(Action action) {
    JButton button = super.createJButtonForAction(action);
    if (action == getHelpAction()) {
      myHelpButton = button;
    }
    return button;
  }

  protected Action[] createLeftSideActions() {
    return new Action[]{new SwitchToDefaultViewAction()};
  }

  protected final void doOKAction() {
    boolean ok = apply();
    if (ok) {
      super.doOKAction();
    }
  }

  protected final void doHelpAction() {
    if (mySelectedConfigurable != null) {
      String helpTopic = mySelectedConfigurable.getHelpTopic();
      if (helpTopic != null) {
        HelpManager.getInstance().invokeHelp(helpTopic);
      }
    }
  }

  private final class ApplyAction extends AbstractAction {
    private Alarm myUpdateAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);

    public ApplyAction() {
      super(OptionsBundle.message("options.apply.button"));
      final Runnable updateRequest = new Runnable() {
        public void run() {
          if (!ExplorerSettingsEditor.this.isShowing()) return;
          ApplyAction.this.setEnabled(mySelectedConfigurable != null && mySelectedConfigurable.isModified());
          addUpdateRequest(this);
        }
      };

      addUpdateRequest(updateRequest);
    }

    private void addUpdateRequest(final Runnable updateRequest) {
      myUpdateAlarm.addRequest(updateRequest, 500, ModalityState.stateForComponent(getWindow()));
    }

    public void actionPerformed(ActionEvent e) {
      if (apply()) {
        setCancelButtonText(CommonBundle.getCloseButtonText());
      }
    }
  }

  private class SwitchToDefaultViewAction extends AbstractAction {
    public SwitchToDefaultViewAction() {
      putValue(Action.NAME, OptionsBundle.message("explorer.panel.default.view.button"));
    }

    public void actionPerformed(ActionEvent e) {
      switchToDefaultView(null);
    }
  }
  private void switchToDefaultView(final Configurable preselectedConfigurable) {
    close(OK_EXIT_CODE);

    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        ((ShowSettingsUtilImpl)ShowSettingsUtil.getInstance()).showControlPanelOptions(myProject, myGroups, preselectedConfigurable);
      }
    }, ModalityState.NON_MMODAL);
  }
}