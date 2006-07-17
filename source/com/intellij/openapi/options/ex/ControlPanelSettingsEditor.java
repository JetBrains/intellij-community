package com.intellij.openapi.options.ex;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.ShowSettingsUtilImpl;
import com.intellij.ide.ui.search.DefaultSearchableConfigurable;
import com.intellij.ide.ui.search.SearchUtil;
import com.intellij.ide.ui.search.SearchableOptionsRegistrar;
import com.intellij.openapi.actionSystem.ActionButtonComponent;
import com.intellij.openapi.actionSystem.ex.ActionButtonLook;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.options.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.LabeledIcon;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TitledSeparator;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.*;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 8, 2003
 * Time: 9:40:01 PM
 * To change this template use Options | File Templates.
 */
public class ControlPanelSettingsEditor extends DialogWrapper {
  private static final int ICONS_PER_ROW = 7;
  private static final Insets ICON_INSETS = new Insets(2, 2, 2, 2);

  protected final Project myProject;
  private final ConfigurableGroup[] myGroups;
  private JPanel myPanel;

  private Configurable myKeypressedConfigurable = null;
  private int mySelectedRow = 0;
  private int mySelectedColumn = 0;
  private int mySelectedGroup = 0;

  private Set<Configurable> myOptionContainers = null;
  private SearchUtil.SearchTextField mySearchField;
  private GlassPanel myGlassPanel;
  private Alarm myShowHintAlarm = new Alarm();
  private JBPopup[] myPopup = new JBPopup[1];

  public ControlPanelSettingsEditor(Project project, ConfigurableGroup[] groups, Configurable preselectedConfigurable) {
    super(project, true);
    myProject = project;
    myGroups = groups;
    setTitle(OptionsBundle.message("settings.panel.title"));
    setCancelButtonText(CommonBundle.getCloseButtonText());
    init();
    if (preselectedConfigurable != null) {
      selectConfigurable(preselectedConfigurable);
      editConfigurable(preselectedConfigurable);
    }
    myGlassPanel = new GlassPanel(myPanel);
    myPanel.getRootPane().setGlassPane(myGlassPanel);
  }

  protected String getDimensionServiceKey() {
    return "#com.intellij.openapi.options.ex.ControlPanelSettingsEditor";
  }

  protected Action[] createActions() {
    return new Action[]{getCancelAction()};
  }

  protected Action[] createLeftSideActions() {
    return new Action[]{new SwitchToClassicViewAction()};
  }

  protected JComponent createCenterPanel() {
    myPanel = new JPanel(new VerticalFlowLayout());
    for (int i = 0; i < myGroups.length; i++) {
      ConfigurableGroup group = myGroups[i];
      myPanel.add(createGroupComponent(group, i));
    }

    myPanel.addKeyListener(new KeyAdapter() {
      public void keyPressed(KeyEvent e) {
        try {
          if (e.getKeyCode() == KeyEvent.VK_ENTER) {
            myKeypressedConfigurable = getSelectedConfigurable();
            return;
          }

          int code = e.getKeyCode();
          if (code == KeyEvent.VK_UP || code == KeyEvent.VK_DOWN || code == KeyEvent.VK_RIGHT ||
              code == KeyEvent.VK_LEFT) {
            if (getSelectedConfigurable() == null) {
              mySelectedColumn = 0;
              mySelectedRow = 0;
              mySelectedGroup = 0;
              return;
            }

            int xShift = 0;
            int yShift = 0;

            if (code == KeyEvent.VK_UP) {
              yShift = -1;
            }
            else if (code == KeyEvent.VK_DOWN) {
              yShift = 1;
            }
            else if (code == KeyEvent.VK_LEFT) {
              xShift = -1;
            }
            else /*if (code == KeyEvent.VK_RIGHT)*/ {
              xShift = 1;
            }

            int newColumn = mySelectedColumn + xShift;
            int newRow = mySelectedRow + yShift;
            int newGroup = mySelectedGroup;

            if (newColumn < 0) newColumn = 0;
            if (newColumn >= ICONS_PER_ROW) newColumn = ICONS_PER_ROW - 1;

            int idx = newColumn + newRow * ICONS_PER_ROW;
            if (idx >= myGroups[newGroup].getConfigurables().length) {
              if (yShift > 0) {
                newRow = 0;
                newGroup++;
                if (newGroup >= myGroups.length) return;

                idx = newColumn + newRow * ICONS_PER_ROW;
                if (idx >= myGroups[newGroup].getConfigurables().length) return;
              }
              else if (xShift > 0) {
                return;
              }
            }

            if (yShift < 0 && idx < 0) {
              newGroup--;
              if (newGroup < 0) return;
              int rowCount = getRowCount(myGroups[newGroup].getConfigurables().length);
              newRow = rowCount - 1;
              idx = newColumn + newRow * ICONS_PER_ROW;
              if (idx >= myGroups[newGroup].getConfigurables().length) {
                if (newRow <= 0) return;
                newRow--;
              }
            }

            mySelectedColumn = newColumn;
            mySelectedRow = newRow;
            mySelectedGroup = newGroup;
            return;
          }

          myKeypressedConfigurable = ControlPanelMnemonicsUtil.getConfigurableFromMnemonic(e, myGroups);
        }
        finally {
          myPanel.repaint();
        }
      }

      public void keyReleased(KeyEvent e) {
        if (myKeypressedConfigurable != null) {
          e.consume();
          selectConfigurable(myKeypressedConfigurable);
          editConfigurable(myKeypressedConfigurable);
          myKeypressedConfigurable = null;
          myPanel.repaint();
        }
      }
    });

    JPanel panel = new JPanel(new GridBagLayout());
    panel.add(myPanel,
              new GridBagConstraints(0, 0, 1, 1, 1, 1, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
                                     new Insets(0, 0, 0, 0), 0, 0));


    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(panel);
    scrollPane.setBorder(null);
    return scrollPane;
  }

