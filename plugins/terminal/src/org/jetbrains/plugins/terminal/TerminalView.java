// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal;

import com.google.common.collect.Sets;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.ShowContentAction;
import com.intellij.ide.actions.ToggleDistractionFreeModeAction;
import com.intellij.ide.actions.ToggleToolbarAction;
import com.intellij.ide.dnd.DnDDropHandler;
import com.intellij.ide.dnd.DnDEvent;
import com.intellij.ide.dnd.DnDSupport;
import com.intellij.ide.dnd.TransferableWrapper;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.openapi.wm.impl.InternalDecorator;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.terminal.JBTerminalWidget;
import com.intellij.terminal.JBTerminalWidgetListener;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.awt.RelativeRectangle;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.docking.DockContainer;
import com.intellij.ui.docking.DockManager;
import com.intellij.ui.docking.DockableContent;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.UniqueNameGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.terminal.action.MoveTerminalToolWindowTabLeftAction;
import org.jetbrains.plugins.terminal.action.MoveTerminalToolWindowTabRightAction;
import org.jetbrains.plugins.terminal.action.RenameTerminalSessionAction;
import org.jetbrains.plugins.terminal.arrangement.TerminalArrangementManager;
import org.jetbrains.plugins.terminal.arrangement.TerminalArrangementState;
import org.jetbrains.plugins.terminal.arrangement.TerminalWorkingDirectoryManager;
import org.jetbrains.plugins.terminal.ui.TerminalContainer;
import org.jetbrains.plugins.terminal.vfs.TerminalSessionVirtualFileImpl;

import javax.swing.*;
import java.awt.event.*;
import java.util.*;

public final class TerminalView {
  private final static Key<JBTerminalWidget> TERMINAL_WIDGET_KEY = new Key<>("TerminalWidget");
  private static final Logger LOG = Logger.getInstance(TerminalView.class);

  private ToolWindow myToolWindow;
  private final Project myProject;
  private final AbstractTerminalRunner<?> myTerminalRunner;
  private TerminalDockContainer myDockContainer;
  private final Map<JBTerminalWidget, TerminalContainer> myContainerByWidgetMap = new HashMap<>();

  @NotNull
  public AbstractTerminalRunner<?> getTerminalRunner() {
    return myTerminalRunner;
  }

  public TerminalView(@NotNull Project project) {
    myProject = project;
    myTerminalRunner = ApplicationManager.getApplication()
      .getService(DefaultTerminalRunnerFactory.class)
      .create(project);
  }

  public static TerminalView getInstance(@NotNull Project project) {
    return project.getService(TerminalView.class);
  }

