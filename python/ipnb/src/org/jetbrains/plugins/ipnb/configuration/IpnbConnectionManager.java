package org.jetbrains.plugins.ipnb.configuration;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.impl.ExecutionManagerImpl;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.components.JBList;
import com.intellij.util.TimeoutUtil;
import com.jetbrains.python.run.PyRunConfigurationFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ipnb.IpnbUtils;
import org.jetbrains.plugins.ipnb.editor.IpnbFileEditor;
import org.jetbrains.plugins.ipnb.editor.panels.code.IpnbCodePanel;
import org.jetbrains.plugins.ipnb.format.IpnbParser;
import org.jetbrains.plugins.ipnb.protocol.IpnbConnection;
import org.jetbrains.plugins.ipnb.protocol.IpnbConnectionListenerBase;
import org.jetbrains.plugins.ipnb.protocol.IpnbConnectionV3;
import org.jetbrains.plugins.ipnb.run.IpnbConfigurationEditor;
import org.jetbrains.plugins.ipnb.run.IpnbRunConfiguration;
import org.jetbrains.plugins.ipnb.run.IpnbRunConfigurationType;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class IpnbConnectionManager implements ProjectComponent, Disposable {
  private static final Logger LOG = Logger.getInstance(IpnbConnectionManager.class);
  private final Project myProject;
  private final Map<String, IpnbConnection> myKernels = new HashMap<>();
  private final Map<String, IpnbCodePanel> myUpdateMap = new HashMap<>();
  @Nullable private String myToken;   // used only if Notebook started from outside
  @Nullable private String myUrl;     // used only if Notebook started from outside

  private static final String CONNECTION_REFUSED = "Connection refused";
  private static final int MAX_ATTEMPTS = 10;

  public IpnbConnectionManager(final Project project) {
    myProject = project;
  }

  public static IpnbConnectionManager getInstance(Project project) {
    return project.getComponent(IpnbConnectionManager.class);
  }

  public void executeCell(@NotNull final IpnbCodePanel codePanel) {
    final IpnbFileEditor fileEditor = codePanel.getFileEditor();
    final VirtualFile virtualFile = fileEditor.getVirtualFile();
    final String path = virtualFile.getPath();
    if (!hasConnection(path)) {
      startConnection(codePanel, path);
    }
    else {
      IpnbConnection connection = myKernels.get(path);
      if (!connection.isAlive()) {
        myKernels.remove(path);
        startConnection(codePanel, path);
      }
      else {
        final String messageId = connection.execute(codePanel.getCell().getSourceAsString());
        myUpdateMap.put(messageId, codePanel);
      }
    }
  }

  public boolean hasConnection(String path) {
    return myKernels.containsKey(path);
  }

  private void startConnection(@NotNull final IpnbCodePanel codePanel, @NotNull final String filePath) {
    final List<RunContentDescriptor> descriptors = ExecutionManagerImpl.getInstance(myProject).getRunningDescriptors(
      settings -> settings.getConfiguration() instanceof IpnbRunConfiguration
    );
    if (descriptors.isEmpty()) {
      if (!connectToExternal(codePanel, filePath)) {
        showMessage(codePanel.getFileEditor(), "Cannot connect to Jupyter Notebook. <a href=\"\">Run Jupyter Notebook</a>",
                    new IpnbRunAdapter(), MessageType.WARNING);
      }
    }
    else {
      if (descriptors.size() == 1) {
        final RunContentDescriptor descriptor = descriptors.get(0);
        final Pair<String, String> urlToken = getUrlTokenByDescriptor(descriptor);
        startConnection(codePanel, filePath, urlToken.getFirst(), urlToken.getSecond());
      }
      else {
        selectRunningInstance(codePanel, filePath, descriptors);
      }
    }
  }

  private void selectRunningInstance(@NotNull IpnbCodePanel codePanel, @NotNull String filePath, List<RunContentDescriptor> descriptors) {
    final JList<RunContentDescriptor> list = new JBList<>(descriptors);
    list.setCellRenderer(new ColoredListCellRenderer<RunContentDescriptor>() {
      @Override
      protected void customizeCellRenderer(@NotNull JList<? extends RunContentDescriptor> list, RunContentDescriptor value, int index,
                                           boolean selected, boolean hasFocus) {
        append(value.getDisplayName());
      }
    });
    final PopupChooserBuilder builder = new PopupChooserBuilder(list);
    builder.setTitle("Choose Jupyter Notebook Server");
    builder.setItemChoosenCallback(() -> {
      final Pair<String, String> urlToken = getUrlTokenByDescriptor(list.getSelectedValue());
      startConnection(codePanel, filePath, urlToken.getFirst(), urlToken.getSecond());
    });
    final JBPopup popup = builder.createPopup();
    final PointerInfo pointerInfo = MouseInfo.getPointerInfo();
    if (pointerInfo == null) return;
    final Point point = pointerInfo.getLocation();
    popup.showInScreenCoordinates(WindowManagerEx.getInstanceEx().getMostRecentFocusedWindow(), point);
  }

  private boolean connectToExternal(@NotNull IpnbCodePanel codePanel, @NotNull String filePath) {
    if (myToken == null || myUrl == null) {
      ApplicationManager.getApplication().invokeAndWait(() -> {
        final Pair<String, String> pair = askForUrlAndToken();
        if (pair != null) {
          myUrl = StringUtil.trimTrailing(pair.getFirst(), '/');
          myToken = pair.getSecond();
        }
      });
    }
    if (myUrl != null) {
      startConnection(codePanel, filePath, myUrl, myToken);
      return true;
    }
    return false;
  }

  @NotNull
  private Pair<String, String> getUrlTokenByDescriptor(@NotNull final RunContentDescriptor descriptor) {
    final Set<RunnerAndConfigurationSettings> configurations = ExecutionManagerImpl.getInstance(myProject).getConfigurations(descriptor);
    for (RunnerAndConfigurationSettings configuration : configurations) {
      final RunConfiguration runConfiguration = configuration.getConfiguration();
      if (runConfiguration instanceof IpnbRunConfiguration) {
        final String token = ((IpnbRunConfiguration)runConfiguration).getToken();
        if (token != null) {
          return Pair.create(((IpnbRunConfiguration)runConfiguration).getUrl(), token);
        }
      }
    }
    return Pair.empty();
  }

  private static Pair<String, String> askForUrlAndToken() {
    final String urlToken = Messages.showInputDialog("Please, enter your Jupyter Notebook URL and authentication token",
                                                     "Jupyter Notebook", null, "http://localhost:8888/?token=", null);
    if (!StringUtil.isEmptyOrSpaces(urlToken)) {
      final String trimmed = urlToken.trim();
      final List<String> strings = StringUtil.split(trimmed, "?token=", true);
      String token = strings.size() > 1 ? strings.get(1) : null;
      return Pair.create(strings.get(0), token);
    }
    return null;
  }

  public void startConnection(@Nullable final IpnbCodePanel codePanel, @NotNull final String path, @NotNull final String urlString,
                              @Nullable final String token) {
    if (codePanel == null) return;
    final VirtualFile file = codePanel.getFileEditor().getVirtualFile();
    String pathToFile = getRelativePathToFile(file);
    if (pathToFile == null) return;
    final boolean format = IpnbParser.isIpythonNewFormat(file);
    IpnbUtils.runCancellableProcessUnderProgress(myProject, () -> setupConnection(codePanel, path, urlString, token, format),
                                                 "Connecting to Jupyter Notebook Server");
  }

  @NotNull
  private IpnbConnectionListenerBase createConnectionListener(@Nullable IpnbCodePanel codePanel, Ref<Boolean> connectionOpened) {
    return new IpnbConnectionListenerBase() {
      @Override
      public void onOpen(@NotNull IpnbConnection connection) {
        connectionOpened.set(true);
        if (codePanel == null) return;
        final String messageId = connection.execute(codePanel.getCell().getSourceAsString());
        myUpdateMap.put(messageId, codePanel);
      }

      @Override
      public void onOutput(@NotNull IpnbConnection connection,
                           @NotNull String parentMessageId) {
        if (!myUpdateMap.containsKey(parentMessageId)) return;
        final IpnbCodePanel cell = myUpdateMap.get(parentMessageId);
        cell.getCell().setPromptNumber(connection.getExecCount());
        cell.updatePanel(null, connection.getOutput());
      }

      @Override
      public void onPayload(@Nullable String payload, @NotNull String parentMessageId) {
        if (!myUpdateMap.containsKey(parentMessageId)) return;
        final IpnbCodePanel cell = myUpdateMap.remove(parentMessageId);
        if (payload != null) {
          cell.updatePanel(payload, null);
        }
      }

      @Override
      public void onFinished(@NotNull IpnbConnection connection, @NotNull String parentMessageId) {
        if (!myUpdateMap.containsKey(parentMessageId)) return;
        final IpnbCodePanel cell = myUpdateMap.remove(parentMessageId);
        cell.getCell().setPromptNumber(connection.getExecCount());
        cell.finishExecution();
      }
    };
  }

  private boolean setupConnection(@NotNull IpnbCodePanel codePanel, @NotNull String path, @NotNull String urlString,
                                  String token, boolean isNewFormat) {
    try {
      Ref<Boolean> connectionOpened = new Ref<>(false);
      final IpnbConnectionListenerBase listener = createConnectionListener(codePanel, connectionOpened);
      final VirtualFile file = codePanel.getFileEditor().getVirtualFile();
      final String pathToFile = getRelativePathToFile(file);
      if (pathToFile == null) return false;
      final IpnbConnection connection = getConnection(urlString, listener, pathToFile, token, isNewFormat);
      int countAttempt = 0;
      while (!connectionOpened.get() && countAttempt < MAX_ATTEMPTS) {
        countAttempt += 1;
        TimeoutUtil.sleep(1000);
      }
      myKernels.put(path, connection);
    }
    catch (URISyntaxException e) {
      showMessage(codePanel.getFileEditor(), "Cannot connect to Jupyter Notebook. <a href=\"\">Run Jupyter Notebook</a>",
                  new IpnbRunAdapter(), MessageType.WARNING);
      LOG.warn("Jupyter Notebook connection refused: " + e.getMessage());
      return false;
    }
    catch (UnsupportedOperationException e) {
      showMessage(codePanel.getFileEditor(), e.getMessage(), null, MessageType.WARNING);
    }
    catch (UnknownHostException e) {
      showMessage(codePanel.getFileEditor(), "Cannot connect to Jupyter Notebook. <a href=\"\">Run Jupyter Notebook</a>",
                  new IpnbRunAdapter(), MessageType.WARNING);
    }
    catch (IOException e) {
      if (IpnbConnection.AUTHENTICATION_NEEDED.equals(e.getMessage())) {
        ApplicationManager.getApplication().invokeAndWait(() -> {
          final Pair<String, String> pair = askForUrlAndToken();
          if (pair != null) {
            myToken = pair.getFirst();
            myUrl = pair.getSecond();
          }
        });
        if (myToken != null) {
          return setupConnection(codePanel, path, urlString, token, isNewFormat);
        }
      }
      final String message = e.getMessage();
      if (message.startsWith(IpnbConnection.UNABLE_LOGIN_MESSAGE)) {
        showMessage(codePanel.getFileEditor(), "Cannot connect to Jupyter Notebook: login failed", new IpnbSettingsAdapter(),
                    MessageType.WARNING);
      }
      else if (message.startsWith(CONNECTION_REFUSED) || message.startsWith(IpnbConnection.CANNOT_START_JUPYTER)) {
        showMessage(codePanel.getFileEditor(), "Cannot connect to Jupyter Notebook. <a href=\"\">Run Jupyter Notebook</a>",
                    new IpnbRunAdapter(), MessageType.WARNING);
      }
      LOG.warn("Jupyter Notebook connection refused: " + message);
      return false;
    }
    return true;
  }

  @NotNull
  private IpnbConnection getConnection(@NotNull String urlString, @NotNull IpnbConnectionListenerBase listener, @NotNull String pathToFile,
                                       String token, boolean isNewFormat) throws IOException, URISyntaxException {
    if (!isNewFormat) {
      return new IpnbConnection(urlString, listener, token, myProject, pathToFile);
    }
    return new IpnbConnectionV3(urlString, listener, token, myProject, pathToFile);
  }

  @Nullable
  private String getRelativePathToFile(VirtualFile file) {
    final String workingDir = myProject.getBasePath();
    if (workingDir != null) {
      final Path basePath = Paths.get(workingDir);
      final Path filePath = Paths.get(file.getPath());
      return basePath.relativize(filePath).toString();
    }
    return null;
  }

  public void interruptKernel(@NotNull String filePath) {
    if (!hasConnection(filePath)) return;
    final IpnbConnection connection = myKernels.get(filePath);
    try {
      connection.interrupt();
    }
    catch (IOException e) {
      LOG.warn("Failed to interrupt kernel " + filePath);
      LOG.warn(e.getMessage());
    }
  }

  public void reloadKernel(@NotNull String filePath) {
    if (!hasConnection(filePath)) return;
    final IpnbConnection connection = myKernels.get(filePath);
    try {
      connection.reload();
    }
    catch (IOException e) {
      LOG.warn("Failed to reload kernel " + filePath);
      LOG.warn(e.getMessage());
    }
  }

  private static void showMessage(@NotNull final IpnbFileEditor fileEditor,
                                  @NotNull final String message,
                                  @Nullable final HyperlinkAdapter listener, MessageType messageType) {
    ApplicationManager.getApplication().invokeLater(() -> {
      BalloonBuilder balloonBuilder = JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(
        message, null, messageType.getPopupBackground(), listener);
      final Balloon balloon = balloonBuilder.setHideOnLinkClick(true).createBalloon();
      ApplicationManager.getApplication().invokeLater(() -> balloon.show(fileEditor.getRunButtonPlace(), Balloon.Position.above));
    });
  }

  @Override
  public void projectClosed() {
    shutdownKernels();
  }

  public void shutdownKernels() {
    for (IpnbConnection connection : myKernels.values()) {
      if (!connection.isAlive()) continue;
      connection.shutdown();
      try {
        connection.close();
      }
      catch (IOException | InterruptedException e) {
        LOG.error(e);
      }
    }
    myKernels.clear();
  }

  @Override
  @NotNull
  public String getComponentName() {
    return "IpnbConnectionManager";
  }

  @Override
  public void dispose() {
    shutdownKernels();
  }

  class IpnbSettingsAdapter extends HyperlinkAdapter {
    @Override
    protected void hyperlinkActivated(HyperlinkEvent e) {
      ShowSettingsUtil.getInstance().showSettingsDialog(myProject, "Jupyter Notebook");
    }
  }

  class IpnbRunAdapter extends HyperlinkAdapter {
    @Override
    protected void hyperlinkActivated(HyperlinkEvent e) {
      final List<RunnerAndConfigurationSettings> configurationsList =
        RunManager.getInstance(myProject).getConfigurationSettingsList(IpnbRunConfigurationType.getInstance());

      if (configurationsList.isEmpty()) {
        final RunnerAndConfigurationSettings configurationSettings = PyRunConfigurationFactory.getInstance()
          .createRunConfiguration(ModuleManager.getInstance(myProject).getModules()[0],
                                  IpnbRunConfigurationType.getInstance().getConfigurationFactories()[0]);
        final IpnbRunConfiguration configuration = (IpnbRunConfiguration)configurationSettings.getConfiguration();
        configuration.setHost(IpnbConfigurationEditor.DEFAULT_HOST);
        configuration.setPort(IpnbConfigurationEditor.DEFAULT_PORT);
        configurationSettings.setSingleton(true);

        ExecutionUtil.runConfiguration(configurationSettings, DefaultRunExecutor.getRunExecutorInstance());
      }
      else {
        if (configurationsList.size() == 1) {
          ExecutionUtil.runConfiguration(configurationsList.get(0), DefaultRunExecutor.getRunExecutorInstance());
        }
        else {
          final JList<RunnerAndConfigurationSettings> list = new JBList<>(configurationsList);
          list.setCellRenderer(new ColoredListCellRenderer<RunnerAndConfigurationSettings>() {
            @Override
            protected void customizeCellRenderer(@NotNull JList<? extends RunnerAndConfigurationSettings> list,
                                                 RunnerAndConfigurationSettings value, int index,
                                                 boolean selected, boolean hasFocus) {
              append(value.getName());
            }
          });
          final PopupChooserBuilder builder = new PopupChooserBuilder(list);
          builder.setTitle("Choose Jupyter Notebook Server");
          builder.setItemChoosenCallback(() -> {
            final RunnerAndConfigurationSettings configuration = list.getSelectedValue();
            ExecutionUtil.runConfiguration(configuration, DefaultRunExecutor.getRunExecutorInstance());
          });
          final JBPopup popup = builder.createPopup();
          final PointerInfo pointerInfo = MouseInfo.getPointerInfo();
          if (pointerInfo == null) return;
          final Point point = pointerInfo.getLocation();
          popup.showInScreenCoordinates(WindowManagerEx.getInstanceEx().getMostRecentFocusedWindow(), point);
        }
      }
    }
  }
}
