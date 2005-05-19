package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.help.impl.HelpManagerImpl;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.plugins.PluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.plugins.PluginManagerConfigurable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionButtonLook;
import com.intellij.openapi.actionSystem.impl.EmptyIcon;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.LabeledIcon;
import com.intellij.ui.ScrollPaneFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: pti
 * Date: Mar 18, 2005
 * Time: 7:32:41 PM
 * To change this template use File | Settings | File Templates.
 */
public class WelcomeScreen {
  private JPanel myWelcomePanel;
  private JPanel myMainPanel;
  private JPanel myPluginsPanel;

  private static final Insets ICON_INSETS = new Insets(15, 0, 15, 0);

  // The below 3 array lists are reserved for future use
  private static ArrayList<MyActionButton> ourMainButtons = new ArrayList<MyActionButton>(5);
  private static ArrayList<MyActionButton> ourPluginButtons = new ArrayList<MyActionButton>(5);
  private static ArrayList<PluginDescriptor> ourPluginsWithActions = new ArrayList<PluginDescriptor>();

  private MyActionButton myKeypressedButton = null;
  private int mySelectedRow = -1;
  private int mySelectedColumn = -1;
  private int mySelectedGroup = -1;
  private static final int MAIN_GROUP = 0;
  private static final int PLUGINS_GROUP = 1;
  private static final int COLUMNS_IN_MAIN = 2;
  private static final int PLUGIN_DSC_MAX_WIDTH = 180;
  private static final int PLUGIN_DSC_MAX_ROWS = 2;
  private static final int PLUGIN_NAME_MAX_WIDTH = 180;
  private static final int PLUGIN_NAME_MAX_ROWS = 2;
  private static final int MAX_TOOLTIP_WIDTH = 400;

  private static final Dimension ACTION_BUTTON_SIZE = new Dimension(78, 78);
  private static final Dimension PLUGIN_LOGO_SIZE = new Dimension(16, 16);
  private static final Dimension LEARN_MORE_SIZE = new Dimension(26, 26);
  private static final Dimension OPEN_PLUGIN_MANAGER_SIZE = new Dimension(166, 31);

  private static final String LEARN_MORE_ICON = "/general/learnMore.png";
  private static final String OPEN_PLUGINS_ICON = "/general/openPluginManager.png";
  private static final String CAPTION_IMAGE = "/general/welcomeCaption.png";
  private static final String DEVELOP_SLOGAN = "/general/developSlogan.png";
  private static final String NEW_PROJECT_ICON = "/general/createNewProject.png";
  private static final String REOPEN_RECENT_ICON = "/general/reopenRecentProject.png";
  private static final String FROM_VCS_ICON = "/general/getProjectfromVCS.png";
  private static final String READ_HELP_ICON = "/general/readHelp.png";
  private static final String KEYMAP_ICON = "/general/defaultKeymap.png";
  private static final String KEYMAP_URL = PathManager.getHomePath() + "/help/4.5_ReferenceCard.pdf";
  private static final String DEFAULT_ICON_PATH = "/general/configurableDefault.png";

  private static final Font TEXT_FONT = new Font("Tahoma", Font.PLAIN, 11);
  private static final Font LINK_FONT = new Font("Tahoma", Font.BOLD, 12);
  private static final Font CAPTION_FONT = new Font("Tahoma", Font.BOLD, 18);

  private static final Color CAPTION_COLOR = new Color(47, 67, 96);
  private static final Color PLUGINS_PANEL_COLOR = new Color(229, 229, 229);
  private static final Color MAIN_PANEL_COLOR = new Color(210, 213, 226);
  private static final Color BUTTON_PUSHED_COLOR = new Color(130, 146, 185);
  private static final Color BUTTON_POPPED_COLOR = new Color(181, 190, 214);
  private static final Color MAIN_PANEL_BACKGROUND = new Color(238, 238, 238);
  private static final Color LEAR_MORE_BUTTON_COLOR = new Color(238, 238, 238);
  private static final Color GRAY_BORDER_COLOR = new Color(177, 177, 177);
  private static final Color CAPTION_BACKGROUND = new Color(24, 52, 146);
  private static final Color ACTION_BUTTON_COLOR = new Color(201, 205, 217);
  private static final Color ACTION_BUTTON_BORDER_COLOR = new Color(166, 170, 182);
  private static final Color WHITE_BORDER_COLOR = new Color(255, 255, 255);

