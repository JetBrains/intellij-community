package org.jetbrains.plugins.terminal;

import com.intellij.icons.AllIcons;
import com.intellij.ide.actions.ToggleDistractionFreeModeAction;
import com.intellij.ide.actions.ToggleToolbarAction;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.internal.statistic.UsageTrigger;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.openapi.wm.impl.InternalDecorator;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.awt.RelativeRectangle;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.docking.DockContainer;
import com.intellij.ui.docking.DockManager;
import com.intellij.ui.docking.DockableContent;
import com.intellij.util.ui.UIUtil;
import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.ui.JediTermWidget;
import com.jediterm.terminal.ui.TabbedTerminalWidget;
import com.jediterm.terminal.ui.TerminalWidget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.terminal.vfs.TerminalSessionVirtualFileImpl;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

/**
 * @author traff
 */
public class TerminalView {
  private final static String TERMINAL_FEATURE = "terminal";

  private JBTabbedTerminalWidget myTerminalWidget;

  private final Project myProject;

  private TerminalDockContainer myDockContainer;

  @Nullable
  private VirtualFile myFileToOpen;

  public TerminalView(Project project) {
    myProject = project;
  }

  public static TerminalView getInstance(@NotNull Project project) {
    return project.getComponent(TerminalView.class);
  }

  public void initTerminal(final ToolWindow toolWindow) {
    LocalTerminalDirectRunner terminalRunner = LocalTerminalDirectRunner.createTerminalRunner(myProject);

    toolWindow.setToHideOnEmptyContent(true);

    Content content = createTerminalInContentPanel(terminalRunner, toolWindow);

    toolWindow.getContentManager().addContent(content);

    ((ToolWindowManagerEx)ToolWindowManager.getInstance(myProject)).addToolWindowManagerListener(new ToolWindowManagerListener() {
      @Override
      public void toolWindowRegistered(@NotNull String id) {
      }

      @Override
      public void stateChanged() {
        ToolWindow window = ToolWindowManager.getInstance(myProject).getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID);
        if (window != null) {
          boolean visible = window.isVisible();
          if (visible) {
            if (toolWindow.getContentManager().getContentCount() == 0) {
              initTerminal(window);
            }
            else if (myFileToOpen != null) {
              terminalRunner.openSessionForFile(myTerminalWidget, myFileToOpen);
            }
            myFileToOpen = null;
          }
        }
      }
    });

    Disposer.register(myProject, new Disposable() {
      @Override
      public void dispose() {
        if (myTerminalWidget != null) {
          myTerminalWidget.dispose();
          myTerminalWidget = null;
        }
      }
    });

