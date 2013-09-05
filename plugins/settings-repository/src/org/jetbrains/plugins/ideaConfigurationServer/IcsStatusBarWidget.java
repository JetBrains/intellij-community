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

public class IcsStatusBarWidget implements StatusBarWidget, StatusBarWidget.IconPresentation, ApplicationComponent {
  private StatusBar statusBar;

  private final StatusListener statusListener = new StatusListener() {
    @Override
    public void statusChanged(IdeaConfigurationServerStatus status) {
      update();
    }
  };

  @NotNull
  @Override
  public String ID() {
    return IcsManager.PLUGIN_NAME;
  }

  @Override
  public WidgetPresentation getPresentation(@NotNull PlatformType type) {
    return this;
  }

  @Override
  public void install(@NotNull StatusBar statusBar) {
    this.statusBar = statusBar;
    ApplicationManager.getApplication().getMessageBus().connect(this).subscribe(StatusListener.TOPIC, statusListener);
  }

  @Override
  public void dispose() {
    statusBar = null;
  }

  @NotNull
  @Override
  public Icon getIcon() {
    return getStatusIcon(IcsManager.getInstance().getStatus());
  }

  @Override
  public String getTooltipText() {
    return IcsManager.PLUGIN_NAME + " status: " + IcsManager.getInstance().getStatusText();
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

  private static Icon getStatusIcon(@NotNull IdeaConfigurationServerStatus status) {
    switch (status) {
      case OPEN_FAILED:
      case UPDATE_FAILED:
        return AllIcons.Nodes.ExceptionClass;
      default:
        return AllIcons.Nodes.Read_access;
    }
  }

  private void update() {
    Application app = ApplicationManager.getApplication();
    if (app.isDispatchThread()) {
      statusBar.updateWidget(this.ID());
    }
    else {
      app.invokeLater(new Runnable() {
        @Override
        public void run() {
          statusBar.updateWidget(IcsStatusBarWidget.this.ID());
        }
      });
    }
  }

  private static class MySettingsDialog extends DialogWrapper {
    public MySettingsDialog() {
      super(true);

      init();
      setTitle(IcsManager.PLUGIN_NAME + " Settings");
    }

    @Override
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
        frame.getStatusBar().addWidget(IcsStatusBarWidget.this);
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