package com.intellij.ide.browsers;

import com.intellij.ide.BrowserUtil;
import com.intellij.lang.Language;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.lang.xhtml.XHTMLLanguage;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.HashMap;
import com.intellij.xml.XmlBundle;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * @author spleaner
 */
@State(name = "WebBrowsersConfiguration", storages = {@Storage(id = "other", file = "$APP_CONFIG$/browsers.xml")})
public class BrowsersConfiguration implements ApplicationComponent, Configurable, PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.browsers.BrowsersConfiguration");
  private static final Icon ICON = IconLoader.getIcon("/general/browsersettings.png");

  private static final Icon SAFARI_ICON = IconLoader.getIcon("/xml/browsers/safari16.png");
  private static final Icon FIREFOX_ICON = IconLoader.getIcon("/xml/browsers/firefox16.png");
  private static final Icon EXPLORER_ICON = IconLoader.getIcon("/xml/browsers/explorer16.png");
  private static final Icon OPERA_ICON = IconLoader.getIcon("/xml/browsers/opera16.png");
  private WebBrowsersPanel mySettingsPanel;

  private Map<BrowserFamily, Pair<String, Boolean>> myBrowserToPathMap = new HashMap<BrowserFamily, Pair<String, Boolean>>();

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static enum BrowserFamily {
    EXPLORER(XmlBundle.message("browsers.explorer"), "iexplore", null, null),
    SAFARI(XmlBundle.message("browsers.safari"), "safari", "safari", "Safari"),
    OPERA(XmlBundle.message("browsers.opera"), "opera", "opera", "Opera"),
    FIREFOX(XmlBundle.message("browsers.firefox"), "firefox", "firefox", "Firefox");

    private String myName;
    private String myWindowsPath;
    private String myLinuxPath;
    private String myMacPath;

    BrowserFamily(final String name, final String windowsPath, final String linuxPath, final String macPath) {
      myName = name;
      myWindowsPath = windowsPath;
      myLinuxPath = linuxPath;
      myMacPath = macPath;
    }

    @Nullable
    public String getExecutionPath() {
      if (SystemInfo.isWindows) {
        return myWindowsPath;
      }
      else if (SystemInfo.isLinux) {
        return null;
      }
      else if (SystemInfo.isMac) {
        return myMacPath;
      }

      return null;
    }

    public String getName() {
      return myName;
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public Element getState() {
    @NonNls Element element = new Element("WebBrowsersConfiguration");
    for (BrowserFamily browserFamily : myBrowserToPathMap.keySet()) {
      final Element browser = new Element("browser");
      browser.setAttribute("family", browserFamily.toString());
      final Pair<String, Boolean> value = myBrowserToPathMap.get(browserFamily);
      browser.setAttribute("path", value.first);
      browser.setAttribute("active", value.second.toString());

      element.addContent(browser);
    }

    return element;
  }

  @SuppressWarnings({"unchecked"})
  public void loadState(@NonNls Element element) {
    for (@NonNls Element child : (Iterable<? extends Element>)element.getChildren("browser")) {
      String family = child.getAttributeValue("family");
      final String path = child.getAttributeValue("path");
      final String active = child.getAttributeValue("active");
      final BrowserFamily browserFamily = BrowserFamily.valueOf(family);
      myBrowserToPathMap.put(browserFamily, new Pair<String, Boolean>(path, Boolean.parseBoolean(active)));
    }
  }

  private void suggestBrowserPath(@NotNull final BrowserFamily browserFamily) {
    if (!myBrowserToPathMap.containsKey(browserFamily)) {
      final String path = browserFamily.getExecutionPath();
      myBrowserToPathMap.put(browserFamily, new Pair<String, Boolean>(path == null ? "" : path, path != null));
    }
  }

  public static BrowsersConfiguration getInstance() {
    return ApplicationManager.getApplication().getComponent(BrowsersConfiguration.class);
  }

  @NotNull
  public String getComponentName() {
    return "BrowsersConfiguration";
  }

  public void initComponent() {
    if (myBrowserToPathMap.size() == 0) {
      for (BrowserFamily browserFamily : BrowserFamily.values()) {
        suggestBrowserPath(browserFamily);
      }
    }

    installBrowserActions();
  }

  public void disposeComponent() {
  }

  @Nls
  public String getDisplayName() {
    return XmlBundle.message("browsers.configuration.name");
  }

  public Icon getIcon() {
    return ICON;
  }

  public String getHelpTopic() {
    return null;
  }

  public JComponent createComponent() {
    if (mySettingsPanel == null) {
      mySettingsPanel = new WebBrowsersPanel(myBrowserToPathMap);
    }

    return mySettingsPanel;
  }

  public boolean isModified() {
    LOG.assertTrue(mySettingsPanel != null);
    return mySettingsPanel.isModified();
  }

  public void apply() throws ConfigurationException {
    LOG.assertTrue(mySettingsPanel != null);
    mySettingsPanel.apply();
  }

  public void reset() {
    LOG.assertTrue(mySettingsPanel != null);
    mySettingsPanel.reset();
  }

  public void disposeUIResources() {
    mySettingsPanel.dispose();
    mySettingsPanel = null;
  }

  public static void launchBrowser(final BrowserFamily family, @NotNull final VirtualFile file) {
    try {
      getInstance()._launchBrowser(family, file);
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private void _launchBrowser(final BrowserFamily family, @NotNull final VirtualFile file) throws IOException {
    final Pair<String, Boolean> pair = myBrowserToPathMap.get(family);
    if (pair != null) {
      final String path = pair.first;
      if (path != null && path.length() > 0) {
        String[] command = null;
        final String url = BrowserUtil.getURL(file.getUrl()).toString();
        if (SystemInfo.isMac) {
          command = new String[]{"open", "-a", path, url};
        }
        if (SystemInfo.isWindows9x) {
          if (path.indexOf(File.separatorChar) != -1) {
            command = new String[]{path, url};
          } else {
            command = new String[]{"command.com", "/c", "start", path, url};
          }
        }
        else if (SystemInfo.isWindows) {
          if (path.indexOf(File.separatorChar) != -1) {
            command = new String[]{path, url};
          } else {
            command = new String[]{"cmd.exe", "/c", "start", path, url};
          }
        }
        else if (SystemInfo.isLinux) {
          command = new String[]{path, url};
        }

        if (command != null) {
          Runtime.getRuntime().exec(command);
        }
        else {
          LOG.assertTrue(false);
        }
      }
      else {
        Messages.showErrorDialog(XmlBundle.message("browser.path.not.specified", family.getName()), XmlBundle.message("browser.path.not.specified.title"));
      }
    }
    else {
      LOG.assertTrue(false);
    }
  }

  public void installBrowserActions() {
    installBrowserAction(BrowserFamily.FIREFOX);
    installBrowserAction(BrowserFamily.EXPLORER);
    installBrowserAction(BrowserFamily.SAFARI);
    installBrowserAction(BrowserFamily.OPERA);
  }

  private void installBrowserAction(@NotNull final BrowserFamily family) {
    final ActionManager actionManager = ActionManager.getInstance();

    @NonNls final String actionId = "BROWSER_" + family.toString();
    AnAction action = actionManager.getAction(actionId);
    if (action == null) {
      action = new AnAction(family.getName(), XmlBundle.message("browser.description", family.getName()), getBrowserIcon(family)) {
        public void actionPerformed(final AnActionEvent e) {
          final VirtualFile file = PlatformDataKeys.VIRTUAL_FILE.getData(e.getDataContext());
          if (file != null) {
            launchBrowser(family, file);
          }
        }

        @Override
        public void update(final AnActionEvent e) {
          final Editor editor = PlatformDataKeys.EDITOR.getData(e.getDataContext());
          boolean visible = false;
          if (editor != null) {
            final Document document = editor.getDocument();
            final Project project = editor.getProject();
            if (project != null) {
              final PsiDocumentManager manager = PsiDocumentManager.getInstance(project);
              final PsiFile psiFile = manager.getPsiFile(document);
              if (psiFile != null) {
                final Language language = psiFile.getLanguage();
                if (language instanceof HTMLLanguage || language instanceof XHTMLLanguage) {
                  visible = true;
                }
              }
            }
          }

          visible &= myBrowserToPathMap.get(family).second.booleanValue();

          final Presentation presentation = e.getPresentation();
          presentation.setVisible(visible);
        }
      };

      actionManager.registerAction(actionId, action);

      AnAction actionGroup = actionManager.getAction("EditorContextBarMenu");
      if (actionGroup == null) {
        actionGroup = new DefaultActionGroup();
        actionManager.registerAction("EditorContextBarMenu", actionGroup);
      }

      if (actionGroup instanceof DefaultActionGroup) {
        ((DefaultActionGroup)actionGroup).add(action);
      }
    }
  }

  private static Icon getBrowserIcon(final BrowserFamily family) {
    switch (family) {
      case EXPLORER:
        return EXPLORER_ICON;
      case FIREFOX:
        return FIREFOX_ICON;
      case OPERA:
        return OPERA_ICON;
      case SAFARI:
        return SAFARI_ICON;
    }
    return null;
  }
}
