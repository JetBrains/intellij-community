package com.jetbrains.edu.learning.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.ide.ui.laf.darcula.DarculaLookAndFeelInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.StreamUtil;
import com.jetbrains.edu.learning.StudyPluginConfigurator;
import com.jetbrains.edu.learning.navigation.StudyNavigator;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebHistory;
import javafx.scene.web.WebView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.*;
import org.w3c.dom.events.Event;
import org.w3c.dom.events.EventListener;
import org.w3c.dom.events.EventTarget;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StudyBrowserWindow extends JFrame {
  private static final Logger LOG = Logger.getInstance(StudyToolWindow.class);
  private static final String EVENT_TYPE_CLICK = "click";
  private static final Pattern IN_COURSE_LINK = Pattern.compile("#(\\w+)#(\\w+)#");
  private JFXPanel myPanel;
  private WebView myWebComponent;
  private StackPane myPane;

  private WebEngine myEngine;
  private ProgressBar myProgressBar;
  private final Project myProject;
  private boolean myLinkInNewBrowser = true;
  private boolean myShowProgress = false;

  public StudyBrowserWindow(@NotNull final Project project, final boolean linkInNewWindow, final boolean showProgress) {
    myProject = project;
    myLinkInNewBrowser = linkInNewWindow;
    myShowProgress = showProgress;
    setSize(new Dimension(900, 800));
    setLayout(new BorderLayout());
    setPanel(new JFXPanel());
    setTitle("Study Browser");
    LafManager.getInstance().addLafManagerListener(new StudyLafManagerListener());
    initComponents();
  }

  private void updateLaf(boolean isDarcula) {
    if (isDarcula) {
      updateLafDarcula();
    }
    else {
      updateIntellijAndGTKLaf();
    }
  }

  private void updateIntellijAndGTKLaf() {
    Platform.runLater(() -> {
      final URL scrollBarStyleUrl = getClass().getResource("/style/javaFXBrowserScrollBar.css");
      myPane.getStylesheets().add(scrollBarStyleUrl.toExternalForm());
      myEngine.setUserStyleSheetLocation(null);
      myEngine.reload();
    });
  }

  private void updateLafDarcula() {
    Platform.runLater(() -> {
      final URL engineStyleUrl = getClass().getResource("/style/javaFXBrowserDarcula.css");
      final URL scrollBarStyleUrl = getClass().getResource("/style/javaFXBrowserDarculaScrollBar.css");
      myEngine.setUserStyleSheetLocation(engineStyleUrl.toExternalForm());
      myPane.getStylesheets().add(scrollBarStyleUrl.toExternalForm());
      myPane.setStyle("-fx-background-color: #3c3f41");
      myPanel.getScene().getStylesheets().add(engineStyleUrl.toExternalForm());
      myEngine.reload();
    });
  }

  private void initComponents() {
    Platform.runLater(() -> {
      myPane = new StackPane();
      myWebComponent = new WebView();
      myWebComponent.setOnDragDetected(event -> {});
      myEngine = myWebComponent.getEngine();


      if (myShowProgress) {
        myProgressBar = makeProgressBarWithListener();
        myWebComponent.setVisible(false);
        myPane.getChildren().addAll(myWebComponent, myProgressBar);
      }
      else {
        myPane.getChildren().add(myWebComponent);
      }
      if (myLinkInNewBrowser) {
        initHyperlinkListener();
      }
      Scene scene = new Scene(myPane);
      myPanel.setScene(scene);
      myPanel.setVisible(true);
      updateLaf(LafManager.getInstance().getCurrentLookAndFeel() instanceof DarculaLookAndFeelInfo);
    });

    add(myPanel, BorderLayout.CENTER);
    setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
  }


  public void load(@NotNull final String url) {
    Platform.runLater(() -> {
      updateLookWithProgressBarIfNeeded();
      myEngine.load(url);
    });
  }

  public void loadContent(@NotNull final String content, @Nullable StudyPluginConfigurator configurator) {
    if (configurator == null) {
      Platform.runLater(() -> myEngine.loadContent(content));
    }
    else {
      String withCodeHighlighting = createHtmlWithCodeHighlighting(content, configurator);
      Platform.runLater(() -> {
        updateLookWithProgressBarIfNeeded();
        myEngine.loadContent(withCodeHighlighting);
      });
    }
  }

  @Nullable
  private String createHtmlWithCodeHighlighting(@NotNull final String content, @NotNull StudyPluginConfigurator configurator) {
    String template = null;
    InputStream stream = getClass().getResourceAsStream("/code-mirror/template.html");
    try {
      template = StreamUtil.readText(stream, "utf-8");
    }
    catch (IOException e) {
      LOG.warn(e.getMessage());
    }
    finally {
      try {
        stream.close();
      }
      catch (IOException e) {
        LOG.warn(e.getMessage());
      }
    }

    if (template == null) {
      LOG.warn("Code mirror template is null");
      return null;
    }

    final EditorColorsScheme editorColorsScheme = EditorColorsManager.getInstance().getGlobalScheme();
    int fontSize = editorColorsScheme.getEditorFontSize();
    
    template = template.replace("${font_size}", String.valueOf(fontSize- 2));
    template = template.replace("${codemirror}", getClass().getResource("/code-mirror/codemirror.js").toExternalForm());
    template = template.replace("${language_script}", configurator.getLanguageScriptUrl());
    template = template.replace("${default_mode}", configurator.getDefaultHighlightingMode());
    template = template.replace("${runmode}", getClass().getResource("/code-mirror/runmode.js").toExternalForm());
    template = template.replace("${colorize}", getClass().getResource("/code-mirror/colorize.js").toExternalForm());
    template = template.replace("${javascript}", getClass().getResource("/code-mirror/javascript.js").toExternalForm());
    if (LafManager.getInstance().getCurrentLookAndFeel() instanceof DarculaLookAndFeelInfo) {
      template = template.replace("${css_oldcodemirror}", getClass().getResource("/code-mirror/codemirror-old-darcula.css").toExternalForm());
      template = template.replace("${css_codemirror}", getClass().getResource("/code-mirror/codemirror-darcula.css").toExternalForm());
    }
    else {
      template = template.replace("${css_oldcodemirror}", getClass().getResource("/code-mirror/codemirror-old.css").toExternalForm());
      template = template.replace("${css_codemirror}", getClass().getResource("/code-mirror/codemirror.css").toExternalForm());
    }
    template = template.replace("${code}", content);

    return template;
  }

  private void updateLookWithProgressBarIfNeeded() {
    if (myShowProgress) {
      myProgressBar.setVisible(true);
      myWebComponent.setVisible(false);
    }
  }

  private void initHyperlinkListener() {
    myEngine.getLoadWorker().stateProperty().addListener((ov, oldState, newState) -> {
      if (newState == Worker.State.SUCCEEDED) {
        final EventListener listener = makeHyperLinkListener();

        addListenerToAllHyperlinkItems(listener);
      }
    });
  }

  private void addListenerToAllHyperlinkItems(EventListener listener) {
    final Document doc = myEngine.getDocument();
    if (doc != null) {
      final NodeList nodeList = doc.getElementsByTagName("a");
      for (int i = 0; i < nodeList.getLength(); i++) {
        ((EventTarget)nodeList.item(i)).addEventListener(EVENT_TYPE_CLICK, listener, false);
      }
    }
  }

  @NotNull
  private EventListener makeHyperLinkListener() {
    return new EventListener() {
      @Override
      public void handleEvent(Event ev) {
        String domEventType = ev.getType();
        if (domEventType.equals(EVENT_TYPE_CLICK)) {
          Element target = (Element)ev.getTarget();
          String hrefAttribute = target.getAttribute("href");
          if (hrefAttribute != null) {
            final Matcher matcher = IN_COURSE_LINK.matcher(hrefAttribute);
            if (matcher.matches()) {
              final String lessonName = matcher.group(1);
              final String taskName = matcher.group(2);
              StudyNavigator.navigateToTask(myProject, lessonName, taskName);
            }
            else {
              myEngine.setJavaScriptEnabled(true);
              myEngine.getLoadWorker().cancel();
              ev.preventDefault();
              final String href = getLink(target);
              if (href == null) return;
              BrowserUtil.browse(href);
            }
          }
        }
      }

      @Nullable
      private String getLink(@NotNull Element element) {
        final String href = element.getAttribute("href");
        return href == null ? getLinkFromNodeWithCodeTag(element) : href;
      }

      @Nullable
      private String getLinkFromNodeWithCodeTag(@NotNull Element element) {
        Node parentNode = element.getParentNode();
        NamedNodeMap attributes = parentNode.getAttributes();
        while (attributes.getLength() > 0 && attributes.getNamedItem("class") != null) {
          parentNode = parentNode.getParentNode();
          attributes = parentNode.getAttributes();
        }
        return attributes.getNamedItem("href").getNodeValue();
      }
    };
  }

  public void addBackAndOpenButtons() {
    ApplicationManager.getApplication().invokeLater(() -> {
      final JPanel panel = new JPanel();
      panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));

      final JButton backButton = makeGoButton("Click to go back", AllIcons.Actions.Back, -1);
      final JButton forwardButton = makeGoButton("Click to go forward", AllIcons.Actions.Forward, 1);
      final JButton openInBrowser = new JButton(AllIcons.Actions.Browser_externalJavaDoc);
      openInBrowser.addActionListener(e -> BrowserUtil.browse(myEngine.getLocation()));
      openInBrowser.setToolTipText("Click to open link in browser");
      addButtonsAvailabilityListeners(backButton, forwardButton);

      panel.setMaximumSize(new Dimension(40, getPanel().getHeight()));
      panel.add(backButton);
      panel.add(forwardButton);
      panel.add(openInBrowser);

      add(panel, BorderLayout.PAGE_START);
    });
  }

  private void addButtonsAvailabilityListeners(JButton backButton, JButton forwardButton) {
    Platform.runLater(() -> myEngine.getLoadWorker().stateProperty().addListener((ov, oldState, newState) -> {
      if (newState == Worker.State.SUCCEEDED) {
        final WebHistory history = myEngine.getHistory();
        boolean isGoBackAvailable = history.getCurrentIndex() > 0;
        boolean isGoForwardAvailable = history.getCurrentIndex() < history.getEntries().size() - 1;
        ApplicationManager.getApplication().invokeLater(() -> {
          backButton.setEnabled(isGoBackAvailable);
          forwardButton.setEnabled(isGoForwardAvailable);
        });
      }
    }));
  }

  private JButton makeGoButton(@NotNull final String toolTipText, @NotNull final Icon icon, final int direction) {
    final JButton button = new JButton(icon);
    button.setEnabled(false);
    button.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 1) {
          Platform.runLater(() -> myEngine.getHistory().go(direction));
        }
      }
    });
    button.setToolTipText(toolTipText);
    return button;
  }


  private ProgressBar makeProgressBarWithListener() {
    final ProgressBar progress = new ProgressBar();
    progress.progressProperty().bind(myWebComponent.getEngine().getLoadWorker().progressProperty());

    myWebComponent.getEngine().getLoadWorker().stateProperty().addListener(
      (ov, oldState, newState) -> {
        if (myWebComponent.getEngine().getLocation().contains("http") && newState == Worker.State.SUCCEEDED) {
          myProgressBar.setVisible(false);
          myWebComponent.setVisible(true);
        }
      });

    return progress;
  }

  public JFXPanel getPanel() {
    return myPanel;
  }

  private void setPanel(JFXPanel panel) {
    myPanel = panel;
  }

  private class StudyLafManagerListener implements LafManagerListener {
    @Override
    public void lookAndFeelChanged(LafManager manager) {
      updateLaf(manager.getCurrentLookAndFeel() instanceof DarculaLookAndFeelInfo);
    }
  }
}