  private void selectConfigurable(Configurable configurable) {
    for (int g = 0; g < myGroups.length; g++) {
      ConfigurableGroup group = myGroups[g];
      Configurable[] allConfigurables = group.getConfigurables();
      int count = allConfigurables.length;
      int rowCount = getRowCount(count);

      for (int i = 0; i < rowCount; i++) {
        for (int j = 0; j < ICONS_PER_ROW; j++) {
          int n = i * ICONS_PER_ROW + j;
          if (n < count && configurable == allConfigurables[n]) {
            mySelectedGroup = g;
            mySelectedRow = i;
            mySelectedColumn = j;
            return;
          }
        }
      }
    }
  }

  public JComponent getPreferredFocusedComponent() {
    return myPanel;
  }

  private JComponent createGroupComponent(ConfigurableGroup group, int groupIdx) {
    JPanel panel = new JPanel(new VerticalFlowLayout());

    final TitledSeparator separator = new TitledSeparator();
    separator.setText(group.getDisplayName());
    separator.setTitleFont(new JLabel().getFont().deriveFont(20.0f));
    panel.add(separator);

    Configurable[] allConfigurables = group.getConfigurables();
    int count = allConfigurables.length;
    int rowCount = getRowCount(count);
    JPanel toolBar = new JPanel(new GridLayout(0, ICONS_PER_ROW));

    for (int i = 0; i < rowCount; i++) {
      for (int j = 0; j < ICONS_PER_ROW; j++) {
        int n = i * ICONS_PER_ROW + j;
        if (n < count) {
          toolBar.add(createActionButton(allConfigurables[n], getShortcut(n, groupIdx), groupIdx, j, i));
        }
      }
    }

    panel.add(toolBar);

    panel.add(new JPanel());
    return panel;
  }

  private static int getRowCount(int count) {
    return count / ICONS_PER_ROW + (count % ICONS_PER_ROW == 0 ? 0 : 1);
  }

  private static KeyStroke getShortcut(int actionIdx, int groupIdx) {
    int mnemonic = getMnemonicByIndex(actionIdx, groupIdx);
    if (mnemonic == 0) return null;
    return KeyStroke.getKeyStroke(mnemonic, 0);
  }

  private static int getMnemonicByIndex(int idx, int groupIdx) {
    if (groupIdx == 0) {
      if (idx >= 0 && idx < 9) return KeyEvent.VK_1 + idx;
      if (idx == 9) return KeyEvent.VK_0;
      return 0;
    }

    if (groupIdx == 1) {
      if (idx >= 0 && idx < KeyEvent.VK_Z - KeyEvent.VK_A) return KeyEvent.VK_A + idx;
    }

    return 0;
  }

  private Configurable getSelectedConfigurable() {
    if (mySelectedColumn == -1 || mySelectedRow == -1 || mySelectedGroup == -1) return null;
    return myGroups[mySelectedGroup].getConfigurables()[mySelectedColumn + mySelectedRow * ICONS_PER_ROW];
  }

  private JComponent createActionButton(final Configurable configurable,
                                        KeyStroke shortcut,
                                        final int groupIdx,
                                        final int column,
                                        final int row) {
    return new MyActionButton(configurable, shortcut, groupIdx, row, column);
  }