  void initToolWindow(@NotNull ToolWindowEx toolWindow) {
    if (myToolWindow != null) {
      LOG.error("Terminal tool window already initialized");
      return;
    }
    myToolWindow = toolWindow;

    toolWindow.setTabActions(
      new DumbAwareAction(IdeBundle.messagePointer("action.DumbAware.TerminalView.text.new.session"),
                          IdeBundle.messagePointer("action.DumbAware.TerminalView.description.create.new.session"), AllIcons.General.Add) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          newTab(toolWindow, null);
        }
      });
    toolWindow.setTabDoubleClickActions(Collections.singletonList(new RenameTerminalSessionAction()));
    toolWindow.setToHideOnEmptyContent(true);

    myProject.getMessageBus().connect().subscribe(ToolWindowManagerListener.TOPIC, new ToolWindowManagerListener() {
      @Override
      public void toolWindowShown(@NotNull String id, @NotNull ToolWindow toolWindow) {
        if (TerminalToolWindowFactory.TOOL_WINDOW_ID.equals(id) && myToolWindow == toolWindow &&
            toolWindow.isVisible() && toolWindow.getContentManager().getContentCount() == 0) {
          // open a new session if all tabs were closed manually
          createNewSession(myTerminalRunner, null, true);
        }
      }
    });

    if (myDockContainer == null) {
      myDockContainer = new TerminalDockContainer();
      DockManager.getInstance(myProject).register(myDockContainer, toolWindow.getDisposable());
    }
  }

  void restoreTabs(@Nullable TerminalArrangementState arrangementState) {
    ContentManager contentManager = myToolWindow.getContentManager();

    if (arrangementState != null) {
      for (TerminalTabState tabState : arrangementState.myTabStates) {
        createNewSession(myTerminalRunner, tabState, false);
      }

      Content content = contentManager.getContent(arrangementState.mySelectedTabIndex);
      if (content != null) {
        contentManager.setSelectedContent(content);
      }
    }

    if (contentManager.getContentCount() == 0) {
      createNewSession(myTerminalRunner, null);
    }
  }

  public void createNewSession(@NotNull AbstractTerminalRunner<?> terminalRunner) {
    createNewSession(terminalRunner, null);
  }

  public void createNewSession(@NotNull AbstractTerminalRunner<?> terminalRunner, @Nullable TerminalTabState tabState) {
    createNewSession(terminalRunner, tabState, true);
  }

  @NotNull
  public ShellTerminalWidget createLocalShellWidget(@Nullable String workingDirectory, @Nullable String tabName) {
    TerminalTabState tabState = new TerminalTabState();
    tabState.myTabName = tabName;
    tabState.myWorkingDirectory = workingDirectory;
    JBTerminalWidget widget = createNewSession(myTerminalRunner, tabState, true);
    return (ShellTerminalWidget)Objects.requireNonNull(widget);
  }

  @NotNull
  private JBTerminalWidget createNewSession(@NotNull AbstractTerminalRunner<?> terminalRunner,
                                            @Nullable TerminalTabState tabState,
                                            boolean requestFocus) {
    ToolWindow toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID);
    // ensure #initToolWindow is called
    Objects.requireNonNull(toolWindow).activate(null);
    LOG.assertTrue(toolWindow == myToolWindow);
    Content content = createNewTab(null, terminalRunner, myToolWindow, tabState, requestFocus);
    return Objects.requireNonNull(getWidgetByContent(content));
  }

  @NotNull
  private Content newTab(@NotNull ToolWindow toolWindow, @Nullable JBTerminalWidget terminalWidget) {
    return createNewTab(terminalWidget, myTerminalRunner, toolWindow, null, true);
  }

  @NotNull
  private Content createNewTab(@Nullable JBTerminalWidget terminalWidget,
                               @NotNull AbstractTerminalRunner<?> terminalRunner,
                               @NotNull ToolWindow toolWindow,
                               @Nullable TerminalTabState tabState,
                               boolean requestFocus) {
    final Content content = createTerminalContent(terminalRunner, toolWindow, terminalWidget, tabState);
    final ContentManager contentManager = toolWindow.getContentManager();
    contentManager.addContent(content);
    new TerminalTabCloseListener(content, myProject);
    contentManager.setSelectedContent(content, requestFocus);
    return content;
  }

  private static String generateUniqueName(String suggestedName, List<String> tabs) {
    final Set<String> names = Sets.newHashSet(tabs);

    return UniqueNameGenerator.generateUniqueName(suggestedName, "", "", " (", ")", o -> !names.contains(o));
  }

  @NotNull
  private Content createTerminalContent(@NotNull AbstractTerminalRunner<?> terminalRunner,
                                        @NotNull ToolWindow toolWindow,
                                        @Nullable JBTerminalWidget terminalWidget,
                                        @Nullable TerminalTabState tabState) {
    TerminalToolWindowPanel panel = new TerminalToolWindowPanel(PropertiesComponent.getInstance(myProject), toolWindow);

    String tabName = ObjectUtils.notNull(tabState != null ? tabState.myTabName : null,
                                         TerminalOptionsProvider.getInstance().getTabName());

    Content content = ContentFactory.SERVICE.getInstance().createContent(panel, tabName, false);

    if (terminalWidget == null) {
      VirtualFile currentWorkingDir = getCurrentWorkingDir(tabState);
      terminalWidget = terminalRunner.createTerminalWidget(content, currentWorkingDir);
      TerminalArrangementManager.getInstance(myProject).register(terminalWidget, tabState);
      TerminalWorkingDirectoryManager.setInitialWorkingDirectory(content, currentWorkingDir);
    }
    else {
      terminalWidget.setVirtualFile(null);
      terminalWidget.moveDisposable(content);
    }
    setupTerminalWidget(toolWindow, terminalWidget, tabState, content, true);

    content.setCloseable(true);
    content.putUserData(TERMINAL_WIDGET_KEY, terminalWidget);

    TerminalContainer container = new TerminalContainer(myProject, content, terminalWidget, this);
    panel.setContent(container.getComponent());
    panel.addFocusListener(createFocusListener(toolWindow));

    panel.updateDFState();

    updatePreferredFocusableComponent(content, terminalWidget);

    return content;
  }

  private void setupTerminalWidget(@NotNull ToolWindow toolWindow,
                                   @NotNull JBTerminalWidget terminalWidget,
                                   @Nullable TerminalTabState tabState,
                                   @NotNull Content content,
                                   boolean updateContentDisplayName) {
    MoveTerminalToolWindowTabLeftAction moveTabLeftAction = new MoveTerminalToolWindowTabLeftAction();
    MoveTerminalToolWindowTabRightAction moveTabRightAction = new MoveTerminalToolWindowTabRightAction();
    terminalWidget.setListener(new JBTerminalWidgetListener() {
      @Override
      public void onNewSession() {
        newTab(toolWindow, null);
      }

      @Override
      public void onTerminalStarted() {
        if (updateContentDisplayName && (tabState == null || StringUtil.isEmpty(tabState.myTabName))) {
          String name = terminalWidget.getSettingsProvider().tabName(terminalWidget.getTtyConnector(),
                                                                     terminalWidget.getSessionName());
          List<Content> contents = ContainerUtil.newArrayList(toolWindow.getContentManager().getContents());
          contents.remove(content);
          content.setDisplayName(generateUniqueName(name, ContainerUtil.map(contents, c -> c.getDisplayName())));
        }
      }

      @Override
      public void onPreviousTabSelected() {
        if (toolWindow.getContentManager().getContentCount() > 1) {
          toolWindow.getContentManager().selectPreviousContent();
        }
      }

      @Override
      public void onNextTabSelected() {
        if (toolWindow.getContentManager().getContentCount() > 1) {
          toolWindow.getContentManager().selectNextContent();
        }
      }

      @Override
      public void onSessionClosed() {
        terminalWidget.close();
      }

      @Override
      public void showTabs() {
        ShowContentAction action = new ShowContentAction(toolWindow, toolWindow.getComponent(), toolWindow.getContentManager());
        DataContext dataContext = DataManager.getInstance().getDataContext(toolWindow.getComponent());
        KeyEvent fakeKeyEvent = new KeyEvent(toolWindow.getComponent(), ActionEvent.ACTION_PERFORMED,
                                             System.currentTimeMillis(), 0, 0, '\0');
        AnActionEvent event = AnActionEvent.createFromInputEvent(fakeKeyEvent, ActionPlaces.UNKNOWN, null, dataContext);
        action.actionPerformed(event);
      }

      @Override
      public void moveTabRight() {
        moveTabRightAction.move(content, myProject);
      }

      @Override
      public void moveTabLeft() {
        moveTabLeftAction.move(content, myProject);
      }

      @Override
      public boolean canMoveTabRight() {
        return moveTabRightAction.isAvailable(content);
      }

      @Override
      public boolean canMoveTabLeft() {
        return moveTabLeftAction.isAvailable(content);
      }

      @Override
      public boolean canSplit(boolean vertically) {
        return true;
      }

      @Override
      public void split(boolean vertically) {
        TerminalView.this.split(terminalWidget, vertically);
      }

      @Override
      public boolean isGotoNextSplitTerminalAvailable() {
        return isSplitTerminal(terminalWidget);
      }

      @Override
      public void gotoNextSplitTerminal(boolean forward) {
        TerminalView.this.gotoNextSplitTerminal(terminalWidget, forward);
      }
    });
    terminalWidget.getTerminalPanel().addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        updatePreferredFocusableComponent(content, terminalWidget);
      }
    });
  }

  private static void updatePreferredFocusableComponent(@NotNull Content content, @NotNull JBTerminalWidget terminalWidget) {
    content.setPreferredFocusableComponent(terminalWidget.getPreferredFocusableComponent());
  }

  public boolean isSplitTerminal(@NotNull JBTerminalWidget widget) {
    TerminalContainer container = Objects.requireNonNull(myContainerByWidgetMap.get(widget));
    return container.isSplitTerminal();
  }

  public void gotoNextSplitTerminal(@NotNull JBTerminalWidget widget, boolean forward) {
    TerminalContainer container = Objects.requireNonNull(myContainerByWidgetMap.get(widget));
    JBTerminalWidget next = container.getNextSplitTerminal(forward);
    if (next != null) {
      container.requestFocus(next);
    }
  }

  public void split(@NotNull JBTerminalWidget widget, boolean vertically) {
    TerminalContainer container = Objects.requireNonNull(myContainerByWidgetMap.get(widget));
    JBTerminalWidget newWidget = myTerminalRunner.createTerminalWidget(container.getContent(), null);
    setupTerminalWidget(myToolWindow, newWidget, null, container.getContent(), false);
    container.split(!vertically, newWidget);
  }

  public void register(@NotNull TerminalContainer terminalContainer) {
    myContainerByWidgetMap.put(terminalContainer.getTerminalWidget(), terminalContainer);
  }

  public void unregister(@NotNull TerminalContainer terminalContainer) {
    myContainerByWidgetMap.remove(terminalContainer.getTerminalWidget());
    if (getWidgetByContent(terminalContainer.getContent()) == terminalContainer.getTerminalWidget()) {
      terminalContainer.getContent().putUserData(TERMINAL_WIDGET_KEY, findWidgetForContent(terminalContainer.getContent()));
    }
  }

  private  @Nullable JBTerminalWidget findWidgetForContent(@NotNull Content content) {
    JBTerminalWidget any = null;
    for (Map.Entry<JBTerminalWidget, TerminalContainer> entry : myContainerByWidgetMap.entrySet()) {
      if (entry.getValue().getContent() == content) {
        JBTerminalWidget terminalWidget = entry.getKey();
        any = terminalWidget;
        if (terminalWidget.getTerminalPanel().hasFocus()) {
          return terminalWidget;
        }
      }
    }
    return any;
  }

  public @NotNull TerminalContainer getContainer(@NotNull JBTerminalWidget terminalWidget) {
    return Objects.requireNonNull(myContainerByWidgetMap.get(terminalWidget));
  }

  @Nullable
  private static VirtualFile getCurrentWorkingDir(@Nullable TerminalTabState tabState) {
    String dir = tabState != null ? tabState.myWorkingDirectory : null;
    VirtualFile result = null;
    if (dir != null) {
      result = LocalFileSystem.getInstance().findFileByPath(dir);
    }
    return result;
  }

  public void closeTab(@NotNull Content content) {
    myToolWindow.getContentManager().removeContent(content, true, true, true);
  }

  @NotNull
  private static FocusListener createFocusListener(@NotNull ToolWindow toolWindow) {
    return new FocusListener() {
      @Override
      public void focusGained(FocusEvent e) {
        JComponent component = getComponentToFocus(toolWindow);
        if (component != null) {
          component.requestFocusInWindow();
        }
      }

      @Override
      public void focusLost(FocusEvent e) {
      }
    };
  }

  @Nullable
  private static JComponent getComponentToFocus(@NotNull ToolWindow toolWindow) {
    Content selectedContent = toolWindow.getContentManager().getSelectedContent();
    if (selectedContent != null) {
      return selectedContent.getPreferredFocusableComponent();
    }
    else {
      return toolWindow.getComponent();
    }
  }

  public void openTerminalIn(@Nullable VirtualFile fileToOpen) {
    TerminalTabState state = new TerminalTabState();
    if (fileToOpen != null) {
      state.myWorkingDirectory = fileToOpen.getPath();
    }
    createNewSession(myTerminalRunner, state);
  }

  @Nullable
  public static JBTerminalWidget getWidgetByContent(@NotNull Content content) {
    return content.getUserData(TERMINAL_WIDGET_KEY);
  }

  public void detachWidgetAndRemoveContent(@NotNull Content content) {
    ContentManager contentManager = myToolWindow.getContentManager();
    LOG.assertTrue(contentManager.getIndexOfContent(content) >= 0, "Not a terminal content");
    contentManager.removeContent(content, true);
    for (TerminalContainer container : myContainerByWidgetMap.values()) {
      if (container.getContent().equals(content)) {
        container.detachWidget();
      }
    }
    content.putUserData(TERMINAL_WIDGET_KEY, null);
  }

  private final class TerminalDockContainer implements DockContainer {
    @NotNull
    @Override
    public RelativeRectangle getAcceptArea() {
      return new RelativeRectangle(myToolWindow.getComponent());
    }

    @NotNull
    @Override
    public ContentResponse getContentResponse(@NotNull DockableContent content, RelativePoint point) {
      return isTerminalSessionContent(content) ? ContentResponse.ACCEPT_MOVE : ContentResponse.DENY;
    }

    @Override
    public JComponent getContainerComponent() {
      return myToolWindow.getComponent();
    }

    @Override
    public void add(@NotNull DockableContent content, RelativePoint dropTarget) {
      if (isTerminalSessionContent(content)) {
        TerminalSessionVirtualFileImpl terminalFile = (TerminalSessionVirtualFileImpl)content.getKey();
        String name = terminalFile.getName();
        Content newContent = newTab(myToolWindow, terminalFile.getTerminalWidget());
        newContent.setDisplayName(name);
      }
    }

    private boolean isTerminalSessionContent(@NotNull DockableContent<?> content) {
      return content.getKey() instanceof TerminalSessionVirtualFileImpl;
    }

    @Override
    public boolean isEmpty() {
      return false;
    }

    @Override
    public boolean isDisposeWhenEmpty() {
      return false;
    }
  }
}


