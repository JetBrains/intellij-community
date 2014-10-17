package org.jetbrains.plugins.ipnb.configuration;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.HyperlinkAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ipnb.editor.IpnbFileEditor;
import org.jetbrains.plugins.ipnb.editor.panels.code.IpnbCodePanel;
import org.jetbrains.plugins.ipnb.format.cells.output.IpnbOutputCell;
import org.jetbrains.plugins.ipnb.protocol.IpnbConnection;
import org.jetbrains.plugins.ipnb.protocol.IpnbConnectionListenerBase;

import javax.swing.event.HyperlinkEvent;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class IpnbConnectionManager implements ProjectComponent {
  private static final Logger LOG = Logger.getInstance(IpnbConnectionManager.class);
  private final Project myProject;
  private Map<String, IpnbConnection> myKernels = new HashMap<String, IpnbConnection>();
  private Map<String, IpnbCodePanel> myUpdateMap = new HashMap<String, IpnbCodePanel>();

  public IpnbConnectionManager(final Project project) {
    myProject = project;
  }

  public static IpnbConnectionManager getInstance(Project project) {
    return project.getComponent(IpnbConnectionManager.class);
  }

  public void executeCell(@NotNull final IpnbCodePanel codePanel) {
    final IpnbFileEditor fileEditor = codePanel.getFileEditor();
    final String path = fileEditor.getVirtualFile().getPath();
    if (!myKernels.containsKey(path)) {
      try {
        final String url = IpnbSettings.getInstance(myProject).getURL();
        if (StringUtil.isEmptyOrSpaces(url)) {
          BalloonBuilder balloonBuilder = JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(
            "Please, specify IPython Notebook URL in <a href=\"\">Settings->IPython Notebook</a>", null,
            MessageType.WARNING.getPopupBackground(),
            new HyperlinkAdapter() {
              @Override
              protected void hyperlinkActivated(HyperlinkEvent e) {
                ShowSettingsUtil.getInstance().showSettingsDialog(myProject, "IPython Notebook");
              }
            });
          final Balloon balloon = balloonBuilder.createBalloon();
          balloon.showInCenterOf(fileEditor.getRunCellButton());
          return;
        }
        final IpnbConnection connection = new IpnbConnection(new URI(url), new IpnbConnectionListenerBase() {
          @Override
          public void onOpen(@NotNull IpnbConnection connection) {
            final String messageId = connection.execute(codePanel.getCell().getSourceAsString());
            myUpdateMap.put(messageId, codePanel);
          }

          @Override
          public void onOutput(@NotNull IpnbConnection connection,
                               @NotNull String parentMessageId,
                               @NotNull List<IpnbOutputCell> outputs,
                               @Nullable Integer execCount) {
            if (!myUpdateMap.containsKey(parentMessageId)) return;
            final IpnbCodePanel cell = myUpdateMap.remove(parentMessageId);
            cell.getCell().setPromptNumber(execCount);
            cell.updatePanel(outputs);
          }
        });
        myKernels.put(path, connection);
      }
      catch (IOException e) {
        BalloonBuilder balloonBuilder = JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(
          "Please, check that IPython Notebook is running", null, MessageType.WARNING.getPopupBackground(), null);
        final Balloon balloon = balloonBuilder.createBalloon();
        balloon.showInCenterOf(fileEditor.getRunCellButton());
      }
      catch (URISyntaxException e) {
        BalloonBuilder balloonBuilder = JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(
          "Please, check IPython Notebook URL in Settings->IPython Notebook", null, MessageType.WARNING.getPopupBackground(), null);
        final Balloon balloon = balloonBuilder.createBalloon();
        balloon.showInCenterOf(fileEditor.getRunCellButton());
      }
    }
    else {
      final IpnbConnection connection = myKernels.get(path);
      if (connection != null) {
        final String messageId = connection.execute(codePanel.getCell().getSourceAsString());
        myUpdateMap.put(messageId, codePanel);
      }
    }
  }

  public void projectOpened() {}


  public void projectClosed() {
    shutdownKernels();
  }

  private void shutdownKernels() {
    for (IpnbConnection connection : myKernels.values()) {
      connection.shutdown();
      try {
        connection.close();
      }
      catch (IOException e) {
        LOG.error(e);
      }
      catch (InterruptedException e) {
        LOG.error(e);
      }
    }
    myKernels.clear();
  }

  @NotNull
  public String getComponentName() {
    return "IpnbConnectionManager";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
    shutdownKernels();
  }
}