  private void editConfigurable(Configurable configurable) {
    Configurable actualConfigurable = configurable;
    if (configurable instanceof ProjectComponent) {
      actualConfigurable = new ProjectConfigurableWrapper(myProject, configurable);
    }

    if (actualConfigurable instanceof SearchableConfigurable){
      actualConfigurable = new DefaultSearchableConfigurable((SearchableConfigurable)actualConfigurable);
      ((DefaultSearchableConfigurable)actualConfigurable).clearSearch();
      @NonNls final String filter = mySearchField.getText();
      if (filter != null && filter.length() > 0 ){
        final DefaultSearchableConfigurable finalConfigurable = (DefaultSearchableConfigurable)actualConfigurable;
        SwingUtilities.invokeLater(new Runnable (){
          public void run() {
            finalConfigurable.enableSearch(filter);
          }
        });
      }
    }
    final SingleConfigurableEditor configurableEditor =
      new SingleConfigurableEditor(myProject, actualConfigurable, createDimensionKey(configurable));
    configurableEditor.show();
  }

  private static String createDimensionKey(Configurable configurable) {
    String displayName = configurable.getDisplayName();
    displayName = displayName.replaceAll("\n", "_").replaceAll(" ", "_");
    return "#" + displayName;
  }


  public void dispose() {
    if (myPopup[0] != null){
      myPopup[0].cancel();
    }
    myGlassPanel = null;
    super.dispose();
  }

  protected JComponent createNorthPanel() {
    final JPanel panel = new JPanel(new GridBagLayout());
    mySearchField = new SearchUtil.SearchTextField();
    mySearchField.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        myGlassPanel.clear();
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
        myPanel.repaint();
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

  private class MyActionButton extends JComponent implements ActionButtonComponent {
    private Configurable myConfigurable;
    private int myGroupIdx;
    private int myRowIdx;
    private int myColumnIdx;
    private Icon myIcon;
    private KeyStroke myShortcut;

    public MyActionButton(Configurable configurable, KeyStroke shortcut, int groupIdx, int rowIdx, int columnIdx) {
      myConfigurable = configurable;
      myGroupIdx = groupIdx;
      myRowIdx = rowIdx;
      myColumnIdx = columnIdx;
      myShortcut = shortcut;
      myIcon = createIcon();
      setToolTipText(null);
      setupListeners();
    }

    private Icon createIcon() {
      Icon icon = myConfigurable.getIcon();
      if (icon == null) {
        icon = IconLoader.getIcon("/general/configurableDefault.png");
      }

      String displayName = myConfigurable.getDisplayName();

      LabeledIcon labeledIcon = new LabeledIcon(icon, displayName,
                                                myShortcut == null
                                                ? null
                                                : " (" + KeyEvent.getKeyText(myShortcut.getKeyCode()) + ")");
      return labeledIcon;
    }

    public Dimension getPreferredSize() {
      return new Dimension(myIcon.getIconWidth() + ICON_INSETS.left + ICON_INSETS.right,
                           myIcon.getIconHeight() + ICON_INSETS.top + ICON_INSETS.bottom);
    }

    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      ActionButtonLook look = ActionButtonLook.IDEA_LOOK;
      look.paintBackground(g, this);
      look.paintIcon(g, this, myIcon);
      look.paintBorder(g, this);
      if (myOptionContainers != null && myOptionContainers.contains(myConfigurable)) {
        myGlassPanel.addSpotlight(this);
      }
    }

    public int getPopState() {
      if (myKeypressedConfigurable == myConfigurable) return ActionButtonComponent.PUSHED;
      if (myKeypressedConfigurable != null) return ActionButtonComponent.NORMAL;
      Configurable selectedConfigurable = getSelectedConfigurable();
      if (selectedConfigurable == myConfigurable) return ActionButtonComponent.POPPED;
      return ActionButtonComponent.NORMAL;
    }

    private void setupListeners() {
      addMouseListener(new MouseAdapter() {
        public void mousePressed(MouseEvent e) {
          myKeypressedConfigurable = myConfigurable;
          myPanel.repaint();
        }

        public void mouseReleased(MouseEvent e) {
          if (myKeypressedConfigurable == myConfigurable) {
            myKeypressedConfigurable = null;
            editConfigurable(myConfigurable);
          }
          else {
            myKeypressedConfigurable = null;
          }

          myPanel.repaint();
        }
      });

      addMouseMotionListener(new MouseMotionListener() {
        public void mouseDragged(MouseEvent e) {
        }

        public void mouseMoved(MouseEvent e) {
          mySelectedColumn = myColumnIdx;
          mySelectedRow = myRowIdx;
          mySelectedGroup = myGroupIdx;
          myPanel.repaint();
        }
      });
    }
  }

  private class SwitchToClassicViewAction extends AbstractAction {
    public SwitchToClassicViewAction() {
      putValue(Action.NAME, OptionsBundle.message("control.panel.classic.view.button"));
    }

    public void actionPerformed(ActionEvent e) {
      ControlPanelSettingsEditor.this.close(OK_EXIT_CODE);

      ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            ((ShowSettingsUtilImpl)ShowSettingsUtil.getInstance()).showExplorerOptions(myProject, myGroups);
          }
        }, ModalityState.NON_MMODAL);
    }
  }
}
