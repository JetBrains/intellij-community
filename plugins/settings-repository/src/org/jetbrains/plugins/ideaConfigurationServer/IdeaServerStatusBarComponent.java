package org.jetbrains.plugins.ideaConfigurationServer;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.wm.*;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.MouseEvent;

public class IdeaServerStatusBarComponent implements StatusBarWidget, StatusBarWidget.IconPresentation, ApplicationComponent {
  private final StatusListener myStatusListener = new StatusListener() {
    public void statusChanged(final IdeaServerStatus status) {
      update();
    }
  };

  private static final Icon DISCONNECTED_ICON = AllIcons.Nodes.ExceptionClass;
  private static final Icon LOGGED_IN_ICON = AllIcons.Nodes.Read_access;
  private static final Icon LOGGED_OUT_ICON = AllIcons.Nodes.Write_access;
  private StatusBar myStatusBar;

  @NotNull
  @Override
  public String ID() {
    return "IntelliJConfigurationServer";
  }

  @Override
  public WidgetPresentation getPresentation(@NotNull PlatformType type) {
    return this;
  }

  @Override
  public void install(@NotNull StatusBar statusBar) {
    myStatusBar = statusBar;
    IdeaConfigurationServerManager.getInstance().getIdeaServerSettings().addStatusListener(myStatusListener);
  }

  @Override
  public void dispose() {
    myStatusBar = null;
    IdeaConfigurationServerManager.getInstance().getIdeaServerSettings().removeStatusListener(myStatusListener);
  }

  @NotNull
  @Override
  public Icon getIcon() {
    return getStatusIcon(IdeaConfigurationServerManager.getInstance().getIdeaServerSettings().getStatus());
  }

  @Override
  public String getTooltipText() {
    return "IntelliJ Configuration Server status: " + IdeaConfigurationServerManager.getStatusText();
  }

  @Override
  public Consumer<MouseEvent> getClickConsumer() {
    return new Consumer<MouseEvent>() {
      @Override
      public void consume(MouseEvent event) {
        new MySettingsDialog().show();
      }
    };
  }

  private static Icon getStatusIcon(final IdeaServerStatus status) {
    switch (status) {
      case CONNECTION_FAILED:
        return DISCONNECTED_ICON;
      case LOGGED_IN:
        return LOGGED_IN_ICON;
      case LOGGED_OUT:
        return LOGGED_OUT_ICON;
      default:
        return DISCONNECTED_ICON;
    }
  }

  private void update() {
    Application app = ApplicationManager.getApplication();
    if (app.isDispatchThread()) {
      myStatusBar.updateWidget(this.ID());
    }
    else {
      app.invokeLater(new Runnable() {
        public void run() {
          myStatusBar.updateWidget(IdeaServerStatusBarComponent.this.ID());
        }
      });
    }
  }

  private static class MySettingsDialog extends DialogWrapper {
    public MySettingsDialog() {
      super(true);
      init();
      setTitle("IntelliJ Configuration Server Settings");
    }

    protected JComponent createCenterPanel() {
      return new IdeaServerPanel(this).getPanel();
    }

    @NotNull
    @Override
    protected Action[] createActions() {
      return new Action[]{getOKAction()};
    }
  }

  @Override
  public void initComponent() {
    WindowManager.getInstance().addListener(new WindowManagerListener() {
      @Override
      public void frameCreated(IdeFrame frame) {
        frame.getStatusBar().addWidget(IdeaServerStatusBarComponent.this);
        WindowManager.getInstance().removeListener(this);
      }

      @Override
      public void beforeFrameReleased(IdeFrame frame) {
      }
    });
  }

  @Override
  public void disposeComponent() {
  }

  @NotNull
  @Override
  public String getComponentName() {
    return ID();
  }
}
