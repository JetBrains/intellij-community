package com.intellij.openapi.options.ex;

import com.intellij.ide.actions.ShowSettingsUtilImpl;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionButtonLook;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ex.ActionToolbarEx;
import com.intellij.ui.HorizontalLabeledIcon;
import com.intellij.ui.TabbedPaneWrapper;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashMap;
import gnu.trove.TObjectIntHashMap;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Iterator;
import java.util.Map;

public class ExplorerSettingsEditor extends DialogWrapper {
  /** When you visit the same editor next time you see the same selected configurable. */
  private static final TObjectIntHashMap<String> ourGroup2LastConfigurableIndex = new TObjectIntHashMap<String>();
  private static String ourLastGroup;

  private final Project myProject;
  private Configurable myKeySelectedConfigurable;
  private int myKeySelectedConfigurableIndex;

  private final ConfigurableGroup[] myGroups;

  /** Configurable which is currently selected. */
  private Configurable mySelectedConfigurable;
  private ConfigurableGroup mySelectedGroup;
  private JPanel myOptionsPanel;

  private final Map<Configurable, JComponent> myInitializedConfigurables2Component;
  private final Dimension myPreferredSize;
  private final Map<Configurable, Dimension> myConfigurable2PrefSize;
  private TabbedPaneWrapper myGroupTabs;
  private JButton myHelpButton;
  private JPanel myComponentPanel;

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
    Configurable[] configurables = mySelectedGroup.getConfigurables();
    Configurable toSelect = configurables[indexToSelect];
    myGroupTabs.setSelectedIndex(groupIdx);

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
      setTitle("Settings");
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

