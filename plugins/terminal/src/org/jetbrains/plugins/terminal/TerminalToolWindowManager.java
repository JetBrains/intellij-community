// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal;

import com.google.common.collect.Sets;
import com.intellij.ide.DataManager;
import com.intellij.ide.actions.DistractionFreeModeController;
import com.intellij.ide.actions.ShowContentAction;
import com.intellij.ide.actions.ToggleToolbarAction;
import com.intellij.ide.dnd.DnDDropHandler;
import com.intellij.ide.dnd.DnDEvent;
import com.intellij.ide.dnd.DnDSupport;
import com.intellij.ide.dnd.TransferableWrapper;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ConfigImportHelper;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.util.registry.Registry;
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
import com.intellij.terminal.TerminalTitle;
import com.intellij.terminal.TerminalTitleListener;
import com.intellij.terminal.ui.TerminalWidget;
import com.intellij.terminal.ui.TerminalWidgetKt;
import com.intellij.toolWindow.InternalDecoratorImpl;
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
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.UniqueNameGenerator;
import kotlin.Unit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.terminal.action.MoveTerminalToolWindowTabLeftAction;
import org.jetbrains.plugins.terminal.action.MoveTerminalToolWindowTabRightAction;
import org.jetbrains.plugins.terminal.action.RenameTerminalSessionAction;
import org.jetbrains.plugins.terminal.arrangement.TerminalArrangementState;
import org.jetbrains.plugins.terminal.arrangement.TerminalCommandHistoryManager;
import org.jetbrains.plugins.terminal.arrangement.TerminalWorkingDirectoryManager;
import org.jetbrains.plugins.terminal.block.BlockTerminalPromotionService;
import org.jetbrains.plugins.terminal.ui.TerminalContainer;
import org.jetbrains.plugins.terminal.vfs.TerminalSessionVirtualFileImpl;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

@Service(Service.Level.PROJECT)
public final class TerminalToolWindowManager implements Disposable {
  private final static Key<TerminalWidget> TERMINAL_WIDGET_KEY = new Key<>("TerminalWidget");
  private static final Logger LOG = Logger.getInstance(TerminalToolWindowManager.class);
  private static final Key<AbstractTerminalRunner<?>> RUNNER_KEY = Key.create("RUNNER_KEY");

  private ToolWindow myToolWindow;
  private final Project myProject;
  private final AbstractTerminalRunner<?> myTerminalRunner;
  private TerminalDockContainer myDockContainer;
  private final Map<TerminalWidget, TerminalContainer> myContainerByWidgetMap = new HashMap<>();

  @NotNull
  public AbstractTerminalRunner<?> getTerminalRunner() {
    return myTerminalRunner;
  }


  public ToolWindow getToolWindow() {
    return myToolWindow;
  }

  public TerminalToolWindowManager(@NotNull Project project) {
    myProject = project;
    myTerminalRunner = DefaultTerminalRunnerFactory.getInstance().create(project);
  }

  @Override
  public void dispose() {
  }

  /**
   * @deprecated use {@link #getTerminalWidgets()} instead
   */
  @ApiStatus.Internal
  @Deprecated
  public Set<JBTerminalWidget> getWidgets() {
    return ContainerUtil.map2SetNotNull(myContainerByWidgetMap.keySet(),
                                        widget -> JBTerminalWidget.asJediTermWidget(widget));
  }

  public @NotNull Set<TerminalWidget> getTerminalWidgets() {
    return Collections.unmodifiableSet(myContainerByWidgetMap.keySet());
  }

  private final List<Consumer<TerminalWidget>> myTerminalSetupHandlers = new CopyOnWriteArrayList<>();

  public void addNewTerminalSetupHandler(@NotNull Consumer<TerminalWidget> listener, @NotNull Disposable parentDisposable) {
    myTerminalSetupHandlers.add(listener);
    if (!Disposer.tryRegister(parentDisposable, () -> { myTerminalSetupHandlers.remove(listener); })) {
      myTerminalSetupHandlers.remove(listener);
    }
  }

  public static TerminalToolWindowManager getInstance(@NotNull Project project) {
    return project.getService(TerminalToolWindowManager.class);
  }