    if (myDockContainer == null) {
      myDockContainer = new TerminalDockContainer(toolWindow);

      Disposer.register(myProject, myDockContainer);
      DockManager.getInstance(myProject).register(myDockContainer);
    }
  }

  private Content createTerminalInContentPanel(@NotNull AbstractTerminalRunner terminalRunner,
                                               final @NotNull ToolWindow toolWindow) {
    TerminalToolWindowPanel panel = new TerminalToolWindowPanel(PropertiesComponent.getInstance(myProject), toolWindow);

    final Content content = ContentFactory.SERVICE.getInstance().createContent(panel, "", false);
    content.setCloseable(true);

    myTerminalWidget = terminalRunner.createTerminalWidget(content);
    myTerminalWidget.addTabListener(new TabbedTerminalWidget.TabListener() {
      @Override
      public void tabClosed(JediTermWidget terminal) {
        UIUtil.invokeLaterIfNeeded(() -> {
          if (myTerminalWidget != null) {
            hideIfNoActiveSessions(toolWindow, myTerminalWidget);
          }
        });
      }
    });

    panel.setContent(myTerminalWidget.getComponent());
    panel.addFocusListener(createFocusListener());

    ActionToolbar toolbar = createToolbar(terminalRunner, myTerminalWidget, toolWindow);
    toolbar.getComponent().addFocusListener(createFocusListener());
    toolbar.setTargetComponent(panel);
    panel.setToolbar(toolbar.getComponent());
    panel.uiSettingsChanged(null);
    
    content.setPreferredFocusableComponent(myTerminalWidget.getComponent());

    return content;
  }

  private FocusListener createFocusListener() {
    return new FocusListener() {
      @Override
      public void focusGained(FocusEvent e) {
        JComponent component = getComponentToFocus();
        if (component != null) {
          component.requestFocusInWindow();
        }
      }

      @Override
      public void focusLost(FocusEvent e) {

      }
    };
  }

  private JComponent getComponentToFocus() {
    return myTerminalWidget != null ? myTerminalWidget.getComponent() : null;
  }

  @Nullable
  public VirtualFile getFileToOpen() {
    return myFileToOpen;
  }

  public void setFileToOpen(@Nullable VirtualFile fileToOpen) {
    myFileToOpen = fileToOpen;
  }

  public void openLocalSession(Project project, ToolWindow terminal) {
    LocalTerminalDirectRunner terminalRunner = LocalTerminalDirectRunner.createTerminalRunner(project);
    openSession(terminal, terminalRunner);
  }

  private void openSession(@NotNull ToolWindow toolWindow, @NotNull AbstractTerminalRunner terminalRunner) {
    if (myTerminalWidget == null) {
      toolWindow.getContentManager().removeAllContents(true);
      final Content content = createTerminalInContentPanel(terminalRunner, toolWindow);
      toolWindow.getContentManager().addContent(content);
    }
    else {
      terminalRunner.openSession(myTerminalWidget);
    }

    toolWindow.activate(() -> {

    }, true);
  }

  public static void recordUsage(@NotNull TtyConnector ttyConnector) {
    UsageTrigger.trigger(TERMINAL_FEATURE + "." +
                         (ttyConnector.toString().contains("Jsch") ? "ssh" :
                          SystemInfo.isWindows ? "win" : SystemInfo.isMac ? "mac" : "linux"));
  }

  private static ActionToolbar createToolbar(@Nullable final AbstractTerminalRunner terminalRunner,
                                             @NotNull final JBTabbedTerminalWidget terminal, @NotNull ToolWindow toolWindow) {
    DefaultActionGroup group = new DefaultActionGroup();

    if (terminalRunner != null) {
      group.add(new NewSession(terminalRunner, terminal));
      group.add(new CloseSession(terminal, toolWindow));
    }

    return ActionManager.getInstance().createActionToolbar("Terminal", group, false);
  }

  public void createNewSession(Project project, final AbstractTerminalRunner terminalRunner) {
    final ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Terminal");

    toolWindow.activate(() -> openSession(toolWindow, terminalRunner), true);
  }

  private static void hideIfNoActiveSessions(@NotNull final ToolWindow toolWindow, @NotNull JBTabbedTerminalWidget terminal) {
    if (terminal.isNoActiveSessions()) {
      toolWindow.getContentManager().removeAllContents(true);
    }
  }


  private static class NewSession extends DumbAwareAction {
    private final AbstractTerminalRunner myTerminalRunner;
    private final TerminalWidget myTerminal;

    public NewSession(@NotNull AbstractTerminalRunner terminalRunner, @NotNull TerminalWidget terminal) {
      super("New Session", "Create New Terminal Session", AllIcons.General.Add);
      myTerminalRunner = terminalRunner;
      myTerminal = terminal;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myTerminalRunner.openSession(myTerminal);
    }
  }

  private static class CloseSession extends DumbAwareAction {
    private final JBTabbedTerminalWidget myTerminal;
    private ToolWindow myToolWindow;

    public CloseSession(@NotNull JBTabbedTerminalWidget terminal, @NotNull ToolWindow toolWindow) {
      super("Close Session", "Close Terminal Session", AllIcons.Actions.Delete);
      myTerminal = terminal;
      myToolWindow = toolWindow;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myTerminal.closeCurrentSession();

      hideIfNoActiveSessions(myToolWindow, myTerminal);
    }
  }

  /**
   * @author traff
   */
  public class TerminalDockContainer implements DockContainer {
    private ToolWindow myTerminalToolWindow;

    public TerminalDockContainer(ToolWindow toolWindow) {
      myTerminalToolWindow = toolWindow;
    }

    @Override
    public RelativeRectangle getAcceptArea() {
      return new RelativeRectangle(myTerminalToolWindow.getComponent());
    }

    @Override
    public RelativeRectangle getAcceptAreaFallback() {
      return getAcceptArea();
    }

    @NotNull
    @Override
    public ContentResponse getContentResponse(@NotNull DockableContent content, RelativePoint point) {
      return isTerminalSessionContent(content) ? ContentResponse.ACCEPT_MOVE : ContentResponse.DENY;
    }

    @Override
    public JComponent getContainerComponent() {
      return myTerminalToolWindow.getComponent();
    }

    @Override
    public void add(@NotNull DockableContent content, RelativePoint dropTarget) {
      if (isTerminalSessionContent(content)) {
        TerminalSessionVirtualFileImpl terminalFile = (TerminalSessionVirtualFileImpl)content.getKey();
        myTerminalWidget.addTab(terminalFile.getName(), terminalFile.getTerminal());
        terminalFile.getTerminal().setNextProvider(myTerminalWidget);
      }
    }

    private boolean isTerminalSessionContent(DockableContent content) {
      return content.getKey() instanceof TerminalSessionVirtualFileImpl;
    }

    @Override
    public void closeAll() {

    }

    @Override
    public void addListener(Listener listener, Disposable parent) {

    }

    @Override
    public boolean isEmpty() {
      return false;
    }

    @Nullable
    @Override
    public Image startDropOver(@NotNull DockableContent content, RelativePoint point) {
      return null;
    }

    @Nullable
    @Override
    public Image processDropOver(@NotNull DockableContent content, RelativePoint point) {
      return null;
    }

    @Override
    public void resetDropOver(@NotNull DockableContent content) {

    }

    @Override
    public boolean isDisposeWhenEmpty() {
      return false;
    }

    @Override
    public void showNotify() {

    }

    @Override
    public void hideNotify() {

    }

    @Override
    public void dispose() {

    }
  }
}


