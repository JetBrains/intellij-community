package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.help.impl.HelpManagerImpl;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.DataManager;
import com.intellij.ide.actions.OpenProjectAction;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerConfigurable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionButtonLook;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.LabeledIcon;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.UIBundle;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.PixelGrabber;
import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

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

  private static final Insets ICON_INSETS = new Insets(15, 30, 15, 0);
  private static final Insets ACTION_GROUP_CAPTION_INSETS = new Insets(20, 30, 5, 0);

  private MyActionButton myKeypressedButton = null;
  private int mySelectedRow = -1;
  private int mySelectedColumn = -1;
  private int mySelectedGroup = -1;
  private static final int MAIN_GROUP = 0;
  private static final int PLUGINS_GROUP = 1;
  private static final int PLUGIN_DSC_MAX_WIDTH = 260;
  private static final int PLUGIN_DSC_MAX_ROWS = 2;
  private static final int PLUGIN_NAME_MAX_WIDTH = 180;
  private static final int PLUGIN_NAME_MAX_ROWS = 2;
  private static final int MAX_TOOLTIP_WIDTH = 400;
  private static final int ACTION_BUTTON_PADDING = 5;

  private static final Dimension ACTION_BUTTON_SIZE = new Dimension(66, 66);
  private static final Dimension PLUGIN_LOGO_SIZE = new Dimension(16, 16);
  private static final Dimension LEARN_MORE_SIZE = new Dimension(26, 26);
  private static final Dimension OPEN_PLUGIN_MANAGER_SIZE = new Dimension(166, 31);

  private static final Icon LEARN_MORE_ICON = IconLoader.getIcon("/general/learnMore.png");
  private static final Icon OPEN_PLUGINS_ICON = IconLoader.getIcon("/general/openPluginManager.png");
  private static final Icon NEW_PROJECT_ICON = IconLoader.getIcon("/general/createNewProject.png");
  private static final Icon OPEN_PROJECT_ICON = IconLoader.getIcon("/general/openProject.png");
  private static final Icon REOPEN_RECENT_ICON = IconLoader.getIcon("/general/reopenRecentProject.png");
  private static final Icon FROM_VCS_ICON = IconLoader.getIcon("/general/getProjectfromVCS.png");
  private static final Icon READ_HELP_ICON = IconLoader.getIcon("/general/readHelp.png");
  private static final Icon PLUGIN_ICON = IconLoader.getIcon("/general/pluginManager.png");
  private static final Icon DEFAULT_ICON = IconLoader.getIcon("/general/configurableDefault.png");
  private static Icon CAPTION_IMAGE;
  private static Icon DEVELOPER_SLOGAN;

  @NonNls private static final String PLUGIN_URL = PathManager.getHomePath() + "/Plugin Development Readme.html";
  @NonNls private static final String PLUGIN_WEBSITE = "http://www.jetbrains.com/idea/plugins/plugin_developers.html";

  @NonNls protected static final String TAHOMA_FONT_NAME = "Tahoma";
  private static final Font TEXT_FONT = new Font(TAHOMA_FONT_NAME, Font.PLAIN, 11);
  private static final Font LINK_FONT = new Font(TAHOMA_FONT_NAME, Font.BOLD, 12);
  private static final Font GROUP_CAPTION_FONT = new Font(TAHOMA_FONT_NAME, Font.BOLD, 18);

  private static final Color CAPTION_COLOR = new Color(47, 67, 96);
  private static final Color PLUGINS_PANEL_COLOR = new Color(229, 229, 229);
  private static final Color MAIN_PANEL_COLOR = new Color(210, 213, 226);
  private static final Color BUTTON_PUSHED_COLOR = new Color(130, 146, 185);
  private static final Color BUTTON_POPPED_COLOR = new Color(181, 190, 214);
  private static final Color MAIN_PANEL_BACKGROUND = new Color(238, 238, 238);
  private static final Color LEARN_MORE_BUTTON_COLOR = new Color(238, 238, 238);
  private static final Color GRAY_BORDER_COLOR = new Color(177, 177, 177);
  private static Color CAPTION_BACKGROUND = new Color(23, 52, 150);
  private static final Color ACTION_BUTTON_COLOR = new Color(201, 205, 217);
  private static final Color ACTION_BUTTON_BORDER_COLOR = new Color(166, 170, 182);
  private static final Color WHITE_BORDER_COLOR = new Color(255, 255, 255);

  private int myPluginsButtonsCount = 0;
  private int myPluginsIdx = -1;
  @NonNls protected static final String ___HTML_SUFFIX = "...</html>";
  @NonNls protected static final String ESC_NEW_LINE = "\\n";

  private class ActionGroupDescriptor {
    private int myIdx = -1;
    private int myCount = 0;
    private JPanel myPanel;
    private final int myColumnIdx;
    @NonNls protected static final String HTML_PREFIX = "<html>";
    @NonNls protected static final String HTML_SUFFIX = "</html>";

    public ActionGroupDescriptor(final String caption, final int columnIndex) {
      JPanel panel = new JPanel(new GridBagLayout()) {
        public Dimension getPreferredSize() {
          return getMinimumSize();
        }
      };
      panel.setBackground(MAIN_PANEL_COLOR);

      JLabel actionGroupCaption = new JLabel(caption);
      actionGroupCaption.setFont(GROUP_CAPTION_FONT);
      actionGroupCaption.setForeground(CAPTION_COLOR);

      GridBagConstraints gBC = new GridBagConstraints(0, 0, 2, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, ACTION_GROUP_CAPTION_INSETS, 0, 0);
      panel.add(actionGroupCaption, gBC);
      myPanel = panel;
      myColumnIdx = columnIndex;
    }

    public void addButton(final MyActionButton button, String commandLink, String description) {
      final int y = myIdx += 2;
      GridBagConstraints gBC =
        new GridBagConstraints(0, y, 1, 2, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, ICON_INSETS, ACTION_BUTTON_PADDING, ACTION_BUTTON_PADDING);

      button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      myPanel.add(button, gBC);
      button.setupWithinPanel(myMainPanel, MAIN_GROUP, myCount, myColumnIdx);
      myCount++;

      JLabel name = new JLabel(underlineHtmlText(commandLink));
      name.addMouseListener(new MouseAdapter() {
        public void mouseClicked(MouseEvent e) {
          button.onPress(e);
        }
      });

      name.setForeground(CAPTION_COLOR);
      name.setFont(LINK_FONT);
      name.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

      gBC = new GridBagConstraints(1, y, 1, 1, 0, 0, GridBagConstraints.SOUTHWEST, GridBagConstraints.NONE, new Insets(15, 15, 0, 0), 5, 0);
      myPanel.add(name, gBC);

      description = wrapWithHtml(description);
      JLabel shortDescription = new JLabel(description);
      shortDescription.setFont(TEXT_FONT);

      gBC = new GridBagConstraints(1, y + 1, 1, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(7, 15, 0, 30), 5, 0);
      myPanel.add(shortDescription, gBC);
    }

    private String wrapWithHtml(final String description) {
      return HTML_PREFIX + description + HTML_SUFFIX;
    }

    @SuppressWarnings({"HardCodedStringLiteral"})
    private String underlineHtmlText(final String commandLink) {
      return "<html><nobr><u>" + commandLink + "</u></nobr></html>";
    }

    private void appendActionsFromGroup(final DefaultActionGroup group) {
      final AnAction[] actions = group.getChildren(null);
      for (final AnAction action : actions) {
        appendButtonForAction(action);
      }
    }

    public void appendButtonForAction(final AnAction action) {
      final Presentation presentation = action.getTemplatePresentation();
      final Icon icon = presentation.getIcon();
      final String text = presentation.getText();
      MyActionButton button = new ButtonWithExtension(icon, "") {
        protected void onPress(InputEvent e, MyActionButton button) {
          final ActionManager actionManager = ActionManager.getInstance();
          AnActionEvent evt = new AnActionEvent(
            null,
            DataManager.getInstance().getDataContext(this),
            ActionPlaces.WELCOME_SCREEN,
            action.getTemplatePresentation(),
            actionManager,
            0
          );
          action.beforeActionPerformedUpdate(evt);
          if (evt.getPresentation().isEnabled()) {
            action.actionPerformed(evt);
          }
        }
      };

      addButton(button, text, presentation.getDescription());
    }

    public JPanel getPanel() {
      return myPanel;
    }

    public int getIdx() {
      return myIdx;
    }
  }

  private WelcomeScreen() {
    initApplicationSpecificImages();

    GridBagConstraints gBC;
    final ActionManager actionManager = ActionManager.getInstance();

    // Create caption pane
    JPanel topPanel = createCaptionPane();

    // Create Main Panel for Quick Start and Documentation
    myMainPanel = new WelcomeScrollablePanel(new GridLayout(1, 2));
    myMainPanel.setBackground(MAIN_PANEL_COLOR);
    // Create QuickStarts group of actions
    ActionGroupDescriptor quickStarts = new ActionGroupDescriptor(UIBundle.message("welcome.screen.quick.start.action.group.name"), 0);
    addDefaultQuickStartActions(quickStarts, actionManager);
    // Append plug-in actions to the end of the QuickStart list
    quickStarts.appendActionsFromGroup((DefaultActionGroup)actionManager.getAction(IdeActions.GROUP_WELCOME_SCREEN_QUICKSTART));
    final JPanel quickStartPanel = quickStarts.getPanel();
    // Add empty panel at the end of the QuickStarts panel
    JPanel emptyPanel_2 = new JPanel();
    emptyPanel_2.setBackground(MAIN_PANEL_COLOR);
    quickStartPanel.add(emptyPanel_2, new GridBagConstraints(0, quickStarts.getIdx() + 2, 2, 1, 1, 1, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));

    // Create Documentation group of actions
    ActionGroupDescriptor docsGroup = new ActionGroupDescriptor(UIBundle.message("welcome.screen.documentation.action.group.name"), 1);
    addDefaultDocsActions(docsGroup, actionManager);
    // Append plug-in actions to the end of the QuickStart list
    docsGroup.appendActionsFromGroup((DefaultActionGroup)actionManager.getAction(IdeActions.GROUP_WELCOME_SCREEN_DOC));
    final JPanel docsPanel = docsGroup.getPanel();
    // Add empty panel at the end of the Documentation list
    JPanel emptyPanel_3 = new JPanel();
    emptyPanel_3.setBackground(MAIN_PANEL_COLOR);
    docsPanel.add(emptyPanel_3, new GridBagConstraints(0, docsGroup.getIdx() + 2, 2, 1, 1, 1, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));

    // Add QuickStarts and Docs to main panel
    myMainPanel.add(quickStartPanel);
    myMainPanel.add(docsPanel);

    JScrollPane myMainScrollPane = new JScrollPane(myMainPanel);
    myMainScrollPane.setBorder(null);
    myMainScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    myMainScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

    // Create Plugins Panel
    JScrollPane myPluginsScrollPane = createPluginsPanel();

    // Create Welcome panel
    gBC = new GridBagConstraints(0, 0, 2, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(10, 5, 0, 5), 0, 0);
    myWelcomePanel.add(topPanel, gBC);
    gBC = new GridBagConstraints(0, 1, 1, 1, 0.7, 1, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, new Insets(15, 15, 15, 0), 0, 0);
    myWelcomePanel.add(myMainScrollPane, gBC);
    gBC = new GridBagConstraints(1, 1, 1, 1, 0.3, 1, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, new Insets(15, 15, 15, 15), 0, 0);
    myWelcomePanel.add(myPluginsScrollPane, gBC);
  }

  private static void initApplicationSpecificImages() {
    if (CAPTION_IMAGE == null) {
      ApplicationInfoEx applicationInfoEx = ApplicationInfoEx.getInstanceEx();
      CAPTION_IMAGE = IconLoader.getIcon(applicationInfoEx.getWelcomeScreenCaptionUrl());
      DEVELOPER_SLOGAN = IconLoader.getIcon(applicationInfoEx.getWelcomeScreenDeveloperSloganUrl());

      if (CAPTION_IMAGE instanceof ImageIcon) {
        Image image = ((ImageIcon)CAPTION_IMAGE).getImage();
        final int[] pixels = new int[1];
        final PixelGrabber pixelGrabber = new PixelGrabber(image, CAPTION_IMAGE.getIconWidth() - 1, CAPTION_IMAGE.getIconHeight() - 1, 1, 1, pixels, 0, 1);
        try {
          pixelGrabber.grabPixels();
          CAPTION_BACKGROUND = new Color(pixels[0]);
        }
        catch (InterruptedException e) {
          //ignore exception
        }
      }
    }
  }

  public static JPanel createWelcomePanel() {
    return new WelcomeScreen().myWelcomePanel;
  }

  private JPanel createCaptionPane() {
    JPanel topPanel = new JPanel(new GridBagLayout()) {
      public void paint(Graphics g) {
        Icon welcome = CAPTION_IMAGE;
        welcome.paintIcon(null, g, 0, 0);
        g.setColor(CAPTION_BACKGROUND);
        g.fillRect(welcome.getIconWidth(), 0, getWidth() - welcome.getIconWidth(), welcome.getIconHeight());
        super.paint(g);
      }
    };
    topPanel.setOpaque(false);

    JPanel transparentTopPanel = new JPanel();
    transparentTopPanel.setOpaque(false);

    topPanel.add(transparentTopPanel,
                 new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));
    topPanel.add(new JLabel(DEVELOPER_SLOGAN),
                 new GridBagConstraints(1, 0, 1, 1, 0, 0, GridBagConstraints.SOUTHWEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 10), 0, 0));

    // Create the base welcome panel
    myWelcomePanel = new JPanel(new GridBagLayout());
    myWelcomePanel.setBackground(MAIN_PANEL_BACKGROUND);
    return topPanel;
  }

  private void addDefaultDocsActions(final ActionGroupDescriptor docsGroup, final ActionManager actionManager) {
    MyActionButton readHelp = new MyActionButton(READ_HELP_ICON, null) {
      protected void onPress(InputEvent e) {
        HelpManagerImpl.getInstance().invokeHelp("");
      }
    };
    docsGroup.addButton(readHelp, UIBundle.message("welcome.screen.read.help.action.name"),
                        UIBundle.message("welcome.screen.read.help.action.description", ApplicationNamesInfo.getInstance().getFullProductName()));


    docsGroup.appendButtonForAction(actionManager.getAction(IdeActions.ACTION_KEYMAP_REFERENCE));

    MyActionButton pluginDev = new MyActionButton(PLUGIN_ICON, null) {
      protected void onPress(InputEvent e) {
        try {
          if (new File(PLUGIN_URL).isFile()) {
            BrowserUtil.launchBrowser(PLUGIN_URL);
          }
          else {
            BrowserUtil.launchBrowser(PLUGIN_WEBSITE);
          }
        }
        catch(IllegalStateException ex) {
          // ignore
        }
      }
    };
    docsGroup.addButton(pluginDev, UIBundle.message("welcome.screen.plugin.development.action.name"),
                        UIBundle.message("welcome.screen.plugin.development.action.description", ApplicationNamesInfo.getInstance().getFullProductName()));
  }

  private void addDefaultQuickStartActions(final ActionGroupDescriptor quickStarts, final ActionManager actionManager) {
    MyActionButton newProject = new MyActionButton(NEW_PROJECT_ICON, null) {
      protected void onPress(InputEvent e) {
        ProjectUtil.createNewProject(null);
      }
    };
    quickStarts.addButton(newProject, UIBundle.message("welcome.screen.create.new.project.action.name"),
                          UIBundle.message("welcome.screen.create.new.project.action.description"));

    MyActionButton openProject = new ButtonWithExtension(OPEN_PROJECT_ICON, null) {
      protected void onPress(InputEvent e, final MyActionButton button) {
        final AnAction action = new OpenProjectAction();
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

    quickStarts.addButton(openProject, UIBundle.message("welcome.screen.open.project.action.name"),
                          UIBundle.message("welcome.screen.open.project.action.description", ApplicationNamesInfo.getInstance().getFullProductName()));

    MyActionButton openRecentProject = new ButtonWithExtension(REOPEN_RECENT_ICON, null) {
      protected void onPress(InputEvent e, final MyActionButton button) {
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
    quickStarts.addButton(openRecentProject, UIBundle.message("welcome.screen.reopen.recent.project.action.name"),
                          UIBundle.message("welcome.screen.reopen.recent.project.action.description"));

    MyActionButton getFromVCS = new ButtonWithExtension(FROM_VCS_ICON, null) {
      protected void onPress(InputEvent e, final MyActionButton button) {
        final GetFromVcsAction action = new GetFromVcsAction();
        action.actionPerformed(button, e);
      }
    };

    quickStarts.addButton(getFromVCS, UIBundle.message("welcome.screen.check.out.from.version.control.action.name"),
                          UIBundle.message("welcome.screen.check.out.from.version.control.action.description"));

    /*
    MyActionButton checkForUpdate = new MyActionButton (CHECK_FOR_UPDATE_ICON, null) {
      protected void onPress(InputEvent e) {
        CheckForUpdateAction.actionPerformed(true);
      }
    };

    quickStarts.addButton(checkForUpdate, "Check for Update", ApplicationNamesInfo.getInstance().getFullProductName() +
                                                              " will check for a new available update of itself, " +
                                                              "using your internet connection.");
    */
  }

  private JScrollPane createPluginsPanel() {
    myPluginsPanel = new WelcomeScrollablePanel(new GridBagLayout());
    myPluginsPanel.setBackground(PLUGINS_PANEL_COLOR);

    JLabel pluginsCaption = new JLabel(UIBundle.message("welcome.screen.plugins.panel.plugins.label"));
    pluginsCaption.setFont(GROUP_CAPTION_FONT);
    pluginsCaption.setForeground(CAPTION_COLOR);

    JLabel installedPluginsCaption = new JLabel(UIBundle.message("welcome.screen.plugins.panel.my.plugins.label"));
    installedPluginsCaption.setFont(LINK_FONT);
    installedPluginsCaption.setForeground(CAPTION_COLOR);

    JPanel installedPluginsPanel = new JPanel(new GridBagLayout());
    installedPluginsPanel.setBackground(PLUGINS_PANEL_COLOR);

    JLabel bundledPluginsCaption = new JLabel(UIBundle.message("welcome.screen.plugins.panel.bundled.plugins.label"));
    bundledPluginsCaption.setFont(LINK_FONT);
    bundledPluginsCaption.setForeground(CAPTION_COLOR);

    JPanel bundledPluginsPanel = new JPanel(new GridBagLayout());
    bundledPluginsPanel.setBackground(PLUGINS_PANEL_COLOR);

    JPanel topPluginsPanel = new JPanel(new GridBagLayout());
    topPluginsPanel.setBackground(PLUGINS_PANEL_COLOR);

    GridBagConstraints gBC = new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(17, 25, 0, 0), 0, 0);
    topPluginsPanel.add(pluginsCaption, gBC);

    JLabel emptyLabel_1 = new JLabel();
    emptyLabel_1.setBackground(PLUGINS_PANEL_COLOR);
    gBC = new GridBagConstraints(1, 0, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0);
    topPluginsPanel.add(emptyLabel_1, gBC);

    createListOfPlugins(installedPluginsPanel, bundledPluginsPanel);

    gBC = new GridBagConstraints(2, 0, 1, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(13, 0, 0, 10), 0, 0);
    MyActionButton openPluginManager = new PluginsActionButton(OPEN_PLUGINS_ICON, null) {
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
    myPluginsPanel.add(installedPluginsPanel, gBC);

    JPanel emptyPanel_1 = new JPanel();
    emptyPanel_1.setBackground(PLUGINS_PANEL_COLOR);

    gBC = new GridBagConstraints(0, 2, 1, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(25, 25, 0, 0), 0, 0);
    myPluginsPanel.add(bundledPluginsCaption, gBC);

    gBC = new GridBagConstraints(0, 3, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, new Insets(5, 5, 0, 0), 0, 0);
    myPluginsPanel.add(bundledPluginsPanel, gBC);

    gBC = new GridBagConstraints(0, 4, 1, 1, 1, 1, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0);
    myPluginsPanel.add(emptyPanel_1, gBC);

    JScrollPane myPluginsScrollPane = ScrollPaneFactory.createScrollPane(myPluginsPanel);
    myPluginsScrollPane.setBorder(BorderFactory.createLineBorder(GRAY_BORDER_COLOR));
    myPluginsScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    myPluginsScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
    return myPluginsScrollPane;
  }

  private void createListOfPlugins(final JPanel installedPluginsPanel, final JPanel bundledPluginsPanel) {
    //Create the list of installed plugins
    IdeaPluginDescriptor[] myInstalledPlugins = ApplicationManager.getApplication().getPlugins();

    if (myInstalledPlugins == null || myInstalledPlugins.length == 0) {
      addListItemToPlugins(installedPluginsPanel, makeItalic(UIBundle
        .message("welcome.screen.plugins.panel.no.plugins.currently.installed.message.text")), null, null, null, null);
      addListItemToPlugins(bundledPluginsPanel, makeItalic(UIBundle
        .message("welcome.screen.plugins.panel.all.bundled.plugins.were.uninstalled.message.text")), null, null, null, null);
    }
    else {
      final Comparator<IdeaPluginDescriptor> pluginsComparator = new Comparator<IdeaPluginDescriptor>() {
        public int compare(final IdeaPluginDescriptor o1, final IdeaPluginDescriptor o2) {
          return o1.getName().compareTo(o2.getName());
        }
      };
      Arrays.sort(myInstalledPlugins, pluginsComparator);

      int embeddedPlugins = 0;
      int installedPlugins = 0;
      String preinstalledPrefix = PathManager.getPreinstalledPluginsPath();

      for (IdeaPluginDescriptor plugin : myInstalledPlugins) {
        if (plugin.getPath().getAbsolutePath().startsWith(preinstalledPrefix)) {
          embeddedPlugins++;
          addListItemToPlugins(bundledPluginsPanel, plugin.getName(), plugin.getDescription(), plugin.getVendorLogoPath(),
                               plugin.getPluginClassLoader(), plugin.getUrl());
        }
        else {
          installedPlugins++;
          addListItemToPlugins(installedPluginsPanel, plugin.getName(), plugin.getDescription(), plugin.getVendorLogoPath(),
                               plugin.getPluginClassLoader(), plugin.getUrl());
        }
      }
      if (embeddedPlugins == 0) {
        addListItemToPlugins(bundledPluginsPanel, makeItalic(UIBundle
          .message("welcome.screen.plugins.panel.all.bundled.plugins.were.uninstalled.message.text")), null, null, null, null);
      }
      if (installedPlugins == 0) {
        addListItemToPlugins(installedPluginsPanel, makeItalic(UIBundle
          .message("welcome.screen.plugins.panel.no.plugins.currently.installed.message.text")), null, null, null, null);
      }
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private String makeItalic(final String message) {
    return "<i>" + message + "</i>";
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
      logoImage = new EmptyIcon(PLUGIN_LOGO_SIZE.width, PLUGIN_LOGO_SIZE.height);
    }
    else {
      logoImage = IconLoader.findIcon(iconPath, pluginClassLoader);
      if (logoImage == null) logoImage = new EmptyIcon(PLUGIN_LOGO_SIZE.width, PLUGIN_LOGO_SIZE.height);
    }
    JLabel imageLabel = new JLabel(logoImage);
    GridBagConstraints gBC = new GridBagConstraints(0, y, 1, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
                                                    new Insets(15, 20, 0, 0), 0, 0);
    panel.add(imageLabel, gBC);

    String shortenedName = adjustStringBreaksByWidth(name, LINK_FONT, false, PLUGIN_NAME_MAX_WIDTH, PLUGIN_NAME_MAX_ROWS);
    JLabel logoName = new JLabel(shortenedName);
    logoName.setFont(LINK_FONT);
    logoName.setForeground(CAPTION_COLOR);
    if (shortenedName.endsWith(___HTML_SUFFIX)) {
      logoName.setToolTipText(adjustStringBreaksByWidth(name, UIUtil.getToolTipFont(), false, MAX_TOOLTIP_WIDTH, 0));
    }

    gBC = new GridBagConstraints(1, y, 1, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
                                 new Insets(15, 7, 0, 0), 0, 0);
    panel.add(logoName, gBC);

    if (!StringUtil.isEmpty(description)) {
      description = description.trim();
      if (description.startsWith(ActionGroupDescriptor.HTML_PREFIX)) {
        description = description.replaceAll(ActionGroupDescriptor.HTML_PREFIX, "");
        if (description.endsWith(ActionGroupDescriptor.HTML_SUFFIX)) {
          description = description.replaceAll(ActionGroupDescriptor.HTML_SUFFIX, "");
        }
      }
      description = description.replaceAll(ESC_NEW_LINE, "");
      String shortenedDcs = adjustStringBreaksByWidth(description, TEXT_FONT, false, PLUGIN_DSC_MAX_WIDTH, PLUGIN_DSC_MAX_ROWS);
      JLabel pluginDescription = new JLabel(shortenedDcs);
      pluginDescription.setFont(TEXT_FONT);
      if (shortenedDcs.endsWith(___HTML_SUFFIX)) {
        pluginDescription.setToolTipText(adjustStringBreaksByWidth(description, UIUtil.getToolTipFont(), false, MAX_TOOLTIP_WIDTH, 0));
      }

      gBC = new GridBagConstraints(1, y + 1, 1, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL,
                                   new Insets(5, 7, 0, 0), 5, 0);
      panel.add(pluginDescription, gBC);
    }

    if (!StringUtil.isEmptyOrSpaces(url)) {
      gBC = new GridBagConstraints(2, y + 1, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 7, 0, 10), 0, 0);
      MyActionButton learnMore = new PluginsActionButton(LEARN_MORE_ICON, null) {
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
      learnMore.setToolTipText(UIBundle.message("welcome.screen.plugins.panel.learn.more.tooltip.text"));
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
   * and/or cuts it, so that the string does not exceed the given width (with ellipsis concatenated at the end if needed).<br>
   * It also removes all of the formatting HTML tags, except <b>&lt;br&gt;</b> and <b>&lt;li&gt;</b> (they are used for correct line breaks).
   * Returns the resulting or original string surrounded by <b>&lt;html&gt;</b> tags.
   * @param string not <code>null</code> {@link String String} value, otherwise the "Not specified." string is returned.
   * @return the resulting or original string ({@link String String}) surrounded by <b>&lt;html&gt;</b> tags.
   * @param font not <code>null</code> {@link Font Font} object.
   * @param isAntiAliased <code>boolean</code> value to denote whether the font is antialiased or not.
   * @param maxWidth <code>int</code> value specifying maximum width of the resulting string in pixels.
   * @param maxRows <code>int</code> value spesifying the number of rows. If the value is positive, the string is modified to not exceed
   * the specified number, and method adds an ellipsis instead of the exceeding part. If the value is zero or negative, the entire string is broken
   * into lines until its end.
   */
  @SuppressWarnings({"HardCodedStringLiteral"})
  private String adjustStringBreaksByWidth(String string,
                                           final Font font,
                                           final boolean isAntiAliased,
                                           final int maxWidth,
                                           final int maxRows) {

    string = string.trim();
    if (StringUtil.isEmpty(string)) {
      return "<html>" + UIBundle.message("welcome.script.text.not.specified.message") + "</html>";
    }

    string = string.replaceAll("<li>", " <>&gt; ");
    string = string.replaceAll("<br>", " <>");
    string = string.replaceAll("(<[^>]+?>)", " ");
    string = string.replaceAll("[\\s]{2,}", " ");
    Rectangle2D r = font.getStringBounds(string, new FontRenderContext(new AffineTransform(), isAntiAliased, false));

    if (r.getWidth() > maxWidth) {

      StringBuffer prefix = new StringBuffer();
      String suffix = string;
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
            prefix.append(suffix.substring(0, i)).append("<br>");
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
            prefix.append(suffix.substring(0, maxIdxPerLine)).append("<br>");
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
        suffix = suffix.substring(0, maxIdxPerLine - 3);
        for (int i = suffix.length() - 1; i > 0; i--) {
          if (suffix.charAt(i) == ' ') {
            if ("...".equals(suffix.substring(i - 3, i))) {
              suffix = suffix.substring(0, i - 1);
              break;
            }
            else if (suffix.charAt(i - 1) == '>') {
              //noinspection AssignmentToForLoopParameter
              i--;
            }
            else if (suffix.charAt(i - 1) == '.') {
              suffix = suffix.substring(0, i) + "..";
              break;
            }
            else {
              suffix = suffix.substring(0, i) + "...";
              break;
            }
          }
        }
      }
      string = prefix + suffix;
    }
    string = string.replaceAll(" <>", "<br>");
    return ActionGroupDescriptor.HTML_PREFIX + string + ActionGroupDescriptor.HTML_SUFFIX;
  }

  private abstract class MyActionButton extends JComponent implements ActionButtonComponent {
    private int myGroupIdx;
    private int myRowIdx;
    private int myColumnIdx;
    private String myDisplayName;
    private Icon myIcon;

    private MyActionButton(Icon icon, String displayName) {
      myDisplayName = displayName;
      myIcon = new LabeledIcon(icon != null ? icon : DEFAULT_ICON, getDisplayName(), null);
    }

    private void setupWithinPanel(JPanel panel, int groupIdx, int rowIdx, int columnIdx) {
      myGroupIdx = groupIdx;
      myRowIdx = rowIdx;
      myColumnIdx = columnIdx;
      setToolTipText(null);
      setupListeners(panel);
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

    protected Color getNormalButtonColor() {
      return ACTION_BUTTON_COLOR;
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
        g.setColor(getNormalButtonColor());
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
      UIUtil.drawLine(g, rectangle.x, rectangle.y, rectangle.x, (rectangle.y + rectangle.height) - 1);
      UIUtil.drawLine(g, rectangle.x, rectangle.y, (rectangle.x + rectangle.width) - 1, rectangle.y);
      UIUtil.drawLine(g, (rectangle.x + rectangle.width) - 1, rectangle.y, (rectangle.x + rectangle.width) - 1,
                      (rectangle.y + rectangle.height) - 1);
      UIUtil.drawLine(g, rectangle.x, (rectangle.y + rectangle.height) - 1, (rectangle.x + rectangle.width) - 1,
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
    private ButtonWithExtension(Icon icon, String displayName) {
      super(icon, displayName);
    }

    protected void onPress(InputEvent e) {
      onPress(e, this);
    }

    protected abstract void onPress(InputEvent e, MyActionButton button);
  }

  private abstract class PluginsActionButton extends MyActionButton {
    protected PluginsActionButton(Icon icon, String displayName) {
      super(icon, displayName);
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

    @Override protected Color getNormalButtonColor() {
      return LEARN_MORE_BUTTON_COLOR;
    }

    protected void paintBorder(Graphics g) {
      Rectangle rectangle = new Rectangle(getSize());
      Color color = WHITE_BORDER_COLOR;
      g.setColor(color);
      UIUtil.drawLine(g, rectangle.x, rectangle.y, rectangle.x, (rectangle.y + rectangle.height) - 1);
      UIUtil.drawLine(g, rectangle.x, rectangle.y, (rectangle.x + rectangle.width) - 1, rectangle.y);
      color = GRAY_BORDER_COLOR;
      g.setColor(color);
      UIUtil.drawLine(g, (rectangle.x + rectangle.width) - 1, rectangle.y + 1, (rectangle.x + rectangle.width) - 1,
                      (rectangle.y + rectangle.height) - 1);
      UIUtil.drawLine(g, rectangle.x + 1, (rectangle.y + rectangle.height) - 1, (rectangle.x + rectangle.width) - 1,
                      (rectangle.y + rectangle.height) - 1);
    }
  }
}