  protected final void dispose() {
    rememberLastUsedPage();

    for (int i = 0; i < myGroups.length; i++) {
      Configurable[] configurables = myGroups[i].getConfigurables();
      for (int j = 0; j < configurables.length; j++) {
        Configurable configurable = configurables[j];
        configurable.disposeUIResources();
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
        for (Iterator<Configurable> i = myInitializedConfigurables2Component.keySet().iterator(); i.hasNext();) {
          Configurable configurable = i.next();
          if (configurable.equals(mySelectedConfigurable)) { // don't update visible component (optimization)
            continue;
          }
          JComponent component = myInitializedConfigurables2Component.get(configurable);
          SwingUtilities.updateComponentTreeUI(component);
        }
      }
    };

    JPanel compoundToolbarPanel = new JPanel(new BorderLayout());

    myGroupTabs = new TabbedPaneWrapper();

    for (int i = 0; i < myGroups.length; i++) {
      final ConfigurableGroup group = myGroups[i];
      JComponent toolbar = createGroupToolbar(group, i == 0 ? '1' : 'A');
      myGroupTabs.addTab(group.getShortName(), toolbar);
    }
    myGroupTabs.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        int selectedIndex = myGroupTabs.getSelectedIndex();
        if (selectedIndex >= 0) {
          selectGroup(selectedIndex);
        }
      }
    });

    compoundToolbarPanel.add(myGroupTabs.getComponent(), BorderLayout.CENTER);

    myComponentPanel.setBorder(BorderFactory.createRaisedBevelBorder());
    myComponentPanel.add(compoundToolbarPanel, BorderLayout.WEST);

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
        }
        else if (keyCode == KeyEvent.VK_DOWN) {
          index++;
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
        if (index == -1 || index == configurables.length) return;
        selectConfigurableLater(configurables[index], index);
      }
    });
    return myComponentPanel;
  }


  private void requestFocusForMainPanel() {
    myComponentPanel.requestFocus();
  }

  private JComponent createGroupToolbar(ConfigurableGroup group, char mnemonicStartChar) {
    final Configurable[] configurables = group.getConfigurables();
    DefaultActionGroup actionGroup = new DefaultActionGroup();
    for (int i = 0; i < configurables.length; i++) {
      Configurable configurable = configurables[i];
      actionGroup.add(new MySelectConfigurableAction(configurable, i, (char)(mnemonicStartChar + i)));
    }

    final ActionToolbarEx toolbar = (ActionToolbarEx)ActionManager.getInstance().createActionToolbar(
      ActionPlaces.UNKNOWN,
      actionGroup,
      false);

    toolbar.adjustTheSameSize(true);

    toolbar.setButtonLook(new LeftAlignedIconButtonLook());

    JPanel toolbarPanel = new JPanel(new BorderLayout(2, 0));
    toolbarPanel.add(toolbar.getComponent(), BorderLayout.CENTER);

    JScrollPane scrollPane = new JScrollPane(toolbarPanel, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                                             JScrollPane.HORIZONTAL_SCROLLBAR_NEVER) {
      public Dimension getPreferredSize() {
        return new Dimension(
          toolbar.getComponent().getPreferredSize().width + getVerticalScrollBar().getPreferredSize().width + 5, 5);
      }

      public Dimension getMinimumSize() {
        return getPreferredSize();
      }
    };
    scrollPane.getVerticalScrollBar().setUnitIncrement(toolbar.getMaxButtonHeight());
    return scrollPane;
  }

  private final Alarm myAlarm = new Alarm();
  private void selectConfigurableLater(final Configurable configurable, final int index) {
    myAlarm.cancelAllRequests();
    myAlarm.addRequest(new Runnable() {
      public void run() {
        selectConfigurable(configurable, index);
      }
    }, 400);
    myKeySelectedConfigurable = configurable;
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
      myKeySelectedConfigurable = null;
      updateTitle();
      myOptionsPanel.removeAll();
      validate();
      repaint();
      return;
    }

    // Save changes if any
    Dimension currentOptionsSize = myOptionsPanel.getSize();

    if (mySelectedConfigurable != null && mySelectedConfigurable.isModified()) {
      int exitCode = Messages.showYesNoDialog("The page has been modified. Save changes made on this page?",
                                              "Save Changes",
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
    myKeySelectedConfigurable = configurable;
    myKeySelectedConfigurableIndex = index;
    JComponent component = myInitializedConfigurables2Component.get(configurable);
    if (component == null) {
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
      super("A&pply");
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
        setCancelButtonText("Close");
      }
    }
  }

  private final class MySelectConfigurableAction extends ToggleAction {
    private final Configurable myConfigurable;
    private final int myIndex;

    private MySelectConfigurableAction(Configurable configurable, int index, char mnemonic) {
      myConfigurable = configurable;
      myIndex = index;
      Presentation presentation = getTemplatePresentation();
      String displayName = myConfigurable.getDisplayName();
      Icon icon = myConfigurable.getIcon();
      if (icon == null) {
        icon = IconLoader.getIcon("/general/configurableDefault.png");
      }
      Icon labeledIcon = new HorizontalLabeledIcon(icon, displayName, " ("+mnemonic+")");
      presentation.setIcon(labeledIcon);
      presentation.setText(null);
    }

    public boolean isSelected(AnActionEvent e) {
      return myConfigurable.equals(myKeySelectedConfigurable);
    }

    public void setSelected(AnActionEvent e, boolean state) {
      if (state) {
        selectConfigurableLater(myConfigurable, myIndex);
      }
    }
  }

  private class SwitchToDefaultViewAction extends AbstractAction {
    public SwitchToDefaultViewAction() {
      putValue(Action.NAME, "Default &View");
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

  private static class LeftAlignedIconButtonLook extends ActionButtonLook {
    private ActionButtonLook myDelegate = ActionButtonLook.IDEA_LOOK;

    public void paintIconAt(Graphics g, ActionButtonComponent button, Icon icon, int x, int y) {
      myDelegate.paintIconAt(g, button, icon, x, y);
    }

    public void paintBorder(Graphics g, JComponent component, int state) {
      myDelegate.paintBorder(g, component, state);
    }

    public void paintIcon(Graphics g, ActionButtonComponent actionButton, Icon icon) {
      int height = icon.getIconHeight();
      int x = 2;
      int y = (int)Math.ceil((actionButton.getHeight() - height) / 2);
      paintIconAt(g, actionButton, icon, x, y);
    }

    public void paintBackground(Graphics g, JComponent component, int state) {
      myDelegate.paintBackground(g, component, state);
    }
  }
}