class TerminalToolWindowPanel extends SimpleToolWindowPanel implements UISettingsListener {
  private final PropertiesComponent myPropertiesComponent;
  private final ToolWindow myWindow;

  public TerminalToolWindowPanel(PropertiesComponent propertiesComponent, ToolWindow window) {
    super(false, true);
    myPropertiesComponent = propertiesComponent;
    myWindow = window;
  }

  @Override
  public void uiSettingsChanged(UISettings uiSettings) {
    updateDFState();
  }

  private void updateDFState() {
    if (isDfmSupportEnabled()) {
      setDistractionFree(shouldMakeDistractionFree());
    }
  }

  private void setDistractionFree(boolean isDistractionFree) {
    boolean isVisible = !isDistractionFree;
    setToolbarVisible(isVisible);
    setToolWindowHeaderVisible(isVisible);
  }

  private void setToolbarVisible(boolean isVisible) {
    ToggleToolbarAction.setToolbarVisible(myWindow, myPropertiesComponent, isVisible);
  }

  private void setToolWindowHeaderVisible(boolean isVisible) {
    InternalDecorator decorator = ((ToolWindowEx)myWindow).getDecorator();
    decorator.setHeaderVisible(isVisible);
  }

  private boolean shouldMakeDistractionFree() {
    return !myWindow.getAnchor().isHorizontal() && ToggleDistractionFreeModeAction.isDistractionFreeModeEnabled();
  }

  @Override
  public void addNotify() {
    super.addNotify();
    updateDFState();
  }

  private static boolean isDfmSupportEnabled() {
    return Registry.get("terminal.distraction.free").asBoolean();
  }
}