  private int myQuickStartCount = 0;
  private int myQuickStartIdx = -1;
  private int myDocsCount = 0;
  private int myDocsIdx = -1;
  private int myPluginsButtonsCount = 0;
  private int myPluginsIdx = -1;

  private WelcomeScreen() {

    // Create Plugins Panel
    myPluginsPanel = new PluginsPanel(new GridBagLayout());
    myPluginsPanel.setBackground(PLUGINS_PANEL_COLOR);

    JLabel pluginsCaption = new JLabel("Plugins");
    pluginsCaption.setFont(CAPTION_FONT);
    pluginsCaption.setForeground(CAPTION_COLOR);

    JLabel installedPluginsCaption = new JLabel("Installed Plugins");
    installedPluginsCaption.setFont(LINK_FONT);
    installedPluginsCaption.setForeground(CAPTION_COLOR);

    JPanel pluginsListPanel = new JPanel(new GridBagLayout());
    pluginsListPanel.setBackground(PLUGINS_PANEL_COLOR);

    JPanel topPluginsPanel = new JPanel(new GridBagLayout());
    topPluginsPanel.setBackground(PLUGINS_PANEL_COLOR);

    //Create the list of installed plugins
    PluginDescriptor[] myInstalledPlugins = PluginManager.getPlugins();
    if (myInstalledPlugins == null || myInstalledPlugins.length == 0) {
      addListItemToPlugins(pluginsListPanel, "<html><i>No plugins currently installed.</i></html>", null, null, null, null);
    }
    else {
      for (int i = 0; i < myInstalledPlugins.length; i++) {
        PluginDescriptor plugin = myInstalledPlugins[i];
        addListItemToPlugins(pluginsListPanel, plugin.getName(), plugin.getDescription(), plugin.getVendorLogoPath(),
                             plugin.getPluginClassLoader(), plugin.getUrl());
        // TODO[pti]: check whether plugin has a welcome-action, and if yes, add it to 'ourPluginsWithActions' ArrayList
      }
    }

    GridBagConstraints gBC = new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(17, 25, 0, 0), 0, 0);
    topPluginsPanel.add(pluginsCaption, gBC);

    JLabel emptyLabel_1 = new JLabel();
    emptyLabel_1.setBackground(PLUGINS_PANEL_COLOR);
    gBC = new GridBagConstraints(1, 0, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0);
    topPluginsPanel.add(emptyLabel_1, gBC);

    gBC = new GridBagConstraints(2, 0, 1, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(13, 0, 0, 10), 0, 0);
    MyActionButton openPluginManager = new PluginsActionButton(null, OPEN_PLUGINS_ICON, null) {
      protected void onPress(InputEvent e) {
        ShowSettingsUtil.getInstance().editConfigurable(myPluginsPanel, PluginManagerConfigurable.getInstance());
      }

      public Dimension getMaximumSize() {
        return OPEN_PLUGIN_MANAGER_SIZE;
      }

      public Dimension getMinimumSize() {
        return OPEN_PLUGIN_MANAGER_SIZE;
      }

      public Dimension getPreferredSize() {
        return OPEN_PLUGIN_MANAGER_SIZE;
      }
    };
    openPluginManager.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    topPluginsPanel.add(openPluginManager, gBC);
    openPluginManager.setupWithinPanel(myPluginsPanel, PLUGINS_GROUP, myPluginsButtonsCount, 0);
    myPluginsButtonsCount++;

    gBC = new GridBagConstraints(0, 1, 1, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(15, 25, 0, 0), 0, 0);
    topPluginsPanel.add(installedPluginsCaption, gBC);

    JLabel emptyLabel_2 = new JLabel();
    emptyLabel_2.setBackground(PLUGINS_PANEL_COLOR);
    gBC = new GridBagConstraints(1, 1, 2, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0);
    topPluginsPanel.add(emptyLabel_2, gBC);

    gBC = new GridBagConstraints(0, 0, 1, 1, 0.5, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0);
    myPluginsPanel.add(topPluginsPanel, gBC);

    gBC = new GridBagConstraints(0, 1, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, new Insets(0, 5, 0, 0), 0, 0);
    myPluginsPanel.add(pluginsListPanel, gBC);

    JPanel emptyPanel_1 = new JPanel();
    emptyPanel_1.setBackground(PLUGINS_PANEL_COLOR);

