package com.intellij.ide.plugins;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ex.ActionToolbarEx;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.SpeedSearchBase;
import com.intellij.ui.TableUtil;
import com.intellij.util.net.HTTPProxySettingsDialog;
import com.intellij.util.net.IOExceptionDialog;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLFrameHyperlinkEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipException;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Dec 25, 2003
 * Time: 9:47:59 PM
 * To change this template use Options | File Templates.
 */
public class PluginManagerMain {
  private static Logger LOG = Logger.getInstance("#com.intellij.ide.plugins.PluginManagerMain");

  public static final int INSTALLED_TAB = 0;
  public static final int AVAILABLE_TAB = 1;
  //public static final int CART_TAB = 2;

  public static final String TEXT_PREFIX = "<html><body style=\"font-family: Arial; font-size: 12pt;\">";
  public static final String TEXT_SUFIX = "</body></html>";

  public static final String HTML_PREFIX = "<html><body><a href=\"\">";
  public static final String HTML_SUFIX = "</a></body></html>";

  public static final String NOT_SPECIFIED = "(not specified)";

  public static final String INSTALLED_TAB_NAME = "Installed";

  private JPanel myToolbarPanel;
  private JPanel main;
  private JLabel myVendorLabel;
  private JLabel myVendorEmailLabel;
  private JLabel myVendorUrlLabel;
  private JLabel myPluginUrlLabel;
  private JEditorPane myDescriptionTextArea;
  private JEditorPane myChangeNotesTextArea;
  private JTabbedPane tabs;
  private JScrollPane installedScrollPane;
  private JScrollPane availableScrollPane;
  //private JScrollPane cartScrollPane;

  // actions
  //private AnAction groupByCategoryAction;
  //private ExpandAllToolbarAction myExpandAllToolbarAction;
  //private CollapseAllToolbarAction myCollapseAllToolbarAction;
  //private AnAction addPluginToCartAction;
  //private AnAction removePluginFromCartAction;
  private AnAction syncAction;
  private AnAction updatePluginsAction;
  //private AnAction findPluginsAction;
  private AnAction installPluginAction;
  private AnAction uninstallPluginAction;

  private PluginTable<PluginDescriptor> installedPluginTable;
  private PluginTable<PluginNode> availablePluginTable;
  //private PluginTable<PluginNode> cartTable;

  private ActionToolbarEx toolbar;

  private CategoryNode root;

  private boolean requireShutdown = false;

  private DefaultActionGroup actionGroup;
  private JButton myHttpProxySettingsButton;
  private final SortableProvider myAvailableProvider;
  private final SortableProvider myInstalledProvider;
  private final SortableProvider myCartProvider;