class TerminalToolWindowPanel extends SimpleToolWindowPanel implements UISettingsListener {
  private final PropertiesComponent myPropertiesComponent;
  private final ToolWindow myWindow;

  TerminalToolWindowPanel(@NotNull PropertiesComponent propertiesComponent, @NotNull ToolWindow window) {
    super(false, true);

    myPropertiesComponent = propertiesComponent;
    myWindow = window;
    installDnD(window);
  }

  private static void installDnD(@NotNull ToolWindow window) {
    DnDDropHandler handler = new DnDDropHandler() {
      @Override
      public void drop(DnDEvent event) {
        TransferableWrapper tw = ObjectUtils.tryCast(event.getAttachedObject(), TransferableWrapper.class);
        if (tw != null) {
          PsiDirectory dir = getDirectory(ArrayUtil.getFirstElement(tw.getPsiElements()));
          if (dir != null && tw.getPsiElements().length == 1) {
            TerminalView view = TerminalView.getInstance(dir.getProject());
            TerminalTabState state = new TerminalTabState();
            state.myWorkingDirectory = dir.getVirtualFile().getPath();
            view.createNewSession(view.getTerminalRunner(), state);
          }
        }
      }
    };
    DnDSupport.createBuilder(window.getComponent()).setDropHandler(handler).install();
  }

  @Nullable
  private static PsiDirectory getDirectory(@Nullable PsiElement item) {
    if (item instanceof PsiFile) {
      return ((PsiFile)item).getParent();
    }
    return ObjectUtils.tryCast(item, PsiDirectory.class);
  }

  @Override
  public void uiSettingsChanged(@NotNull UISettings uiSettings) {
    updateDFState();
  }

  void updateDFState() {
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