  void initToolWindow(@NotNull ToolWindowEx toolWindow) {
    if (myToolWindow != null) {
      LOG.error("Terminal tool window already initialized");
      return;
    }
    myToolWindow = toolWindow;

    toolWindow.setTabActions(ActionManager.getInstance().getAction("TerminalToolwindowActionGroup"));
    toolWindow.setTabDoubleClickActions(Collections.singletonList(new RenameTerminalSessionAction()));

    myProject.getMessageBus().connect(toolWindow.getDisposable())
      .subscribe(ToolWindowManagerListener.TOPIC, new ToolWindowManagerListener() {
        @Override
        public void toolWindowShown(@NotNull ToolWindow toolWindow) {
          if (isTerminalToolWindow(toolWindow) && myToolWindow == toolWindow &&
              toolWindow.isVisible() && toolWindow.getContentManager().isEmpty()) {
            // open a new session if all tabs were closed manually
            createNewSession(myTerminalRunner, null, true, true);
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
        createNewSession(myTerminalRunner, tabState, false, true);
      }

      Content content = contentManager.getContent(arrangementState.mySelectedTabIndex);
      if (content != null) {
        contentManager.setSelectedContent(content);
      }
    }
  }

  public void createNewSession(@NotNull AbstractTerminalRunner<?> terminalRunner) {
    createNewSession(terminalRunner, null);
  }

  public void createNewSession(@NotNull AbstractTerminalRunner<?> terminalRunner, @Nullable TerminalTabState tabState) {
    createNewSession(terminalRunner, tabState, true);
  }

  public @NotNull TerminalWidget createNewSession() {
    return createNewSession(myTerminalRunner, null, true, true);
  }

  /**
   * @deprecated use {@link #createShellWidget(String, String, boolean, boolean)} instead
   */
  @Deprecated(forRemoval = true)
  public @NotNull ShellTerminalWidget createLocalShellWidget(@Nullable String workingDirectory, @Nullable @Nls String tabName) {
    return ShellTerminalWidget.toShellJediTermWidgetOrThrow(createShellWidget(workingDirectory, tabName, true, true));
  }

  /**
   * @deprecated use {@link #createShellWidget(String, String, boolean, boolean)} instead
   */
  @Deprecated(forRemoval = true)
  public @NotNull ShellTerminalWidget createLocalShellWidget(@Nullable String workingDirectory,
                                                             @Nullable @Nls String tabName,
                                                             boolean requestFocus) {
    return ShellTerminalWidget.toShellJediTermWidgetOrThrow(createShellWidget(workingDirectory, tabName, requestFocus, true));
  }

  /**
   * @deprecated use {@link #createShellWidget(String, String, boolean, boolean)} instead
   */
  @Deprecated(forRemoval = true)
  public @NotNull ShellTerminalWidget createLocalShellWidget(@Nullable String workingDirectory,
                                                             @Nullable @Nls String tabName,
                                                             boolean requestFocus,
                                                             boolean deferSessionStartUntilUiShown) {
    return ShellTerminalWidget.toShellJediTermWidgetOrThrow(
      createShellWidget(workingDirectory, tabName, requestFocus, deferSessionStartUntilUiShown));
  }

  public @NotNull TerminalWidget createShellWidget(@Nullable String workingDirectory,
                                                   @Nullable @Nls String tabName,
                                                   boolean requestFocus,
                                                   boolean deferSessionStartUntilUiShown) {
    TerminalTabState tabState = new TerminalTabState();
    tabState.myTabName = tabName;
    tabState.myWorkingDirectory = workingDirectory;
    return createNewSession(myTerminalRunner, tabState, requestFocus, deferSessionStartUntilUiShown);
  }

  private void createNewSession(@NotNull AbstractTerminalRunner<?> terminalRunner,
                                @Nullable TerminalTabState tabState,
                                boolean requestFocus) {
    createNewSession(terminalRunner, tabState, requestFocus, true);
  }

  private @NotNull TerminalWidget createNewSession(@NotNull AbstractTerminalRunner<?> terminalRunner,
                                                     @Nullable TerminalTabState tabState,
                                                     boolean requestFocus,
                                                     boolean deferSessionStartUntilUiShown) {
    ToolWindow toolWindow = getOrInitToolWindow();
    Content content = createNewTab(null, terminalRunner, toolWindow, tabState, requestFocus, deferSessionStartUntilUiShown);
    return Objects.requireNonNull(content.getUserData(TERMINAL_WIDGET_KEY));
  }

  private @NotNull ToolWindow getOrInitToolWindow() {
    ToolWindow toolWindow = myToolWindow;
    if (toolWindow == null) {
      toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID);
      Objects.requireNonNull(toolWindow).getContentManager(); // to call #initToolWindow
      LOG.assertTrue(toolWindow == myToolWindow);
    }
    return toolWindow;
  }

  @NotNull
  public Content newTab(@NotNull ToolWindow toolWindow, @Nullable TerminalWidget terminalWidget) {
    return createNewTab(terminalWidget, myTerminalRunner, toolWindow, null, true, true);
  }

  @NotNull
  private Content createNewTab(@Nullable TerminalWidget terminalWidget,
                               @NotNull AbstractTerminalRunner<?> terminalRunner,
                               @NotNull ToolWindow toolWindow,
                               @Nullable TerminalTabState tabState,
                               boolean requestFocus,
                               boolean deferSessionStartUntilUiShown) {
    TerminalStartupMoment startupMoment = requestFocus && deferSessionStartUntilUiShown ? new TerminalStartupMoment() : null;
    Content content = createTerminalContent(terminalRunner, toolWindow, terminalWidget, tabState, deferSessionStartUntilUiShown, startupMoment);
    content.putUserData(RUNNER_KEY, terminalRunner);
    final ContentManager contentManager = toolWindow.getContentManager();
    contentManager.addContent(content);
    new TerminalTabCloseListener(content, myProject, this);
    Runnable selectRunnable = () -> {
      contentManager.setSelectedContent(content, requestFocus);
    };
    if (requestFocus && !toolWindow.isActive()) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Activating " + toolWindow.getId() + " tool window");
      }
      toolWindow.activate(selectRunnable, true, true);
    }
    else {
      selectRunnable.run();
    }
    return content;
  }

  private static @Nls String generateUniqueName(@Nls String suggestedName, List<@Nls String> tabs) {
    final Set<String> names = Sets.newHashSet(tabs);

    return UniqueNameGenerator.generateUniqueName(suggestedName, "", "", " (", ")", o -> !names.contains(o));
  }

  @NotNull
  private Content createTerminalContent(@NotNull AbstractTerminalRunner<?> terminalRunner,
                                        @NotNull ToolWindow toolWindow,
                                        @Nullable TerminalWidget terminalWidget,
                                        @Nullable TerminalTabState tabState,
                                        boolean deferSessionStartUntilUiShown,
                                        @Nullable TerminalStartupMoment startupMoment) {
    TerminalToolWindowPanel panel = new TerminalToolWindowPanel(PropertiesComponent.getInstance(myProject), toolWindow);

    Content content = ContentFactory.getInstance().createContent(panel, null, false);

    TerminalWidget widget = terminalWidget;
    if (widget == null) {
      String currentWorkingDir = terminalRunner.getCurrentWorkingDir(tabState);
      NullableLazyValue<Path> commandHistoryFileLazyValue = NullableLazyValue.atomicLazyNullable(() -> {
        return TerminalCommandHistoryManager.getInstance().getOrCreateCommandHistoryFile(
          tabState != null ? tabState.myCommandHistoryFileName : null,
          myProject
        );
      });
      ShellStartupOptions startupOptions = new ShellStartupOptions.Builder()
        .workingDirectory(currentWorkingDir)
        .shellCommand(tabState != null ? tabState.myShellCommand : null)
        .commandHistoryFileProvider(() -> commandHistoryFileLazyValue.getValue())
        .startupMoment(startupMoment)
        .build();
      widget = terminalRunner.startShellTerminalWidget(content, startupOptions, deferSessionStartUntilUiShown);
      widget.getTerminalTitle().change(state -> {
        if (state.getDefaultTitle() == null) {
          state.setDefaultTitle(terminalRunner.getDefaultTabTitle());
        }
        return Unit.INSTANCE;
      });
      TerminalWorkingDirectoryManager.setInitialWorkingDirectory(content, currentWorkingDir);
    }
    else {
      TerminalWidgetKt.setNewParentDisposable(terminalWidget, content);
    }

    if (tabState != null && tabState.myTabName != null) {
      widget.getTerminalTitle().change(state -> {
        if (tabState.myIsUserDefinedTabTitle) {
          state.setUserDefinedTitle(tabState.myTabName);
        }
        else {
          state.setDefaultTitle(tabState.myTabName);
        }
        return null;
      });
    }
    updateTabTitle(widget.getTerminalTitle(), toolWindow, content);
    setupTerminalWidget(toolWindow, terminalRunner, widget, content);

    content.setCloseable(true);
    content.putUserData(TERMINAL_WIDGET_KEY, widget);

    TerminalContainer container = new TerminalContainer(myProject, content, widget, this);
    panel.setContent(container.getWrapperPanel());
    panel.addFocusListener(createFocusListener(toolWindow));

    TerminalWidget finalWidget = widget;
    myTerminalSetupHandlers.forEach(consumer -> consumer.accept(finalWidget));
    panel.updateDFState();

    content.setPreferredFocusedComponent(() -> finalWidget.getPreferredFocusableComponent());
    return content;
  }

  private void setupTerminalWidget(@NotNull ToolWindow toolWindow,
                                   @NotNull AbstractTerminalRunner<?> runner,
                                   @NotNull TerminalWidget widget,
                                   @NotNull Content content) {
    MoveTerminalToolWindowTabLeftAction moveTabLeftAction = new MoveTerminalToolWindowTabLeftAction();
    MoveTerminalToolWindowTabRightAction moveTabRightAction = new MoveTerminalToolWindowTabRightAction();

    widget.getTerminalTitle().addTitleListener(new TerminalTitleListener() {
      @Override
      public void onTitleChanged(@NotNull TerminalTitle terminalTitle) {
        ApplicationManager.getApplication().invokeLater(() -> {
          updateTabTitle(terminalTitle, toolWindow, content);
        }, myProject.getDisposed());
      }
    }, content);
    JBTerminalWidget terminalWidget = JBTerminalWidget.asJediTermWidget(widget);
    if (terminalWidget == null) return;

    terminalWidget.setListener(new JBTerminalWidgetListener() {
      @Override
      public void onNewSession() {
        newTab(toolWindow, null);
      }

      @Override
      public void onTerminalStarted() {
        boolean shouldShowPromotion = runner instanceof LocalBlockTerminalRunner blockRunner && blockRunner.shouldShowPromotion();
        boolean blockTerminalSupported = terminalWidget instanceof ShellTerminalWidget shellWidget &&
                                         isBlockTerminalSupported(shellWidget.getStartupOptions());
        // Show the promotion only if the current runner allows it and block terminal can be used with the shell started now.
        // And it is not the first launch of the IDE by the user.
        if (shouldShowPromotion && blockTerminalSupported && !ConfigImportHelper.isNewUser()) {
          BlockTerminalPromotionService.INSTANCE.showPromotionOnce(myProject, widget);
        }
      }

      /** Checks whether new terminal can be used with the shell, started with the provided options */
      private static boolean isBlockTerminalSupported(ShellStartupOptions options) {
        if (options == null) return false;
        List<String> command = options.getShellCommand();
        String shellPath = ContainerUtil.getFirstItem(command);
        if (shellPath == null) return false;
        String shellName = PathUtil.getFileName(shellPath);
        return LocalTerminalDirectRunner.isBlockTerminalSupported(shellName);
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
        getContainer(terminalWidget).closeAndHide();
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
        TerminalToolWindowManager.this.split(widget, vertically);
      }

      @Override
      public boolean isGotoNextSplitTerminalAvailable() {
        return isSplitTerminal(terminalWidget);
      }

      @Override
      public void gotoNextSplitTerminal(boolean forward) {
        TerminalToolWindowManager.this.gotoNextSplitTerminal(widget, forward);
      }
    });
  }

  private static void updateTabTitle(@NotNull TerminalTitle terminalTitle,
                                     @NotNull ToolWindow toolWindow,
                                     @NotNull Content content) {
    String title = terminalTitle.buildTitle();
    List<String> tabs = Arrays.stream(toolWindow.getContentManager().getContents())
      .filter(c -> c!= content)
      .map(c -> c.getDisplayName()).toList();
    String generatedName = generateUniqueName(title, tabs);
    content.setDisplayName(generatedName);
    terminalTitle.change((state) -> {
      state.setDefaultTitle(generatedName);
      return null;
    });
  }

  public boolean isSplitTerminal(@NotNull JBTerminalWidget widget) {
    TerminalContainer container = getContainer(widget);
    return container.isSplitTerminal();
  }

  public boolean isSplitTerminal(@NotNull TerminalWidget widget) {
    TerminalContainer container = getContainer(widget);
    return container != null && container.isSplitTerminal();
  }

  public void gotoNextSplitTerminal(@NotNull TerminalWidget widget, boolean forward) {
    TerminalContainer container = getContainer(widget);
    if (container != null) {
      TerminalWidget next = container.getNextSplitTerminal(forward);
      if (next != null) {
        next.requestFocus();
      }
    }
  }

  public void split(@NotNull TerminalWidget widget, boolean vertically) {
    TerminalContainer container = getContainer(widget);
    if (container != null) {
      String workingDirectory = TerminalWorkingDirectoryManager.getWorkingDirectory(widget);
      ShellStartupOptions startupOptions = ShellStartupOptionsKt.shellStartupOptions(workingDirectory);
      TerminalWidget newWidget = myTerminalRunner.startShellTerminalWidget(container.getContent(), startupOptions, true);
      setupTerminalWidget(myToolWindow, myTerminalRunner, newWidget, container.getContent());
      container.split(!vertically, newWidget);
    }
  }

  public void register(@NotNull TerminalContainer terminalContainer) {
    myContainerByWidgetMap.put(terminalContainer.getTerminalWidget(), terminalContainer);
  }

  public void unregister(@NotNull TerminalContainer terminalContainer) {
    myContainerByWidgetMap.remove(terminalContainer.getTerminalWidget());
    if (terminalContainer.getContent().getUserData(TERMINAL_WIDGET_KEY) == terminalContainer.getTerminalWidget()) {
      terminalContainer.getContent().putUserData(TERMINAL_WIDGET_KEY, findWidgetForContent(terminalContainer.getContent()));
    }
  }

  private @Nullable TerminalWidget findWidgetForContent(@NotNull Content content) {
    TerminalWidget any = null;
    for (Map.Entry<TerminalWidget, TerminalContainer> entry : myContainerByWidgetMap.entrySet()) {
      if (entry.getValue().getContent() == content) {
        TerminalWidget terminalWidget = entry.getKey();
        any = terminalWidget;
        if (terminalWidget.hasFocus()) {
          return terminalWidget;
        }
      }
    }
    return any;
  }

  /**
   * @deprecated use {@link #getContainer(TerminalWidget)} instead
   */
  @Deprecated(forRemoval = true)
  public @NotNull TerminalContainer getContainer(@NotNull JBTerminalWidget terminalWidget) {
    return Objects.requireNonNull(getContainer(terminalWidget.asNewWidget()));
  }

  public @Nullable TerminalContainer getContainer(@NotNull TerminalWidget terminalWidget) {
    return myContainerByWidgetMap.get(terminalWidget);
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
    VirtualFile parentDirectory = fileToOpen != null && !fileToOpen.isDirectory() ? fileToOpen.getParent() : fileToOpen;
    if (parentDirectory != null) {
      state.myWorkingDirectory = parentDirectory.getPath();
    }
    createNewSession(myTerminalRunner, state);
  }

  @Nullable
  public static JBTerminalWidget getWidgetByContent(@NotNull Content content) {
    TerminalWidget data = content.getUserData(TERMINAL_WIDGET_KEY);
    return data != null ? JBTerminalWidget.asJediTermWidget(data) : null;
  }

  public static @Nullable TerminalWidget findWidgetByContent(@NotNull Content content) {
    return content.getUserData(TERMINAL_WIDGET_KEY);
  }

  public static @Nullable AbstractTerminalRunner<?> getRunnerByContent(@NotNull Content content) {
    return content.getUserData(RUNNER_KEY);
  }

  public void detachWidgetAndRemoveContent(@NotNull Content content) {
    ContentManager contentManager = myToolWindow.getContentManager();
    LOG.assertTrue(contentManager.getIndexOfContent(content) >= 0, "Not a terminal content");
    TerminalTabCloseListener.Companion.executeContentOperationSilently(content, () -> {
      contentManager.removeContent(content, true);
      return Unit.INSTANCE;
    });
    content.putUserData(TERMINAL_WIDGET_KEY, null);
  }

  public static boolean isInTerminalToolWindow(@NotNull JBTerminalWidget widget) {
    DataContext dataContext = DataManager.getInstance().getDataContext(widget.getTerminalPanel());
    ToolWindow toolWindow = dataContext.getData(PlatformDataKeys.TOOL_WINDOW);
    return isTerminalToolWindow(toolWindow);
  }

  public static boolean isTerminalToolWindow(@Nullable ToolWindow toolWindow) {
    return toolWindow != null && TerminalToolWindowFactory.TOOL_WINDOW_ID.equals(toolWindow.getId());
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
    public @NotNull JComponent getContainerComponent() {
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

    private static boolean isTerminalSessionContent(@NotNull DockableContent<?> content) {
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


final class TerminalToolWindowPanel extends SimpleToolWindowPanel implements UISettingsListener {
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
            TerminalToolWindowManager view = TerminalToolWindowManager.getInstance(dir.getProject());
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
    return !myWindow.getAnchor().isHorizontal() && DistractionFreeModeController.isDistractionFreeModeEnabled();
  }

  @Override
  public void addNotify() {
    super.addNotify();
    updateDFState();
    InternalDecoratorImpl.componentWithEditorBackgroundAdded(this);
  }

  @Override
  public void removeNotify() {
    super.removeNotify();
    InternalDecoratorImpl.componentWithEditorBackgroundRemoved(this);
  }

  private static boolean isDfmSupportEnabled() {
    return Registry.get("terminal.distraction.free").asBoolean();
  }
}