    gBC = new GridBagConstraints(0, 2, 1, 1, 1, 1, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0);
    myPluginsPanel.add(emptyPanel_1, gBC);

    JScrollPane myPluginsScrollPane = ScrollPaneFactory.createScrollPane(myPluginsPanel);
    myPluginsScrollPane.setBorder(BorderFactory.createLineBorder(GRAY_BORDER_COLOR));
    myPluginsScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    myPluginsScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);


    // Create Main Manel for Quick Start and Documentation
    myMainPanel = new JPanel(new GridBagLayout());
    myMainPanel.setBackground(MAIN_PANEL_COLOR);

    // Create Quick Start sub-panel
    JPanel quickStartPanel = new JPanel(new GridBagLayout());
    quickStartPanel.setBackground(MAIN_PANEL_COLOR);

    JLabel quickStartCaption = new JLabel("Quick Start");
    quickStartCaption.setFont(CAPTION_FONT);
    quickStartCaption.setForeground(CAPTION_COLOR);

    gBC = new GridBagConstraints(0, 0, 2, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(20, 0, 5, 0), 0, 0);
    quickStartPanel.add(quickStartCaption, gBC);

    MyActionButton newProject = new MyActionButton(null, NEW_PROJECT_ICON, null) {
      protected void onPress(InputEvent e) {
        ProjectUtil.createNewProject(null);
      }
    };
    addButtonToQuickStart(quickStartPanel, newProject, "Create New Project", "Start the \"New Project\" Wizard that will guide you through " +
                                                                             "the steps that are necessary to create a new project.");

    MyActionButton openRecentProject = new ButtonWithExtension(null, REOPEN_RECENT_ICON, null) {
      protected void onPress(InputEvent e, final MyActionButton button) {
        final ActionManager actionManager = ActionManager.getInstance();
        final AnAction action = new RecentProjectsAction();
        action.actionPerformed(new AnActionEvent(e, new DataContext() {
          public Object getData(String dataId) {
            if (DataConstants.PROJECT.equals(dataId)) {
              return null;
            }
            return button;
          }
        }, ActionPlaces.UNKNOWN, new PresentationFactory().getPresentation(action), actionManager, 0));
      }
    };
    addButtonToQuickStart(quickStartPanel, openRecentProject, "Reopen Recent Project...", "You can open one of the most recent " +
                                                                                          "projects you were working with. Click the icon " +
                                                                                          "or link to select a project from the list.");

    MyActionButton getFromVCS = new ButtonWithExtension(null, FROM_VCS_ICON, null) {
      protected void onPress(InputEvent e, final MyActionButton button) {
        final ActionManager actionManager = ActionManager.getInstance();
        final AnAction action = new GetFromVcsAction();
        action.actionPerformed(new AnActionEvent(e, new DataContext() {
          public Object getData(String dataId) {
            if (DataConstants.PROJECT.equals(dataId)) {
              return null;
            }
            return button;
          }
        }, ActionPlaces.UNKNOWN, new PresentationFactory().getPresentation(action), actionManager, 0));
      }
    };
    addButtonToQuickStart(quickStartPanel, getFromVCS, "Get Project From Version Control...", "You can check out an entire project from " +
                                                                                              "a Version Control System. Click the icon to " +
                                                                                              "select CVS or Subversion.");

    // Create Documentation panel
    JPanel docsPanel = new JPanel(new GridBagLayout());
    docsPanel.setBackground(MAIN_PANEL_COLOR);

    JLabel docsCaption = new JLabel("Documentation");
    docsCaption.setFont(CAPTION_FONT);
    docsCaption.setForeground(CAPTION_COLOR);

    gBC = new GridBagConstraints(0, 0, 2, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(20, 0, 5, 0), 0, 0);
    docsPanel.add(docsCaption, gBC);

    MyActionButton readHelp = new MyActionButton(null, READ_HELP_ICON, null) {
      protected void onPress(InputEvent e) {
        HelpManagerImpl.getInstance().invokeHelp("");
      }
    };
    addButtonToDocs(docsPanel, readHelp, "Read Help", "Open IntelliJ IDEA \"Help Topics\" in a new window.");

    MyActionButton defaultKeymap = new MyActionButton(null, KEYMAP_ICON, null) {
      protected void onPress(InputEvent e) {
        try {
          BrowserUtil.launchBrowser(KEYMAP_URL);
        }
        catch (IllegalThreadStateException ex) {
          // it's not a problem
        }
      }
    };
    addButtonToDocs(docsPanel, defaultKeymap, "Default Keymap", "Open PDF file with the default keymap reference card.");