  private void pluginInfoUpdate (Object plugin) {
    if (plugin instanceof PluginDescriptor) {
      PluginDescriptor pluginDescriptor = (PluginDescriptor)plugin;

      myVendorLabel.setText(pluginDescriptor.getVendor());

      if (pluginDescriptor.getDescription() != null) {
        myDescriptionTextArea.setText(TEXT_PREFIX + pluginDescriptor.getDescription().trim() + TEXT_SUFIX);
        myDescriptionTextArea.setCaretPosition(0);
      } else {
        myDescriptionTextArea.setText("");
      }

      if (pluginDescriptor.getChangeNotes() != null) {
        myChangeNotesTextArea.setText(TEXT_PREFIX + pluginDescriptor.getChangeNotes().trim() + TEXT_SUFIX);
        myChangeNotesTextArea.setCaretPosition(0);
      } else {
        myChangeNotesTextArea.setText("");
      }

      final String email = pluginDescriptor.getVendorEmail();
      if (email != null && email.trim().length() > 0) {
        myVendorEmailLabel.setText(HTML_PREFIX + email.trim() + HTML_SUFIX);
        myVendorEmailLabel.setCursor(new Cursor (Cursor.HAND_CURSOR));
      }
      else {
        myVendorEmailLabel.setText(NOT_SPECIFIED);
        myVendorEmailLabel.setCursor(new Cursor (Cursor.DEFAULT_CURSOR));
      }

      final String url = pluginDescriptor.getVendorUrl();
      if (url != null && url.trim().length() > 0) {
        myVendorUrlLabel.setText(HTML_PREFIX + url.trim() + HTML_SUFIX);
        myVendorUrlLabel.setCursor(new Cursor (Cursor.HAND_CURSOR));
      }
      else {
        myVendorUrlLabel.setText(NOT_SPECIFIED);
        myVendorUrlLabel.setCursor(new Cursor (Cursor.DEFAULT_CURSOR));
      }

      final String homePage = pluginDescriptor.getUrl();
      if (homePage != null && homePage.trim().length() > 0) {
        myPluginUrlLabel.setText(HTML_PREFIX + homePage + HTML_SUFIX);
        myPluginUrlLabel.setCursor(new Cursor (Cursor.HAND_CURSOR));
      }
      else {
        myPluginUrlLabel.setText(NOT_SPECIFIED);
        myPluginUrlLabel.setCursor(new Cursor (Cursor.DEFAULT_CURSOR));
      }

    } else if (plugin instanceof PluginNode) {
      PluginNode pluginNode = (PluginNode)plugin;

      myVendorLabel.setText(pluginNode.getVendor());

      if (pluginNode.getDescription() != null) {
        myDescriptionTextArea.setText(TEXT_PREFIX + pluginNode.getDescription().trim() + TEXT_SUFIX);
        myDescriptionTextArea.setCaretPosition(0);
      } else {
        myDescriptionTextArea.setText("");
      }

      if (pluginNode.getChangeNotes() != null) {
        myChangeNotesTextArea.setText(TEXT_PREFIX + pluginNode.getChangeNotes().trim() + TEXT_SUFIX);
        myChangeNotesTextArea.setCaretPosition(0);
      } else {
        myChangeNotesTextArea.setText("");
      }

      final String email = pluginNode.getVendorEmail();
      if (email != null && email.trim().length() > 0) {
        myVendorEmailLabel.setText(HTML_PREFIX + email.trim() + HTML_SUFIX);
        myVendorEmailLabel.setCursor(new Cursor (Cursor.HAND_CURSOR));
      }
      else {
        myVendorEmailLabel.setText(NOT_SPECIFIED);
        myVendorEmailLabel.setCursor(new Cursor (Cursor.DEFAULT_CURSOR));
      }

      final String url = pluginNode.getVendorUrl();
      if (url != null && url.trim().length() > 0) {
        myVendorUrlLabel.setText(HTML_PREFIX + url.trim() + HTML_SUFIX);
        myVendorUrlLabel.setCursor(new Cursor (Cursor.HAND_CURSOR));
      }
      else {
        myVendorUrlLabel.setText(NOT_SPECIFIED);
        myVendorUrlLabel.setCursor(new Cursor (Cursor.DEFAULT_CURSOR));
      }

      final String homePage = pluginNode.getUrl();
      if (homePage != null && homePage.trim().length() > 0) {
        myPluginUrlLabel.setText(HTML_PREFIX + homePage + HTML_SUFIX);
        myPluginUrlLabel.setCursor(new Cursor (Cursor.HAND_CURSOR));
      }
      else {
        myPluginUrlLabel.setText(NOT_SPECIFIED);
        myPluginUrlLabel.setCursor(new Cursor (Cursor.DEFAULT_CURSOR));
      }

    } else {
      myVendorLabel.setText(NOT_SPECIFIED);
      myVendorEmailLabel.setText(NOT_SPECIFIED);
      myVendorUrlLabel.setText(NOT_SPECIFIED);
      myDescriptionTextArea.setText("");
      myChangeNotesTextArea.setText("");
      myPluginUrlLabel.setText(NOT_SPECIFIED);
    }
  }