// TODO[pti]: before adding Quick Start and Documentation panes to the main panel, check for plugins with welcome actions and add them to the appropriate panes

    JPanel emptyPanel_2 = new JPanel();
    emptyPanel_2.setBackground(MAIN_PANEL_COLOR);

    quickStartPanel.add(emptyPanel_2,
                        new GridBagConstraints(0, myQuickStartIdx + 2, 2, 1, 1, 1, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));

    JPanel emptyPanel_3 = new JPanel();
    emptyPanel_3.setBackground(MAIN_PANEL_COLOR);

    docsPanel.add(emptyPanel_3,
              new GridBagConstraints(0, myDocsIdx + 2, 2, 1, 1, 1, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));

    // Fill Main Panel with Quick Start and Documentation lists
    gBC = new GridBagConstraints(0, 0, 1, 1, 1, 1, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, new Insets(0, 30, 0, 0), 0, 0);
    myMainPanel.add(quickStartPanel, gBC);

    gBC = new GridBagConstraints(1, 0, 1, 1, 1, 1, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, new Insets(0, 15, 0, 0), 0, 0);
    myMainPanel.add(docsPanel, gBC);

    myMainPanel.setPreferredSize(new Dimension(650, 450));

    JScrollPane myMainScrollPane = ScrollPaneFactory.createScrollPane(myMainPanel);
    myMainScrollPane.setBorder(null);
    myMainScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    myMainScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

    // Create caption pane
    JPanel topPanel = new JPanel(new GridBagLayout()) {
      public void paint(Graphics g) {
        Icon welcome = IconLoader.getIcon(CAPTION_IMAGE);
        welcome.paintIcon(null, g, 0, 0);
        g.setColor(CAPTION_BACKGROUND);
        g.fillRect(welcome.getIconWidth(), 0, this.getWidth() - welcome.getIconWidth(), welcome.getIconHeight());
        super.paint(g);
      }
    };
    topPanel.setOpaque(false);

    JPanel transparentTopPanel = new JPanel();
    transparentTopPanel.setOpaque(false);

    topPanel.add(transparentTopPanel,
                            new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));
    topPanel.add(new JLabel(IconLoader.getIcon(DEVELOP_SLOGAN)),
                            new GridBagConstraints(1, 0, 1, 1, 0, 0, GridBagConstraints.SOUTHWEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 10), 0, 0));

    // Create the base welcome panel
    myWelcomePanel = new JPanel(new GridBagLayout());
    myWelcomePanel.setBackground(MAIN_PANEL_BACKGROUND);

    gBC = new GridBagConstraints(0, 0, 3, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(10, 5, 0, 5), 0, 0);
    myWelcomePanel.add(topPanel, gBC);

    gBC = new GridBagConstraints(0, 1, 2, 1, 1.4, 1, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, new Insets(15, 15, 15, 0), 0, 0);
    myWelcomePanel.add(myMainScrollPane, gBC);

    gBC = new GridBagConstraints(2, 1, 1, 1, 0.6, 1, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, new Insets(15, 15, 15, 15), 0, 0);
    myWelcomePanel.add(myPluginsScrollPane, gBC);
  }

  public static JPanel createWelcomePanel() {
    return new WelcomeScreen().myWelcomePanel;
  }

  public void addButtonToQuickStart(JPanel panel, final MyActionButton button, String commandLink, String description) {
    final int y = myQuickStartIdx += 2;
    GridBagConstraints gBC =
      new GridBagConstraints(0, y, 1, 2, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, ICON_INSETS, 5, 5);

    button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    panel.add(button, gBC);
    button.setupWithinPanel(myMainPanel, MAIN_GROUP, myQuickStartCount, 0);
    myQuickStartCount++;

    JLabel name = new JLabel("<html><nobr><u>" + commandLink + "</u></nobr></html>");
    name.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        button.onPress(e);
      }

    });
    name.setForeground(CAPTION_COLOR);
    name.setFont(LINK_FONT);
    name.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

    gBC = new GridBagConstraints(1, y, 1, 1, 0, 0, GridBagConstraints.SOUTHWEST, GridBagConstraints.NONE, new Insets(15, 15, 0, 0), 5, 0);
    panel.add(name, gBC);

    description = "<HTML>" + description + "</html>";
    JLabel shortDescription = new JLabel(description);
    shortDescription.setFont(TEXT_FONT);

    gBC = new GridBagConstraints(1, y + 1, 1, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(7, 15, 0, 30), 5, 0);
    panel.add(shortDescription, gBC);
  }

  public void addButtonToDocs(JPanel panel, final MyActionButton button, String commandLink, String description) {
    final int y = myDocsIdx += 2;
    GridBagConstraints gBC =
      new GridBagConstraints(0, y, 1, 2, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, ICON_INSETS, 5, 5);

    button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    panel.add(button, gBC);
    button.setupWithinPanel(myMainPanel, MAIN_GROUP, myDocsCount, 1);
    myDocsCount++;

    JLabel name = new JLabel("<html><nobr><u>" + commandLink + "</u></nobr></html>");
    name.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        button.onPress(e);
      }

    });
    name.setForeground(CAPTION_COLOR);
    name.setFont(LINK_FONT);
    name.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

    gBC = new GridBagConstraints(1, y, 1, 1, 0, 0, GridBagConstraints.SOUTHWEST, GridBagConstraints.NONE, new Insets(15, 15, 0, 0), 5, 0);
    panel.add(name, gBC);

    description = "<HTML>" + description + "</html>";
    JLabel shortDescription = new JLabel(description);
    shortDescription.setFont(TEXT_FONT);

    gBC = new GridBagConstraints(1, y + 1, 1, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(7, 15, 0, 30), 5, 0);
    panel.add(shortDescription, gBC);
  }

  public void addListItemToPlugins(JPanel panel, String name, String description, String iconPath, ClassLoader pluginClassLoader, final String url) {

    if (StringUtil.isEmptyOrSpaces(name)) {
      return;
    }
    else {
      name = name.trim();
    }

    final int y = myPluginsIdx += 2;
    Icon logoImage;

    // Check the iconPath and insert empty icon in case of empty or invalid value
    if (StringUtil.isEmptyOrSpaces(iconPath)) {
      logoImage = EmptyIcon.create(PLUGIN_LOGO_SIZE.width, PLUGIN_LOGO_SIZE.height);
    }
    else {
      logoImage = IconLoader.findIcon(iconPath, pluginClassLoader);
      if (logoImage == null) logoImage = EmptyIcon.create(PLUGIN_LOGO_SIZE.width, PLUGIN_LOGO_SIZE.height);
    }
    JLabel imageLabel = new JLabel(logoImage);
    GridBagConstraints gBC = new GridBagConstraints(0, y, 1, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
                                                    new Insets(15, 20, 0, 0), 0, 0);
    panel.add(imageLabel, gBC);

    String shortenedName = adjustStringBreaksByWidth(name, LINK_FONT, false, PLUGIN_NAME_MAX_WIDTH, PLUGIN_NAME_MAX_ROWS);
    JLabel logoName = new JLabel(shortenedName);
    logoName.setFont(LINK_FONT);
    logoName.setForeground(CAPTION_COLOR);
    if (shortenedName.endsWith("...</html>")) {
      logoName.setToolTipText(adjustStringBreaksByWidth(name, UIManager.getFont("ToolTip.font"), false, MAX_TOOLTIP_WIDTH, 0));
    }

    gBC = new GridBagConstraints(1, y, 1, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
                                                    new Insets(15, 7, 0, 0), 0, 0);
    panel.add(logoName, gBC);

    if (!StringUtil.isEmpty(description)) {
      description = description.trim();
      if (description.startsWith("<html>")) {
        description = description.replaceAll("<html>", "");
        if (description.endsWith("</html>")) {
          description = description.replaceAll("</html>", "");
        }
      }
      description = description.replaceAll("\\n", "");
      String shortenedDcs = adjustStringBreaksByWidth(description, TEXT_FONT, false, PLUGIN_DSC_MAX_WIDTH, PLUGIN_DSC_MAX_ROWS);
      JLabel pluginDescription = new JLabel(shortenedDcs);
      pluginDescription.setFont(TEXT_FONT);
      if (shortenedDcs.endsWith("...</html>")) {
        pluginDescription.setToolTipText(adjustStringBreaksByWidth(description, UIManager.getFont("ToolTip.font"), false, MAX_TOOLTIP_WIDTH, 0));
      }

      gBC = new GridBagConstraints(1, y + 1, 1, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL,
                                   new Insets(5, 7, 0, 0), 5, 0);
      panel.add(pluginDescription, gBC);
    }

    if (!StringUtil.isEmptyOrSpaces(url)) {
      gBC = new GridBagConstraints(2, y, 1, 2, 0, 0, GridBagConstraints.EAST, GridBagConstraints.NONE, new Insets(0, 7, 0, 0), 0, 0);
      JLabel label = new JLabel("<html><nobr><u>Learn More...</u></nobr></html>");
      label.setFont(TEXT_FONT);
      label.setForeground(CAPTION_COLOR);
      label.setToolTipText(url);
      label.addMouseListener(new MouseAdapter() {
        public void mouseClicked(MouseEvent e) {
          try {
            BrowserUtil.launchBrowser(url);
          }
          catch (IllegalThreadStateException ex) {
            // it's not a problem
          }
        }
      });
      label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      panel.add(label, gBC);

      gBC = new GridBagConstraints(3, y, 1, 2, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 7, 0, 10), 0, 0);
      MyActionButton learnMore = new PluginsActionButton(null, LEARN_MORE_ICON, null) {
        protected void onPress(InputEvent e) {
          try {
            BrowserUtil.launchBrowser(url);
          }
          catch (IllegalThreadStateException ex) {
            // it's not a problem
          }
        }
      };
      learnMore.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      panel.add(learnMore, gBC);
      learnMore.setupWithinPanel(myPluginsPanel, PLUGINS_GROUP, myPluginsButtonsCount, 0);
      myPluginsButtonsCount++;
    }
    else {
      gBC = new GridBagConstraints(2, y, 2, 2, 1, 0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 10), 0, 0);
      JPanel emptyPane = new JPanel();
      emptyPane.setBackground(PLUGINS_PANEL_COLOR);
      panel.add(emptyPane, gBC);
    }
  }

  /**
   * This method checks the width of the given string with given font applied, breaks the string into the specified number of lines if necessary,
   * and/or cuts it, so that the string does not exceed the given width (with ellipsis concatenated at the end if needed).
   * Returns the resulting or original string surrounded by html tags.
   * @param string not <code>null</code> {@link String String} value, otherwise the "Not specified." string is returned.
   * @return the resulting or original string ({@link String String}) surrounded by <code>html</html> tags.
   * @param font not <code>null</code> {@link Font Font} object.
   * @param isAntiAliased <code>boolean</code> value to denote whether the font is antialiased or not.
   * @param maxWidth <code>int</code> value specifying maximum width of the resulting string in pixels.
   * @param maxRows <code>int</code> value spesifying the number of rows. If the value is positive, the string is modified to not exceed
   * the specified number, and method adds an ellipsis instead of the exceeding part. If the value is zero or negative, the entire string is broken
   * into lines until its end.
   */
  private String adjustStringBreaksByWidth(String string,
                                           final Font font,
                                           final boolean isAntiAliased,
                                           final int maxWidth,
                                           final int maxRows) {

    String modifiedString = string.trim();
    if (StringUtil.isEmpty(modifiedString)) {
      return "<html>Not specified.</html>";
    }
    Rectangle2D r = font.getStringBounds(string, new FontRenderContext(new AffineTransform(), isAntiAliased, false));

    if (r.getWidth() > maxWidth) {

      String prefix = "";
      String suffix = string.trim();
      int maxIdxPerLine = (int)(maxWidth / r.getWidth() * string.length());
      int lengthLeft = string.length();
      int rows = maxRows;
      if (rows <= 0) {
        rows = string.length() / maxIdxPerLine + 1;
      }

      while (lengthLeft > maxIdxPerLine && rows > 1) {
        int i;
        for (i = maxIdxPerLine; i > 0; i--) {
          if (suffix.charAt(i) == ' ') {
            prefix += suffix.substring(0, i) + "<br>";
            suffix = suffix.substring(i + 1, suffix.length());
            lengthLeft = suffix.length();
            if (maxRows > 0) {
              rows--;
            }
            else {
              rows = lengthLeft / maxIdxPerLine + 1;
            }
            break;
          }
        }
        if (i == 0) {
          if (rows > 1 && maxRows <= 0) {
            prefix += suffix.substring(0, maxIdxPerLine) + "<br>";
            suffix = suffix.substring(maxIdxPerLine, suffix.length());
            lengthLeft = suffix.length();
            rows--;
          }
          else {
            break;
          }
        }
      }
      if (suffix.length() > maxIdxPerLine) {
        suffix = suffix.substring(0, maxIdxPerLine - 3) + "...";
      }
      modifiedString = prefix + suffix;
    }
    return "<html>" + modifiedString + "</html>";
  }

  private abstract class MyActionButton extends JComponent implements ActionButtonComponent {
    private int myGroupIdx;
    private int myRowIdx;
    private int myColumnIdx;
    private String myIconPath;
    private String myDisplayName;
    private Icon myIcon;
    private KeyStroke myShortcut;

    private MyActionButton(KeyStroke shortcut, String iconPath, String displayName) {
      myShortcut = shortcut;
      myIconPath = iconPath;
      myDisplayName = displayName;
      myIcon = createIcon();
    }

    private void setupWithinPanel(JPanel panel, int groupIdx, int rowIdx, int columnIdx) {
      myGroupIdx = groupIdx;
      myRowIdx = rowIdx;
      myColumnIdx = columnIdx;
      if (groupIdx == MAIN_GROUP) {
        ourMainButtons.add(MyActionButton.this);
      }
      else if (groupIdx == PLUGINS_GROUP) {
        ourPluginButtons.add(MyActionButton.this);
      }
      setToolTipText(null);
      setupListeners(panel);
    }

    private Icon createIcon() {
      return new LabeledIcon(getIcon(), getDisplayName(),
                             myShortcut == null
                             ? null
                             : " (" + KeyEvent.getKeyText(myShortcut.getKeyCode()) + ")");
    }

    protected int getColumnIdx() {
      return myColumnIdx;
    }

    protected int getGroupIdx() {
      return myGroupIdx;
    }

    protected int getRowIdx() {
      return myRowIdx;
    }

    protected String getDisplayName() {
      return myDisplayName != null ? myDisplayName : "";
    }

    protected Icon getIcon() {
      if (myIconPath == null) myIconPath = DEFAULT_ICON_PATH;
      Icon icon = IconLoader.getIcon(myIconPath);
      return icon != null ? icon : IconLoader.getIcon(DEFAULT_ICON_PATH);
    }

    protected KeyStroke getShortcut() {
      return myShortcut;
    }

    public Dimension getMaximumSize() {
      return ACTION_BUTTON_SIZE;
    }

    public Dimension getMinimumSize() {
      return ACTION_BUTTON_SIZE;
    }

    public Dimension getPreferredSize() {
      return ACTION_BUTTON_SIZE;
    }

    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      ActionButtonLook look = ActionButtonLook.IDEA_LOOK;
      paintBackground(g);
      look.paintIcon(g, this, myIcon);
      paintBorder(g);
    }

    protected void paintBackground(Graphics g) {
      Dimension dimension = getSize();
      int state = getPopState();
      if (state != ActionButtonComponent.NORMAL) {
        if (state == ActionButtonComponent.POPPED) {
          g.setColor(BUTTON_POPPED_COLOR);
          g.fillRect(0, 0, dimension.width, dimension.height);
        }
        else {
          g.setColor(BUTTON_PUSHED_COLOR);
          g.fillRect(0, 0, dimension.width, dimension.height);
        }
      }
      else {
        g.setColor(ACTION_BUTTON_COLOR);
        g.fillRect(0, 0, dimension.width, dimension.height);
      }
      if (state == ActionButtonComponent.PUSHED) {
        g.setColor(BUTTON_PUSHED_COLOR);
        g.fillRect(0, 0, dimension.width, dimension.height);
      }
    }

    protected void paintBorder(Graphics g) {
      Rectangle rectangle = new Rectangle(getSize());
      Color color = ACTION_BUTTON_BORDER_COLOR;
      g.setColor(color);
      g.drawLine(rectangle.x, rectangle.y, rectangle.x, (rectangle.y + rectangle.height) - 1);
      g.drawLine(rectangle.x, rectangle.y, (rectangle.x + rectangle.width) - 1, rectangle.y);
      g.drawLine((rectangle.x + rectangle.width) - 1, rectangle.y, (rectangle.x + rectangle.width) - 1,
                                                      (rectangle.y + rectangle.height) - 1);
      g.drawLine(rectangle.x, (rectangle.y + rectangle.height) - 1, (rectangle.x + rectangle.width) - 1,
                 (rectangle.y + rectangle.height) - 1);
    }

    public int getPopState() {
      if (myKeypressedButton == this) return ActionButtonComponent.PUSHED;
      if (myKeypressedButton != null) return ActionButtonComponent.NORMAL;
      if (mySelectedColumn == myColumnIdx &&
          mySelectedRow == myRowIdx &&
          mySelectedGroup == myGroupIdx) {
        return ActionButtonComponent.POPPED;
      }
      return ActionButtonComponent.NORMAL;
    }

    private void setupListeners(final JPanel panel) {
      addMouseListener(new MouseAdapter() {
        public void mousePressed(MouseEvent e) {
          myKeypressedButton = MyActionButton.this;
          panel.repaint();
        }

        public void mouseReleased(MouseEvent e) {
          if (myKeypressedButton == MyActionButton.this) {
            myKeypressedButton = null;
            onPress(e);
          }
          else {
            myKeypressedButton = null;
          }

          panel.repaint();
        }

        public void mouseExited(MouseEvent e) {
          mySelectedColumn = -1;
          mySelectedRow = -1;
          mySelectedGroup = -1;
          panel.repaint();
        }
      });

      addMouseMotionListener(new MouseMotionListener() {
        public void mouseDragged(MouseEvent e) {
        }

        public void mouseMoved(MouseEvent e) {
          mySelectedColumn = myColumnIdx;
          mySelectedRow = myRowIdx;
          mySelectedGroup = myGroupIdx;
          panel.repaint();
        }
      });
    }

    protected abstract void onPress(InputEvent e);

  }

  private abstract class ButtonWithExtension extends MyActionButton {
    private ButtonWithExtension(KeyStroke shortcut, String iconPath, String displayName) {
      super(shortcut, iconPath, displayName);
    }

    protected void onPress(InputEvent e) {
      onPress(e, this);
    }

    protected abstract void onPress(InputEvent e, MyActionButton button);
  }

  private abstract class PluginsActionButton extends MyActionButton {
    protected PluginsActionButton(KeyStroke shortcut, String iconPath, String displayName) {
      super(shortcut, iconPath, displayName);
    }

    public Dimension getMaximumSize() {
      return LEARN_MORE_SIZE;
    }

    public Dimension getMinimumSize() {
      return LEARN_MORE_SIZE;
    }

    public Dimension getPreferredSize() {
      return LEARN_MORE_SIZE;
    }

    protected void paintBackground(Graphics g) {
      Dimension dimension = getSize();
      int state = getPopState();
      if (state != ActionButtonComponent.NORMAL) {
        if (state == ActionButtonComponent.POPPED) {
          g.setColor(BUTTON_POPPED_COLOR);
          g.fillRect(0, 0, dimension.width, dimension.height);
        }
        else {
          g.setColor(BUTTON_PUSHED_COLOR);
          g.fillRect(0, 0, dimension.width, dimension.height);
        }
      }
      else {
        g.setColor(LEAR_MORE_BUTTON_COLOR);
        g.fillRect(0, 0, dimension.width, dimension.height);
      }
      if (state == ActionButtonComponent.PUSHED) {
        g.setColor(BUTTON_PUSHED_COLOR);
        g.fillRect(0, 0, dimension.width, dimension.height);
      }
    }

    protected void paintBorder(Graphics g) {
      Rectangle rectangle = new Rectangle(getSize());
      Color color = WHITE_BORDER_COLOR;
      g.setColor(color);
      g.drawLine(rectangle.x, rectangle.y, rectangle.x, (rectangle.y + rectangle.height) - 1);
      g.drawLine(rectangle.x, rectangle.y, (rectangle.x + rectangle.width) - 1, rectangle.y);
      color = GRAY_BORDER_COLOR;
      g.setColor(color);
      g.drawLine((rectangle.x + rectangle.width) - 1, rectangle.y + 1, (rectangle.x + rectangle.width) - 1,
        (rectangle.y + rectangle.height) - 1);
      g.drawLine(rectangle.x + 1, (rectangle.y + rectangle.height) - 1, (rectangle.x + rectangle.width) - 1,
        (rectangle.y + rectangle.height) - 1);
    }
  }
}