  public PluginManagerMain(SortableProvider availableProvider, SortableProvider installedProvider, SortableProvider cartProvider) {
    myAvailableProvider = availableProvider;
    myInstalledProvider = installedProvider;
    myCartProvider = cartProvider;
    myToolbarPanel.setLayout(new BorderLayout());
    toolbar = (ActionToolbarEx)ActionManagerEx.getInstance().createActionToolbar("PluginManaer", getActionGroup(), true);

    myToolbarPanel.add(toolbar.getComponent(), BorderLayout.WEST);

    myDescriptionTextArea.setContentType("text/html");
    myDescriptionTextArea.addHyperlinkListener(new MyHyperlinkListener());

    myChangeNotesTextArea.setContentType("text/html");
    myChangeNotesTextArea.addHyperlinkListener(new MyHyperlinkListener());

    installedPluginTable = new PluginTable<PluginDescriptor>(new InstalledPluginsTableModel(myInstalledProvider));

    installedScrollPane.getViewport().setBackground(installedPluginTable.getBackground());
    installedScrollPane.getViewport().setView(installedPluginTable);
    installedPluginTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        pluginInfoUpdate(installedPluginTable.getSelectedObject());
        toolbar.updateActions();
      }
    });
    PopupHandler.installUnknownPopupHandler(installedPluginTable, getActionGroup(), ActionManager.getInstance());
    tabs.setTitleAt(INSTALLED_TAB, INSTALLED_TAB_NAME + " (" + installedPluginTable.getRowCount() + ")");

    /*
    cartTable = new PluginTable<PluginNode>(new ShoppingCartTableModel(
    PluginManagerConfigurable.getInstance().getCartSortableProvider()));
    cartScrollPane.getViewport().setBackground(cartTable.getBackground());
    cartScrollPane.getViewport().setView(cartTable);
    cartTable.getSelectionModel().addListSelectionListener(new ListSelectionListener () {
    public void valueChanged(ListSelectionEvent e) {
    pluginInfoUpdate(cartTable.getSelectedObject());
    }
    });
    */

    tabs.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        if (tabs.getSelectedIndex() == INSTALLED_TAB) {
          pluginInfoUpdate(installedPluginTable.getSelectedObject());
        }
        else if (tabs.getSelectedIndex() == AVAILABLE_TAB) {
          pluginInfoUpdate(null);

          // load plugin list, if required
          loadAvailablePlugins(true);
          if (availablePluginTable != null) {
            pluginInfoUpdate(availablePluginTable.getSelectedObject());
          }

          /*
          } else if (tabs.getSelectedIndex() == CART_TAB) {
          pluginInfoUpdate(null);

          // show cart tab
          pluginInfoUpdate(cartTable.getSelectedObject());
          */
        }
        toolbar.updateActions();
      }
    });

    myHttpProxySettingsButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        HTTPProxySettingsDialog settingsDialog = new HTTPProxySettingsDialog();
        settingsDialog.pack();
        settingsDialog.show();
      }
    });
    /*
    httpProxySettingsLabel.setCursor(new Cursor (Cursor.HAND_CURSOR));
    httpProxySettingsLabel.addMouseListener(new MouseAdapter () {
      public void mouseClicked(MouseEvent e) {
        HTTPProxySettingsDialog settingsDialog = new HTTPProxySettingsDialog();
        settingsDialog.pack();
        settingsDialog.show();
      }
    });
    */

    myVendorEmailLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
    myVendorEmailLabel.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        String email = null;

        if (tabs.getSelectedIndex() == INSTALLED_TAB) {
          PluginDescriptor pluginDescriptor = installedPluginTable.getSelectedObject();
          if (pluginDescriptor != null) {
            email = pluginDescriptor.getVendorEmail();
          }
        }
        else if (tabs.getSelectedIndex() == AVAILABLE_TAB) {
          PluginNode pluginNode = availablePluginTable.getSelectedObject();
          if (pluginNode != null) {
            email = pluginNode.getVendorEmail();
          }
          /*
          } else if (tabs.getSelectedIndex() == CART_TAB) {
          PluginNode pluginNode = cartTable.getSelectedObject();
          if (pluginNode != null)
          email = pluginNode.getVendorEmail();
          */
        }

        if (email != null && email.trim().length() > 0) {
          try {
            BrowserUtil.launchBrowser("mailto:" + email.trim());
          }
          catch (IllegalThreadStateException ex) {
            // not a problem
          }
        }
      }
    });

    myVendorUrlLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
    myVendorUrlLabel.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        String url = null;

        if (tabs.getSelectedIndex() == INSTALLED_TAB) {
          PluginDescriptor pluginDescriptor = installedPluginTable.getSelectedObject();
          if (pluginDescriptor != null) {
            url = pluginDescriptor.getVendorUrl();
          }
        }
        else if (tabs.getSelectedIndex() == AVAILABLE_TAB) {
          PluginNode pluginNode = availablePluginTable.getSelectedObject();
          if (pluginNode != null) {
            url = pluginNode.getVendorUrl();
          }
          /*
          } else if (tabs.getSelectedIndex() == CART_TAB) {
          PluginNode pluginNode = cartTable.getSelectedObject();
          if (pluginNode != null)
          url = pluginNode.getVendorUrl();
          */
        }

        if (url != null && url.trim().length() > 0) {
          BrowserUtil.launchBrowser(url.trim());
        }
      }
    });

    myPluginUrlLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
    myPluginUrlLabel.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        String url = null;

        if (tabs.getSelectedIndex() == INSTALLED_TAB) {
          PluginDescriptor pluginDescriptor = installedPluginTable.getSelectedObject();
          if (pluginDescriptor != null) {
            url = pluginDescriptor.getUrl();
          }
        }
        else if (tabs.getSelectedIndex() == AVAILABLE_TAB) {
          PluginNode pluginNode = availablePluginTable.getSelectedObject();
          if (pluginNode != null) {
            url = pluginNode.getUrl();
          }
          /*
          } else if (tabs.getSelectedIndex() == CART_TAB) {
          PluginNode pluginNode = cartTable.getSelectedObject();
          if (pluginNode != null)
          url = pluginNode.getUrl();
          */
        }

        if (url != null && url.trim().length() > 0) {
          BrowserUtil.launchBrowser(url.trim());
        }
      }
    });

    new SpeedSearchBase<PluginTable<PluginDescriptor>>(installedPluginTable) {
      public int getSelectedIndex() {
        return installedPluginTable.getSelectedRow();
      }

      public Object[] getAllElements() {
        return installedPluginTable.getElements();
      }

      public String getElementText(Object element) {
        return ((PluginDescriptor)element).getName();
      }

      public void selectElement(Object element, String selectedText) {
        for (int i = 0; i < installedPluginTable.getRowCount(); i++) {
          if (installedPluginTable.getObjectAt(i).getName().equals(((PluginDescriptor)element).getName())) {
            installedPluginTable.setRowSelectionInterval(i, i);
            TableUtil.scrollSelectionToVisible(installedPluginTable);
            break;
          }
        }
      }
    };
  }

  private void loadAvailablePlugins (boolean askForDownload) {
    try {
      if (root == null) {
        if (askForDownload) {
          if (Messages.showYesNoDialog(main, "Latest information from the Plugin Repository is required.\n" +
                                             "Would you like to download it?", "Plugins",
                                       Messages.getQuestionIcon()) != 0) {
            tabs.setSelectedIndex(INSTALLED_TAB);
            return;
          }
        }

        root = loadPluginList();
        if (root == null) {
          Messages.showErrorDialog(getMainPanel(), "List of plugins was not loaded.", "Plugins");
          tabs.setSelectedIndex(INSTALLED_TAB);

          return;
        }

        availablePluginTable = new PluginTable<PluginNode>(
          new AvailablePluginsTableModel(root, myAvailableProvider)
        );
        availablePluginTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
          public void valueChanged(ListSelectionEvent e) {
            pluginInfoUpdate(availablePluginTable.getSelectedObject());
            toolbar.updateActions();
          }
        });
        ActionGroup group = getActionGroup();
        PopupHandler.installUnknownPopupHandler(availablePluginTable, group, ActionManager.getInstance());
        availableScrollPane.getViewport().setBackground(availablePluginTable.getBackground());
        availableScrollPane.getViewport().setView(availablePluginTable);

        tabs.setTitleAt(AVAILABLE_TAB, "Available (" + availablePluginTable.getRowCount() + ")");

        availablePluginTable.requestFocus();

        new SpeedSearchBase<PluginTable<PluginNode>>(availablePluginTable) {
          public int getSelectedIndex() {
            return availablePluginTable.getSelectedRow();
          }

          public Object[] getAllElements() {
            return availablePluginTable.getElements();
          }

          public String getElementText(Object element) {
            return ((PluginNode)element).getName();
          }

          public void selectElement(Object element, String selectedText) {
            for (int i = 0; i < availablePluginTable.getRowCount(); i++) {
              if (availablePluginTable.getObjectAt(i).getName().equals(((PluginNode)element).getName())) {
                availablePluginTable.setRowSelectionInterval(i, i);
                TableUtil.scrollSelectionToVisible(availablePluginTable);
                break;
              }
            }
          }
        };
      }
    } catch (RuntimeException ex) {
      ex.printStackTrace();
      Messages.showErrorDialog(getMainPanel(), "Available plugins list is not loaded", "Error");
    }
  }

  private ActionGroup getActionGroup () {
    if (actionGroup == null) {
      actionGroup = new DefaultActionGroup();

      /*
      groupByCategoryAction = new ToggleAction("Group by category",
      "Group plugins by category",
      IconLoader.getIcon("/_cvs/showAsTree.png")) {
      public boolean isSelected(AnActionEvent e) {
      return PluginManagerConfigurable.getInstance().TREE_VIEW;
      }

      public void setSelected(AnActionEvent e, boolean state) {
      PluginManagerConfigurable.getInstance().TREE_VIEW = state;
      // @todo switch table and tree view
      }
      };
      actionGroup.add(groupByCategoryAction);
      actionGroup.addSeparator();
      */

      //myExpandAllToolbarAction = new ExpandAllToolbarAction(myTreeTable);
      //actionGroup.add(myExpandAllToolbarAction);
      //myCollapseAllToolbarAction = new CollapseAllToolbarAction(myTreeTable);
      //actionGroup.add(myCollapseAllToolbarAction);

      /*
      addPluginToCartAction = new AnAction ("Add Plugin to \"Shopping Cart\"",
      "Add Plugin to \"Shopping Cart\"",
      IconLoader.getIcon("/actions/include.png")) {
      public void update(AnActionEvent e) {
      Presentation presentation = e.getPresentation();
      boolean enabled = false;

      if (tabs.getSelectedIndex() == AVAILABLE_TAB && availablePluginTable != null) {
      PluginNode pluginNode = availablePluginTable.getSelectedObject();

      if (pluginNode != null) {
      int status = PluginManagerColumnInfo.getRealNodeState(pluginNode);
      if (status == PluginNode.STATUS_MISSING ||
      status == PluginNode.STATUS_NEWEST ||
      status == PluginNode.STATUS_OUT_OF_DATE ||
      status == PluginNode.STATUS_UNKNOWN) {
      enabled = true;
      }
      }
      }

      presentation.setEnabled(enabled);
      }

      public void actionPerformed(AnActionEvent e) {
      if (tabs.getSelectedIndex() == AVAILABLE_TAB) {
      PluginNode pluginNode = availablePluginTable.getSelectedObject();
      if (pluginNode != null) {
      int state = PluginManagerColumnInfo.getRealNodeState(pluginNode);
      if (state == PluginNode.STATUS_MISSING ||
      state == PluginNode.STATUS_NEWEST ||
      state == PluginNode.STATUS_OUT_OF_DATE ||
      state == PluginNode.STATUS_UNKNOWN) {
      ((ShoppingCartTableModel)cartTable.getModel ()).add(pluginNode);
      tabs.setTitleAt(CART_TAB, "Shopping Cart (" + cartTable.getRowCount() + ")");
      }
      }
      }
      }
      };
      actionGroup.add(addPluginToCartAction);

      removePluginFromCartAction = new AnAction ("Delete Plugin from \"Shopping Cart\"",
      "Delete Plugin from \"Shopping Cart\"",
      IconLoader.getIcon("/actions/exclude.png")) {
      public void update(AnActionEvent e) {
      Presentation presentation = e.getPresentation();
      boolean enabled = false;

      if (tabs.getSelectedIndex() == CART_TAB) {
      PluginNode pluginNode = cartTable.getSelectedObject();

      if (pluginNode != null) {
      int status = PluginManagerColumnInfo.getRealNodeState(pluginNode);
      if (status == PluginNode.STATUS_CART) {
      enabled = true;
      }
      }
      }

      presentation.setEnabled(enabled);
      }

      public void actionPerformed(AnActionEvent e) {
      if (tabs.getSelectedIndex() == CART_TAB) {
      PluginNode pluginNode = cartTable.getSelectedObject();
      if (pluginNode != null) {
      int state = PluginManagerColumnInfo.getRealNodeState(pluginNode);
      if (state == PluginNode.STATUS_CART) {
      ((ShoppingCartTableModel)cartTable.getModel ()).remove(pluginNode);
      tabs.setTitleAt(CART_TAB, "Shopping Cart (" + cartTable.getRowCount() + ")");
      }
      }
      }
      }
      };
      actionGroup.add(removePluginFromCartAction);
      actionGroup.addSeparator();
      */

      syncAction = new AnAction("Synchronize with Plugin Repository", "Synchronize with Plugin Repository",
                                IconLoader.getIcon("/actions/sync.png")) {
        public void update(AnActionEvent e) {
          Presentation presentation = e.getPresentation();
          boolean enabled = false;

          if (tabs.getSelectedIndex() == AVAILABLE_TAB) {
            enabled = true;
          }

          presentation.setEnabled(enabled);
        }

        public void actionPerformed(AnActionEvent e) {
          root = null;
          pluginInfoUpdate(null);
          loadAvailablePlugins(false);
        }
      };
      syncAction.registerCustomShortcutSet(
        new CustomShortcutSet (KeymapManager.getInstance().getActiveKeymap().getShortcuts("Synchronize")), main);
      actionGroup.add(syncAction);

      updatePluginsAction = new AnAction("Update Installed Plugins", "Update Installed Plugins",
                                         IconLoader.getIcon("/actions/refresh.png")) {

        public void actionPerformed(AnActionEvent e) {
          if (availablePluginTable == null) {
            loadAvailablePlugins(true);
          }

          if (root != null)
            do {
              try {
                List<PluginNode> updateList = new ArrayList<PluginNode>();
                checkForUpdate(updateList, root);

                if (updateList.size() == 0) {
                  Messages.showMessageDialog(main,
                                                "Nothing to update", "Plugin Manager", Messages.getInformationIcon());
                  break;
                }
                else {
                  String list = "";
                  for (int i = 0; i < updateList.size(); i++) {
                    PluginNode pluginNode = updateList.get(i);
                    list += pluginNode.getName() + "\n";
                  }

                  if (Messages.showYesNoDialog(main,
                                                    "Plugin(s) can be updated: \n" + list +
                                                    "Would you like to update them?",
                                                    "Update Installed Plugins", Messages.getQuestionIcon()) == 0) {
                    if (downloadPlugins(updateList)) {
                      availablePluginTable.updateUI();

                      requireShutdown = true;
                    }
                  }
                  break;
                }
              }
              catch (IOException e1) {
                if (!IOExceptionDialog.showErrorDialog(e1, "Update Installed Plugins", "Plugins updating failed")) {
                  break;
                }
                else {
                  LOG.error(e1);
                }
              }
            }
            while (true);
        }
      };
      actionGroup.add(updatePluginsAction);

      //findPluginsAction = new AnAction("Find", "Find Plugins", IconLoader.getIcon("/actions/find.png")) {
      //  public void actionPerformed(AnActionEvent e) {
      //    Messages.showMessageDialog(main, "Sorry, not implemented yet.", "Find Plugins", Messages.getWarningIcon());

          /*
          PluginManagerConfigurable.getInstance().FIND = JOptionPane.showInputDialog(
          JOptionPane.getRootFrame(), "Find:",
          PluginManagerConfigurable.getInstance().FIND != null ? PluginManagerConfigurable.getInstance().FIND : "");
          if (PluginManagerConfigurable.getInstance().FIND == null ||
          PluginManagerConfigurable.getInstance().FIND.trim().length() == 0) {
          return;
          }
          else {
          PluginNode pluginNode = find(myRoot,
          myCurrentNode,
          PluginManagerConfigurable.getInstance().FIND.trim());
          if (pluginNode == null) {
          JOptionPane.showMessageDialog(JOptionPane.getRootFrame(),
          "Nothing was found for '" + PluginManagerConfigurable.getInstance().FIND +
          "'",
          "Find plugin", JOptionPane.INFORMATION_MESSAGE);
          }
          }
          */
      //  }
      //};
      //findPluginsAction.registerCustomShortcutSet(
      //  new CustomShortcutSet (KeymapManager.getInstance().getActiveKeymap().getShortcuts("Find")), main);
      //actionGroup.add(findPluginsAction);

      installPluginAction = new AnAction("Download and Install Plugin",
                                         "Download and Install Plugin",
                                         IconLoader.getIcon("/actions/install.png")) {
        public void update(AnActionEvent e) {
          Presentation presentation = e.getPresentation();
          boolean enabled = false;

          if (tabs.getSelectedIndex() == AVAILABLE_TAB && availablePluginTable != null) {
            PluginNode pluginNode = availablePluginTable.getSelectedObject();

            if (pluginNode != null) {
              int status = PluginManagerColumnInfo.getRealNodeState(pluginNode);
              if (status == PluginNode.STATUS_MISSING ||
                  status == PluginNode.STATUS_NEWEST ||
                  status == PluginNode.STATUS_OUT_OF_DATE ||
                  status == PluginNode.STATUS_UNKNOWN) {
                enabled = true;
              }
            }
          }

          presentation.setEnabled(enabled);
        }

        public void actionPerformed(AnActionEvent e) {
          do {
            try {
              PluginNode pluginNode = availablePluginTable.getSelectedObject();

              if (Messages.showYesNoDialog(main,
                                                "Would you like to download and install plugin \"" + pluginNode.getName() + "\"?",
                                                "Download and Install Plugin", Messages.getQuestionIcon()) == 0) {
                if (downloadPlugin(pluginNode)) {
                  requireShutdown = true;
                  availablePluginTable.updateUI();
                }
              }
              break;
            }
            catch (IOException e1) {
              if (!IOExceptionDialog.showErrorDialog(e1, "Download and Install Plugin", "Plugin download failed")) {
                break;
              }
              else {
                LOG.error(e1);
              }
            }
          }
          while (true);
        }
      };
      actionGroup.add(installPluginAction);

      uninstallPluginAction = new AnAction("Uninstall Plugin",
                                           "Uninstall Plugin",
                                           IconLoader.getIcon("/actions/uninstall.png")) {
        public void update(AnActionEvent e) {
          Presentation presentation = e.getPresentation();
          boolean enabled = false;

          if (installedPluginTable != null && tabs.getSelectedIndex() == INSTALLED_TAB) {
            PluginDescriptor pluginDescriptor = installedPluginTable.getSelectedObject();

            if (pluginDescriptor != null && ! pluginDescriptor.isDeleted()) {
              enabled = true;
            }
          }
          presentation.setEnabled(enabled);
        }

        public void actionPerformed(AnActionEvent e) {
          String pluginName = null;

          if (tabs.getSelectedIndex() == INSTALLED_TAB) {
            PluginDescriptor pluginDescriptor = installedPluginTable.getSelectedObject();
            if (pluginDescriptor != null) {
              if (Messages.showYesNoDialog(main, "Do you really want to uninstall plugin \"" +
                                                      pluginDescriptor.getName() + "\"?", "Plugin Uninstall",
                                                Messages.getQuestionIcon()) == 0) {
                pluginName = pluginDescriptor.getName();
                pluginDescriptor.setDeleted(true);
              }
            }
          }

          if (pluginName != null) {
            try {
              PluginInstaller.prepareToUninstall(pluginName);

              requireShutdown = true;

              installedPluginTable.updateUI();

              /*
              Messages.showMessageDialog(main,
                                            "Plugin \'" + pluginName +
                                            "\' is uninstalled but still running. You will " +
                                            "need to restart IDEA to deactivate it.",
                                            "Plugin Uninstalled",
                                            Messages.getInformationIcon());
              */
            }
            catch (IOException e1) {
              LOG.equals(e1);
            }
          }
        }
      };
      actionGroup.add(uninstallPluginAction);
    }

    return actionGroup;
  }

  public JPanel getMainPanel() {
    return main;
  }

  private class MyHyperlinkListener implements HyperlinkListener {
    public void hyperlinkUpdate(HyperlinkEvent e) {
      if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
        JEditorPane pane = (JEditorPane)e.getSource();
        if (e instanceof HTMLFrameHyperlinkEvent) {
          HTMLFrameHyperlinkEvent evt = (HTMLFrameHyperlinkEvent)e;
          HTMLDocument doc = (HTMLDocument)pane.getDocument();
          doc.processHTMLFrameHyperlinkEvent(evt);
        }
        else {
          BrowserUtil.launchBrowser(e.getURL().toString());
        }

      }
    }
  }

  private CategoryNode loadPluginList() {
    StatusProcess statusProcess = new StatusProcess();
    do {
      boolean canceled = ApplicationManager.getApplication().runProcessWithProgressSynchronously(
        statusProcess, "Downloading List of Plugins", true, null);
      if (canceled && statusProcess.getException() != null) {
        if (statusProcess.getException() instanceof IOException) {
          if (! IOExceptionDialog.showErrorDialog((IOException)statusProcess.getException(), "Plugin Manager", "Could not download a list of plugins")) {
            break;
          }
        } else
          throw new RuntimeException(statusProcess.getException());
      } else {
        break;
      }
    }
    while (true);

    if (statusProcess != null) {
      return statusProcess.getRoot();
    }
    else {
      return null;
    }
  }

  public Object getSelectedPlugin () {
    switch (tabs.getSelectedIndex()) {
      case INSTALLED_TAB:
        return installedPluginTable.getSelectedObject();
      case AVAILABLE_TAB:
        return availablePluginTable.getSelectedObject();
        /*
        case CART_TAB:
        return cartTable.getSelectedObject();
        */
      default:
        return null;
    }
  }

  private void checkForUpdate(List<PluginNode> updateList, CategoryNode categoryNode)
    throws IOException {
    for (int i = 0; i < categoryNode.getPlugins().size(); i++) {
      PluginNode pluginNode = categoryNode.getPlugins().get(i);
      if (PluginManagerColumnInfo.getRealNodeState(pluginNode) == PluginNode.STATUS_OUT_OF_DATE) {
        updateList.add(pluginNode);
      }
    }

    if (categoryNode.getChildCount() > 0) {
      for (int i = 0; i < categoryNode.getChildren().size(); i++) {
        CategoryNode node = categoryNode.getChildren().get(i);
        checkForUpdate(updateList, node);
      }
    }
  }

  private boolean downloadPlugins (final List <PluginNode> plugins) throws IOException {
    final boolean[] result = new boolean[1];
    try {
      ApplicationManager.getApplication().runProcessWithProgressSynchronously(
        new Runnable () {
          public void run() {
            result[0] = PluginInstaller.prepareToInstall(plugins);
          }
        }, "Download Plugins", true, null);
    } catch (RuntimeException e) {
      if (e.getCause() != null && e.getCause() instanceof IOException)
        throw (IOException)e.getCause();
      else
        throw e;
    }
    return result[0];
  }

  private boolean downloadPlugin (final PluginNode pluginNode) throws IOException {
    final boolean[] result = new boolean[1];
    try {
      ApplicationManager.getApplication().runProcessWithProgressSynchronously(
        new Runnable () {
          public void run() {
            ProgressIndicator pi = ProgressManager.getInstance().getProgressIndicator();

            pi.setText(pluginNode.getName());

            try {
              result[0] = PluginInstaller.prepareToInstall(pluginNode);
            } catch (ZipException e) {
              JOptionPane.showMessageDialog(JOptionPane.getRootFrame(),
                                            "Plugin \"" + pluginNode.getName() + "\" has problems in ZIP archive and will be not installed.",
                                            "Installing Plugin", JOptionPane.ERROR_MESSAGE, Messages.getErrorIcon());
            } catch (IOException e) {
              throw new RuntimeException (e);
            }
          }
        }, "Download Plugin \"" + pluginNode.getName() + "\"", true, null);
    } catch (RuntimeException e) {
      if (e.getCause() != null && e.getCause() instanceof IOException)
        throw (IOException)e.getCause();
      else
        throw e;
    }

    return result[0];
  }

  public boolean isRequireShutdown() {
    return requireShutdown;
  }

  public void ignoreChages() {
    requireShutdown = false;
  }
}
