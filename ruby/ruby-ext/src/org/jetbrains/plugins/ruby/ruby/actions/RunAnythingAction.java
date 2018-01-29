// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.ruby.ruby.actions;

import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.execution.Executor;
import com.intellij.execution.ExecutorRegistry;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ChooseRunConfigurationPopup;
import com.intellij.execution.actions.ExecutorProvider;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.IdeTooltipManager;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.laf.darcula.ui.DarculaTextBorder;
import com.intellij.ide.ui.laf.intellij.MacIntelliJTextBorder;
import com.intellij.ide.ui.laf.intellij.WinIntelliJTextBorder;
import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.internal.statistic.customUsageCollectors.ui.ToolbarClicksCollector;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.actionSystem.impl.PoppedIcon;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actions.TextComponentEditorAction;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.keymap.impl.ModifierKeyDoubleClickHandler;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ComponentPopupBuilder;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.*;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.util.Alarm;
import com.intellij.util.Consumer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.components.BorderLayoutPanel;
import icons.RubyIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.RBundle;
import org.jetbrains.plugins.ruby.gem.bundler.actions.AbstractBundlerAction;
import org.jetbrains.plugins.ruby.rails.actions.generators.actions.GeneratorsActionGroup;
import org.jetbrains.plugins.ruby.rails.facet.RailsFacetUtil;
import org.jetbrains.plugins.ruby.ruby.RModuleUtil;
import org.jetbrains.plugins.ruby.tasks.rake.RakeAction;
import org.jetbrains.plugins.ruby.tasks.rake.RakeTaskModuleCache;
import org.jetbrains.plugins.ruby.tasks.rake.task.RakeTask;

import javax.accessibility.Accessible;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.plaf.TextUI;
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.intellij.openapi.wm.IdeFocusManager.getGlobalInstance;
import static org.jetbrains.plugins.ruby.ruby.actions.RunAnythingIconHandler.*;
import static org.jetbrains.plugins.ruby.ruby.actions.RunAnythingUndefinedItem.UNDEFINED_COMMAND_ICON;

@SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
public class RunAnythingAction extends AnAction implements CustomComponentAction, DumbAware, DataProvider {
  public static final String RUN_ANYTHING_HISTORY_KEY = "RunAnythingHistoryKey";
  public static final int SEARCH_FIELD_COLUMNS = 25;
  public static final String UNKNOWN_CONFIGURATION = "UNKNOWN_CONFIGURATION";
  public static final String RUN_ICON_TEXT = "RUN_ICON_TEXT";
  public static final AtomicBoolean ourShiftIsPressed = new AtomicBoolean(false);
  public static final AtomicBoolean ourAltIsPressed = new AtomicBoolean(false);
  public static final Key<JBPopup> RUN_ANYTHING_POPUP = new Key<>("RunAnythingPopup");
  public static final String RUN_ANYTHING_ACTION_ID = "RunAnything";

  private static final int MAX_RAKE = 5;
  private static final int MAX_RUN_CONFIGURATION = 6;
  private static final int MAX_BUNDLER_ACTIONS = 2;
  private static final int MAX_UNDEFINED_FILES = 5;
  private static final int MAX_RUN_ANYTHING_HISTORY = 50;
  private static final int MAX_GENERATORS = 3;
  private static final int DEFAULT_MORE_STEP_COUNT = 5;
  private static final Logger LOG = Logger.getInstance(RunAnythingAction.class);
  private static final Border RENDERER_BORDER = JBUI.Borders.empty(1, 0);
  private static final String SHIFT_SHORTCUT_TEXT = KeymapUtil.getShortcutText(KeyboardShortcut.fromString(("SHIFT")));
  private static final String AD_ACTION_TEXT = String.format("Press %s to run with default settings", SHIFT_SHORTCUT_TEXT);
  private static final String AD_DEBUG_TEXT = String.format("%s to debug", SHIFT_SHORTCUT_TEXT);
  private static final String AD_MODULE_CONTEXT =
    String.format("Press %s to run in the current file context", KeymapUtil.getShortcutText(KeyboardShortcut.fromString("pressed ALT")));
  private static final Icon RUN_ANYTHING_POPPED_ICON = new PoppedIcon(RubyIcons.RunAnything.Run_anything, 16, 16);
  private AnAction[] myRakeActions = AnAction.EMPTY_ARRAY;
  private AnAction[] myGeneratorsActions = AnAction.EMPTY_ARRAY;
  private RunAnythingAction.MyListRenderer myRenderer;
  private MySearchTextField myPopupField;
  private Component myFocusComponent;
  private JBPopup myPopup;
  private Alarm myAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, ApplicationManager.getApplication());
  private JBList myList;
  private AnActionEvent myActionEvent;
  private Component myContextComponent;
  private CalcThread myCalcThread;
  private volatile ActionCallback myCurrentWorker = ActionCallback.DONE;
  private int myCalcThreadRestartRequestId = 0;
  private final Object myWorkerRestartRequestLock = new Object();
  private int myHistoryIndex = 0;
  private boolean mySkipFocusGain = false;
  private volatile JBPopup myBalloon;
  private int myPopupActualWidth;
  private Component myFocusOwner;
  private Editor myEditor;
  @Nullable
  private FileEditor myFileEditor;
  private RunAnythingHistoryItem myHistoryItem;
  private AnAction[] myBundlerActions;
  private JLabel myAdComponent;
  private DataContext myDataContext;
  private static final NotNullLazyValue<Map<String, Icon>> ourIconsMap;
  private JLabel myTextFieldTitle;
  private boolean myIsItemSelected;
  private String myLastInputText = null;

  static {
    ModifierKeyDoubleClickHandler.getInstance().registerAction(RUN_ANYTHING_ACTION_ID, KeyEvent.VK_CONTROL, -1, false);

    ourIconsMap = new NotNullLazyValue<Map<String, Icon>>() {
      @NotNull
      @Override
      protected Map<String, Icon> compute() {
        Map<String, Icon> map = ContainerUtil.newHashMap();
        map.put(UNKNOWN_CONFIGURATION, UNDEFINED_COMMAND_ICON);

        for (RunAnythingProvider provider : RunAnythingProvider.EP_NAME.getExtensions()) {
          map.put(provider.getConfigurationFactory().getName(), provider.getConfigurationFactory().getIcon());
        }

        return map;
      }
    };

    IdeEventQueue.getInstance().addPostprocessor(event -> {
      if (event instanceof KeyEvent) {
        final int keyCode = ((KeyEvent)event).getKeyCode();
        if (keyCode == KeyEvent.VK_SHIFT) {
          ourShiftIsPressed.set(event.getID() == KeyEvent.KEY_PRESSED);
        }
        else if (keyCode == KeyEvent.VK_ALT) {
          ourAltIsPressed.set(event.getID() == KeyEvent.KEY_PRESSED);
        }
      }
      return false;
    }, null);
  }

  @Override
  public JComponent createCustomComponent(Presentation presentation) {
    JPanel panel = new BorderLayoutPanel() {
      @Override
      public Dimension getPreferredSize() {
        return JBUI.size(25);
      }
    };
    panel.setOpaque(false);

    final JLabel label = new JBLabel(RubyIcons.RunAnything.Run_anything) {
      {
        enableEvents(AWTEvent.MOUSE_EVENT_MASK);
        enableEvents(AWTEvent.MOUSE_MOTION_EVENT_MASK);
      }
    };
    panel.add(label, BorderLayout.CENTER);
    RunAnythingUtil.initTooltip(label);
    label.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        if (myBalloon != null) {
          myBalloon.cancel();
        }
        myFocusOwner = IdeFocusManager.findInstance().getFocusOwner();
        label.setToolTipText(null);
        IdeTooltipManager.getInstance().hideCurrentNow(false);
        ActionToolbarImpl toolbar = UIUtil.getParentOfType(ActionToolbarImpl.class, panel);
        if (toolbar != null) {
          ToolbarClicksCollector.record(RunAnythingAction.this, toolbar.getPlace());
        }
        actionPerformed(null, e);
      }

      @Override
      public void mouseEntered(MouseEvent e) {
        if (myBalloon == null || myBalloon.isDisposed()) {
          label.setIcon(RUN_ANYTHING_POPPED_ICON);
        }
      }

      @Override
      public void mouseExited(MouseEvent e) {
        if (myBalloon == null || myBalloon.isDisposed()) {
          label.setIcon(RubyIcons.RunAnything.Run_anything);
        }
      }
    });

    return panel;
  }

  private void updateComponents() {
    //noinspection unchecked
    myList = new JBList(new RunAnythingSearchListModel()) {
      int lastKnownHeight = JBUI.scale(30);

      @Override
      public Dimension getPreferredSize() {
        final Dimension size = super.getPreferredSize();
        if (size.height == -1) {
          size.height = lastKnownHeight;
        }
        else {
          lastKnownHeight = size.height;
        }
        int width = myBalloon != null ? myBalloon.getSize().width : 0;
        return new Dimension(Math.max(width, Math.min(size.width - 2, RunAnythingUtil.getPopupMaxWidth())),
                             myList.isEmpty() ? JBUI.scale(30) : size.height);
      }

      @Override
      public void clearSelection() {
        //avoid blinking
      }

      @Override
      public Object getSelectedValue() {
        try {
          return super.getSelectedValue();
        }
        catch (Exception e) {
          return null;
        }
      }
    };
    myRenderer = new MyListRenderer();
    myList.setCellRenderer(myRenderer);

    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent event, int clickCount) {
        if (clickCount > 1 && clickCount % 2 == 0 || tryGetSettingsModel() != null) {
          event.consume();
          final int i = myList.locationToIndex(event.getPoint());
          if (i != -1) {
            getGlobalInstance().doWhenFocusSettlesDown(() -> getGlobalInstance().requestFocus(getField(), true));
            ApplicationManager.getApplication().invokeLater(() -> {
              myList.setSelectedIndex(i);
              executeCommand();
            });
          }
        }
        return false;
      }
    }.installOn(myList);
  }

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    return null;
  }

  private void initSearchField(final MySearchTextField search) {
    final JTextField editor = search.getTextEditor();
    //    onFocusLost();
    editor.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        final String pattern = editor.getText();
        if (editor.hasFocus()) {
          ApplicationManager.getApplication().invokeLater(() -> {
            myIsItemSelected = false;
          });

          if (!myIsItemSelected) {
            myLastInputText = null;
            clearSelection();

            rebuildList(pattern);
          }
        }
      }
    });
    editor.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        if (mySkipFocusGain) {
          mySkipFocusGain = false;
          return;
        }
        String text = RunAnythingUtil.getInitialTextForNavigation(myEditor);
        text = text != null ? text.trim() : "";

        search.setText(text);
        search.getTextEditor().setForeground(UIUtil.getLabelForeground());
        search.selectText();
        editor.setColumns(SEARCH_FIELD_COLUMNS);
        myFocusComponent = e.getOppositeComponent();
        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(() -> {
          final JComponent parent = (JComponent)editor.getParent();
          parent.revalidate();
          parent.repaint();
        });
        rebuildList(text);
      }

      @Override
      public void focusLost(FocusEvent e) {
        if (myPopup instanceof AbstractPopup && myPopup.isVisible()
            && ((myList == e.getOppositeComponent()) || ((AbstractPopup)myPopup).getPopupWindow() == e.getOppositeComponent())) {
          return;
        }

        onPopupFocusLost();
      }
    });
  }

  private void clearSelection() {
    myList.getSelectionModel().clearSelection();
  }

  @NotNull
  private ActionCallback onPopupFocusLost() {
    final ActionCallback result = new ActionCallback();
    //noinspection SSBasedInspection
    UIUtil.invokeLaterIfNeeded(() -> {
      try {
        if (myCalcThread != null) {
          myCalcThread.cancel();
          //myCalcThread = null;
        }
        myAlarm.cancelAllRequests();
        if (myBalloon != null && !myBalloon.isDisposed() && myPopup != null && !myPopup.isDisposed()) {
          myBalloon.cancel();
          myPopup.cancel();
        }

        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(() -> ActionToolbarImpl.updateAllToolbarsImmediately());
      }
      finally {
        result.setDone();
      }
    });
    return result;
  }

  private SearchTextField getField() {
    return myPopupField;
  }

  private void executeCommand() {
    final String pattern = getField().getText();
    int index = myList.getSelectedIndex();

    //do nothing on attempt to execute empty command
    if (pattern.isEmpty() && index == -1) return;

    final Project project = getProject();
    final Module module = getModule();

    if (index != -1) {

      final RunAnythingSearchListModel model = tryGetSearchingModel(myList);
      if (model != null) {
        if (isMoreItem(index)) {
          WidgetID wid = null;
          if (index == model.moreIndex.permanentRunConfigurations) {
            wid = WidgetID.PERMANENT;
          }
          else if (index == model.moreIndex.rakeTasks) {
            wid = WidgetID.RAKE;
          }
          else if (index == model.moreIndex.generators) {
            wid = WidgetID.GENERATORS;
          }
          else if (index == model.moreIndex.bundlerActions) {
            wid = WidgetID.BUNDLER;
          }
          else if (index == model.moreIndex.temporaryRunConfigurations) {
            wid = WidgetID.TEMPORARY;
          }
          else if (index == model.moreIndex.undefined) {
            wid = WidgetID.UNDEFINED;
          }

          if (wid != null) {
            final WidgetID widgetID = wid;
            myCurrentWorker.doWhenProcessed(() -> {
              myCalcThread = new CalcThread(project, pattern, true);
              myPopupActualWidth = 0;
              myCurrentWorker = myCalcThread.insert(index, widgetID);
            });

            return;
          }
        }
      }
    }

    final Object value = myList.getSelectedValue();
    saveHistory(project, pattern, value);
    IdeFocusManager focusManager = IdeFocusManager.findInstanceByComponent(getField().getTextEditor());
    if (myPopup != null && myPopup.isVisible()) {
      myPopup.cancel();
    }

    if (value instanceof BooleanOptionDescription) {
      updateOption((BooleanOptionDescription)value);
      return;
    }

    Runnable onDone = null;
    AccessToken token = ApplicationManager.getApplication().acquireReadActionLock();
    try {
      if (isActionValue(value) || isRunConfigurationItem(value)) {
        focusManager.requestDefaultFocus(true);
        final Component comp = myContextComponent;
        final AnActionEvent event = myActionEvent;
        IdeFocusManager.getInstance(project).doWhenFocusSettlesDown(() -> {
          Component c = comp;
          if (c == null) {
            c = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
          }

          if (isRunConfigurationItem(value)) {
            ChooseRunConfigurationPopup.ItemWrapper itemWrapper = (ChooseRunConfigurationPopup.ItemWrapper)value;
            RunnerAndConfigurationSettings settings = ObjectUtils.tryCast(itemWrapper.getValue(), RunnerAndConfigurationSettings.class);
            if (settings != null) {
              Executor executor = RunAnythingUtil.findExecutor(settings);
              if (executor != null) {
                itemWrapper.perform(project, executor, DataManager.getInstance().getDataContext(c));
              }
            }
          }
          else {
            RunAnythingUtil.performRunAnythingAction(value, project, c, event);
          }
        });
        return;
      }
      VirtualFile directory = getWorkDirectory(module);
      if (value instanceof RunAnythingUndefinedItem) {
        onDone = () -> ((RunAnythingUndefinedItem)value).run(getExecutor(), directory);
      }
      else if (value == null) {
        onDone = () -> RunAnythingUtil.runOrCreateRunConfiguration(project, pattern, module, directory);
        return;
      }
    }
    finally {
      token.finish();
      final ActionCallback callback = onPopupFocusLost();
      if (onDone != null) {
        callback.doWhenDone(onDone);
      }
    }
    focusManager.requestDefaultFocus(true);
  }

  @NotNull
  private Project getProject() {
    final Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(getField().getTextEditor()));
    assert project != null;
    return project;
  }

  @Nullable
  private Module getModule() {
    return RModuleUtil.getInstance().getModule(myDataContext);
  }

  @Nullable
  private VirtualFile getWorkDirectory(@Nullable Module module) {
    if (module == null) return null;

    VirtualFile workDirectory = RModuleUtil.getInstance().getFirstContentRoot(module);
    if (myFileEditor == null) return workDirectory;

    VirtualFile file = myFileEditor.getFile();
    if (file == null) return workDirectory;

    if (ourAltIsPressed.get()) {
      workDirectory = file.getParent();
    }
    return workDirectory;
  }

  private void updateOption(BooleanOptionDescription value) {
    value.setOptionState(!value.isOptionEnabled());
    myList.revalidate();
    myList.repaint();
    getGlobalInstance().doWhenFocusSettlesDown(() -> getGlobalInstance().requestFocus(getField(), true));
  }

  private boolean isMoreItem(int index) {
    RunAnythingSearchListModel model = tryGetSearchingModel(myList);
    if (model == null) return false;

    return index == model.moreIndex.permanentRunConfigurations ||
           index == model.moreIndex.rakeTasks ||
           index == model.moreIndex.bundlerActions ||
           index == model.moreIndex.generators ||
           index == model.moreIndex.undefined ||
           index == model.moreIndex.temporaryRunConfigurations;
  }

  @Nullable
  public static RunAnythingSearchListModel tryGetSearchingModel(@NotNull JBList list) {
    ListModel model = list.getModel();
    return model instanceof RunAnythingSearchListModel ? (RunAnythingSearchListModel)model : null;
  }

  @Nullable
  private RunAnythingSettingsModel tryGetSettingsModel() {
    ListModel model = myList.getModel();
    return model instanceof RunAnythingSettingsModel ? (RunAnythingSettingsModel)model : null;
  }

  private void rebuildList(final String pattern) {
    assert EventQueue.isDispatchThread() : "Must be EDT";
    if (myCalcThread != null && !myCurrentWorker.isProcessed()) {
      myCurrentWorker = myCalcThread.cancel();
    }
    if (myCalcThread != null && !myCalcThread.isCanceled()) {
      myCalcThread.cancel();
    }
    synchronized (myWorkerRestartRequestLock) { // this lock together with RestartRequestId should be enough to prevent two CalcThreads running at the same time
      final int currentRestartRequest = ++myCalcThreadRestartRequestId;
      myCurrentWorker.doWhenProcessed(() -> {
        synchronized (myWorkerRestartRequestLock) {
          if (currentRestartRequest != myCalcThreadRestartRequestId) {
            return;
          }
          myCalcThread = new CalcThread(getProject(), pattern, false);
          myPopupActualWidth = 0;
          myCurrentWorker = myCalcThread.start();
        }
      });
    }
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    if (Registry.is("ide.suppress.double.click.handler") && e.getInputEvent() instanceof KeyEvent) {
      if (((KeyEvent)e.getInputEvent()).getKeyCode() == KeyEvent.VK_CONTROL) {
        return;
      }
    }

    actionPerformed(e, null);
  }

  public void actionPerformed(AnActionEvent e, MouseEvent me) {
    if (myBalloon != null && myBalloon.isVisible()) {
      rebuildList(myPopupField.getText());
      return;
    }
    myCurrentWorker = ActionCallback.DONE;
    if (e != null) {
      myEditor = e.getData(CommonDataKeys.EDITOR);
      myFileEditor = e.getData(PlatformDataKeys.FILE_EDITOR);
    }
    if (e == null && myFocusOwner != null) {
      e = AnActionEvent.createFromAnAction(this, me, ActionPlaces.UNKNOWN, DataManager.getInstance().getDataContext(myFocusOwner));
    }
    if (e == null) return;
    final Project project = e.getProject();
    if (project == null) return;

    //noinspection SSBasedInspection
    SwingUtilities.invokeLater(() -> LookupManager.getInstance(project).hideActiveLookup());


    updateComponents();

    myContextComponent = PlatformDataKeys.CONTEXT_COMPONENT.getData(e.getDataContext());

    Window wnd = myContextComponent != null ? SwingUtilities.windowForComponent(myContextComponent)
                                            : KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
    if (wnd == null && myContextComponent instanceof Window) {
      wnd = (Window)myContextComponent;
    }
    if (wnd == null || wnd.getParent() != null) return;
    myActionEvent = e;

    Module module = RModuleUtil.getInstance().getModule(myActionEvent.getDataContext());

    myDataContext = SimpleDataContext.getSimpleContext(LangDataKeys.MODULE.getName(), module);
    initActions();

    if (myPopupField != null) {
      Disposer.dispose(myPopupField);
    }
    myPopupField = new MySearchTextField();
    myPopupField.setPreferredSize(new Dimension(500, 43));
    myPopupField.getTextEditor().setFont(EditorUtil.getEditorFont().deriveFont(18f));

    JBTextField myTextField = myPopupField.getTextEditor();
    myTextField.putClientProperty(MATCHED_CONFIGURATION_PROPERTY, UNKNOWN_CONFIGURATION);

    setHandleMatchedConfiguration(myTextField);

    myTextField.setMinimumSize(new Dimension(500, 50));

    myTextField.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
          case KeyEvent.VK_SHIFT:
            myTextFieldTitle.setText(RBundle.message("run.anything.run.debug.title"));
            break;
          case KeyEvent.VK_ALT:
            myTextFieldTitle.setText(RBundle.message("run.anything.run.in.context.title"));
            break;
        }
      }

      @Override
      public void keyReleased(KeyEvent e) {
        switch (e.getKeyCode()) {
          case KeyEvent.VK_SHIFT:
            myTextFieldTitle.setText(RBundle.message("run.anything.run.anything.title"));
            break;
          case KeyEvent.VK_ALT:
            myTextFieldTitle.setText(RBundle.message("run.anything.run.anything.title"));
            break;
        }
      }
    });

    myPopupField.getTextEditor().addKeyListener(new KeyAdapter() {
      @Override
      public void keyTyped(KeyEvent e) {
        myHistoryIndex = 0;
        myHistoryItem = null;
      }
    });
    initSearchField(myPopupField);

    JTextField editor = myPopupField.getTextEditor();
    editor.setColumns(SEARCH_FIELD_COLUMNS);
    JPanel panel = new JPanel(new BorderLayout());

    myTextFieldTitle = new JLabel(RBundle.message("run.anything.run.anything.title"));
    JPanel topPanel = new NonOpaquePanel(new BorderLayout());
    Color foregroundColor = UIUtil.isUnderDarcula()
                            ? UIUtil.isUnderWin10LookAndFeel() ? JBColor.WHITE : new JBColor(Gray._240, Gray._200)
                            : UIUtil.getLabelForeground();


    myTextFieldTitle.setForeground(foregroundColor);
    myTextFieldTitle.setBorder(BorderFactory.createEmptyBorder(3, 5, 5, 0));
    if (SystemInfo.isMac) {
      myTextFieldTitle.setFont(myTextFieldTitle.getFont().deriveFont(Font.BOLD, myTextFieldTitle.getFont().getSize() - 1f));
    }
    else {
      myTextFieldTitle.setFont(myTextFieldTitle.getFont().deriveFont(Font.BOLD));
    }

    topPanel.add(myTextFieldTitle, BorderLayout.WEST);
    JPanel controls = new JPanel(new BorderLayout());
    controls.setOpaque(false);

    JLabel settings = new JLabel(AllIcons.General.GearPlain);
    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent event, int clickCount) {
        showSettings();
        return true;
      }
    }.installOn(settings);

    settings.setBorder(UIUtil.isUnderWin10LookAndFeel() ? JBUI.Borders.emptyLeft(6) : JBUI.Borders.empty());

    controls.add(settings, BorderLayout.EAST);
    controls.setBorder(UIUtil.isUnderWin10LookAndFeel() ? JBUI.Borders.emptyTop(1) : JBUI.Borders.empty());

    topPanel.add(controls, BorderLayout.EAST);
    panel.add(myPopupField, BorderLayout.CENTER);
    panel.add(topPanel, BorderLayout.NORTH);
    panel.setBorder(JBUI.Borders.empty(3, 5, 4, 5));

    myAdComponent = HintUtil.createAdComponent(AD_MODULE_CONTEXT, JBUI.Borders.empty(1, 5), SwingConstants.LEFT);

    panel.add(myAdComponent, BorderLayout.SOUTH);

    myList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        updateAdText();

        Object selectedValue = myList.getSelectedValue();
        if (selectedValue == null || tryGetSearchingModel(myList) == null) return;

        String lastInput = myTextField.getText();
        myIsItemSelected = true;

        if (isMoreItem(myList.getSelectedIndex()) && myLastInputText != null) {
          myTextField.setText(myLastInputText);
          return;
        }

        if (selectedValue instanceof RunAnythingItem) {
          myTextField.setText(((RunAnythingItem)selectedValue).getText());
        }
        else if (selectedValue instanceof AnAction) {
          myTextField.setText(((AnAction)selectedValue).getTemplatePresentation().getText());
        }
        else if (selectedValue instanceof ChooseRunConfigurationPopup.ItemWrapper) {
          myTextField.setText(((ChooseRunConfigurationPopup.ItemWrapper)selectedValue).getText());
        }
        else {
          myTextField.setText(myLastInputText);
        }

        if (myLastInputText == null) myLastInputText = lastInput;
      }
    });

    DataManager.registerDataProvider(panel, this);
    final ComponentPopupBuilder builder = JBPopupFactory.getInstance().createComponentPopupBuilder(panel, editor);
    myBalloon = builder
      .setCancelOnClickOutside(true)
      .setModalContext(false)
      .setRequestFocus(true)
      .setCancelCallback(() -> !mySkipFocusGain)
      .createPopup();
    myBalloon.getContent().setBorder(JBUI.Borders.empty());
    final Window window = WindowManager.getInstance().suggestParentWindow(project);

    project.getMessageBus().connect(myBalloon).subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {
      @Override
      public void enteredDumbMode() {
      }

      @Override
      public void exitDumbMode() {
        ApplicationManager.getApplication().invokeLater(() -> rebuildList(myPopupField.getText()));
      }
    });

    Component parent = UIUtil.findUltimateParent(window);

    final RelativePoint showPoint;
    if (parent != null) {
      int height = UISettings.getInstance().getShowMainToolbar() ? 135 : 115;
      if (parent instanceof IdeFrameImpl && ((IdeFrameImpl)parent).isInFullScreen()) {
        height -= 20;
      }
      showPoint = new RelativePoint(parent, new Point((parent.getSize().width - panel.getPreferredSize().width) / 2, height));
    }
    else {
      showPoint = JBPopupFactory.getInstance().guessBestPopupLocation(e.getDataContext());
    }
    myList.setFont(UIUtil.getListFont());
    myBalloon.show(showPoint);
    initSearchActions(myBalloon, myPopupField);
    IdeFocusManager focusManager = IdeFocusManager.getInstance(project);
    focusManager.requestFocus(editor, true);
    FeatureUsageTracker.getInstance().triggerFeatureUsed(RunAnythingUtil.RUN_ANYTHING);
  }

  private void setHandleMatchedConfiguration(JBTextField textField) {
    textField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        String pattern;
        try {
          pattern = e.getDocument().getText(0, e.getDocument().getLength());
        }
        catch (BadLocationException e1) {
          LOG.error(e1);
          return;
        }

        String name = null;
        for (RunAnythingProvider provider : RunAnythingProvider.EP_NAME.getExtensions()) {
          if (provider.isMatched(getProject(), pattern, getWorkDirectory(getModule()))) {
            name = provider.getConfigurationFactory().getName();
          }
        }

        if (name != null) {
          textField.putClientProperty(MATCHED_CONFIGURATION_PROPERTY, name);
          setAdText(AD_MODULE_CONTEXT + ", " + AD_DEBUG_TEXT);
        }
        else {
          textField.putClientProperty(MATCHED_CONFIGURATION_PROPERTY, UNKNOWN_CONFIGURATION);
        }
      }
    });
  }

  private void updateAdText() {
    Object value = myList.getSelectedValue();

    String text = AD_MODULE_CONTEXT;
    if (isRunConfigurationItem(value)) {
      text += " , " + AD_DEBUG_TEXT;
    }
    else if (isActionValue(value)) {
      text = AD_ACTION_TEXT;
    }

    setAdText(text);
  }

  private void initActions() {
    myBundlerActions = Stream.of(((DefaultActionGroup)ActionManager.getInstance().getAction("BUNDLER_ACTIONS")).getChildren(myActionEvent))
                             .filter(action -> action instanceof AbstractBundlerAction).toArray(AnAction[]::new);

    //todo move to EP
    Module myModule = getModule();
    if (myModule != null && RailsFacetUtil.hasRailsSupport(myModule)) {
      RakeTask rakeTasks = RakeTaskModuleCache.getInstance(myModule).getRakeTasks();
      if (rakeTasks != null) {
        myRakeActions = Arrays.stream(RakeTaskModuleCache.getInstance(myModule).getRakeActions())
                              .filter(RakeAction.class::isInstance)
                              .map(RakeAction.class::cast)
                              .peek(rakeAction -> rakeAction.updateAction(myDataContext, rakeAction.getTemplatePresentation()))
                              .toArray(AnAction[]::new);
      }

      myGeneratorsActions = GeneratorsActionGroup.collectGeneratorsActions(myModule, false).toArray(AnAction.EMPTY_ARRAY);
    }
  }

  private void showSettings() {
    myPopupField.setText("");
    final RunAnythingSettingsModel model = new RunAnythingSettingsModel();
    model.addElement(new RunAnythingSEOption("Show generators", "run.anything.generators"));
    model.addElement(new RunAnythingSEOption("Show rake tasks", "run.anything.rake.tasks"));
    model.addElement(new RunAnythingSEOption("Show bundler actions", "run.anything.bundler.actions"));
    model.addElement(new RunAnythingSEOption("Show permanent run configurations", "run.anything.permanent.configurations"));
    model.addElement(new RunAnythingSEOption("Show temporary run configurations", "run.anything.temporary.configurations"));
    model.addElement(new RunAnythingSEOption("Show undefined command ", "run.anything.undefined.commands"));

    if (myCalcThread != null && !myCurrentWorker.isProcessed()) {
      myCurrentWorker = myCalcThread.cancel();
    }
    if (myCalcThread != null && !myCalcThread.isCanceled()) {
      myCalcThread.cancel();
    }
    myCurrentWorker.doWhenProcessed(() -> {
      myList.setModel(model);
      updatePopupBounds();
    });
  }

  private static void saveHistory(Project project, String text, Object value) {
    if (project == null || project.isDisposed() || !project.isInitialized()) {
      return;
    }
    HistoryType type = null;
    String fqn = null;
    if (isActionValue(value)) {
      type = HistoryType.ACTION;
      fqn = ActionManager.getInstance().getId((AnAction)value);
    }
    else if (isRunConfigurationItem(value)) {
      type = HistoryType.RUN_CONFIGURATION;
      fqn = ((ChooseRunConfigurationPopup.ItemWrapper)value).getText();
    }

    final PropertiesComponent storage = PropertiesComponent.getInstance(project);
    final String[] values = storage.getValues(RUN_ANYTHING_HISTORY_KEY);
    List<RunAnythingHistoryItem> history = new ArrayList<>();
    if (values != null) {
      for (String s : values) {
        final String[] split = s.split("\t");
        if (split.length != 3 || text.equals(split[0])) {
          continue;
        }
        if (!StringUtil.isEmpty(split[0])) {
          history.add(new RunAnythingHistoryItem(split[0], split[1], split[2]));
        }
      }
    }
    history.add(0, new RunAnythingHistoryItem(text, type == null ? null : type.name(), fqn));

    if (history.size() > MAX_RUN_ANYTHING_HISTORY) {
      history = history.subList(0, MAX_RUN_ANYTHING_HISTORY);
    }
    final String[] newValues = new String[history.size()];
    for (int i = 0; i < newValues.length; i++) {
      newValues[i] = history.get(i).toString();
    }
    storage.setValues(RUN_ANYTHING_HISTORY_KEY, newValues);
  }

  private void initSearchActions(@NotNull JBPopup balloon, @NotNull MySearchTextField searchTextField) {

    final JTextField editor = searchTextField.getTextEditor();
    DumbAwareAction.create(e -> RunAnythingUtil.jumpNextGroup(true, myList))
                   .registerCustomShortcutSet(CustomShortcutSet.fromString("TAB"), editor, balloon);
    DumbAwareAction.create(e -> RunAnythingUtil.jumpNextGroup(false, myList))
                   .registerCustomShortcutSet(CustomShortcutSet.fromString("shift TAB"), editor, balloon);
    AnAction escape = ActionManager.getInstance().getAction("EditorEscape");
    DumbAwareAction.create(e -> {
      if (myBalloon != null && myBalloon.isVisible()) {
        myBalloon.cancel();
      }
      if (myPopup != null && myPopup.isVisible()) {
        myPopup.cancel();
      }
    }).registerCustomShortcutSet(escape == null ? CommonShortcuts.ESCAPE : escape.getShortcutSet(), editor, balloon);

    DumbAwareAction.create(e -> executeCommand())
                   .registerCustomShortcutSet(CustomShortcutSet.fromString("ENTER", "shift ENTER", "alt ENTER", "meta ENTER"), editor,
                                              balloon);

    DumbAwareAction.create(e -> {
      //todo
      RunAnythingSearchListModel model = tryGetSearchingModel(myList);
      if (model == null) return;

      Object selectedValue = myList.getSelectedValue();
      if (!(selectedValue instanceof RunAnythingUndefinedItem)) return;

      RunAnythingCache.getInstance(getProject()).getState().undefinedCommands.remove(((RunAnythingUndefinedItem)selectedValue).getText());

      int shift = -1;
      int index = myList.getSelectedIndex();

      model.remove(index);

      RunAnythingMoreIndex moreIndex = model.moreIndex;
      model.titleIndex.shift(index, shift);
      moreIndex.shift(index, shift);

      if (model.size() > 0) ScrollingUtil.selectItem(myList, index < model.size() ? index : index - 1);

      updatePopupBounds();
    }).registerCustomShortcutSet(CustomShortcutSet.fromString("shift BACK_SPACE"), editor, balloon);

    new DumbAwareAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        final PropertiesComponent storage = PropertiesComponent.getInstance(e.getProject());
        final String[] values = storage.getValues(RUN_ANYTHING_HISTORY_KEY);
        if (values != null) {
          if (values.length > myHistoryIndex) {
            final List<String> data = StringUtil.split(values[myHistoryIndex], "\t");
            myHistoryItem = new RunAnythingHistoryItem(data.get(0), data.get(1), data.get(2));
            myHistoryIndex++;
            editor.setText(myHistoryItem.pattern);
            editor.setCaretPosition(myHistoryItem.pattern.length());
            editor.moveCaretPosition(0);
          }
        }
      }

      @Override
      public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(editor.getCaretPosition() == 0);
      }
    }.registerCustomShortcutSet(CustomShortcutSet.fromString("LEFT"), editor, balloon);
  }


  public void setAdText(@NotNull final String s) {
    myAdComponent.setText(s);
  }

  @NotNull
  public static Executor getExecutor() {
    final Executor runExecutor = DefaultRunExecutor.getRunExecutorInstance();
    final Executor debugExecutor = ExecutorRegistry.getInstance().getExecutorById(ToolWindowId.DEBUG);

    return !ourShiftIsPressed.get() ? runExecutor : debugExecutor;
  }

  private class MyListRenderer extends ColoredListCellRenderer {
    private RunAnythingMyAccessibleComponent myMainPanel = new RunAnythingMyAccessibleComponent(new BorderLayout());
    private JLabel myTitle = new JLabel();

    @Override
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      Component cmp = null;
      String pattern = "*" + myPopupField.getText();
      if (isMoreItem(index)) {
        cmp = RunAnythingMore.get(isSelected);
      }

      if (cmp == null) {
        if (isActionValue(value)) {
          cmp = RunAnythingUtil.getActionCellRendererComponent(((AnAction)value), isSelected);
        }
        else if (isRunConfigurationItem(value)) {
          cmp = RunAnythingUtil.getRunConfigurationCellRendererComponent(((ChooseRunConfigurationPopup.ItemWrapper)value), isSelected);
        }
        else if (isUndefined(value)) {
          cmp = RunAnythingUtil.getUndefinedCommandCellRendererComponent(((RunAnythingUndefinedItem)value), isSelected);
        }
        else {
          cmp = super.getListCellRendererComponent(list, value, index, isSelected, isSelected);
          final JPanel p = new JPanel(new BorderLayout());
          p.setBackground(UIUtil.getListBackground(isSelected));
          p.add(cmp, BorderLayout.CENTER);
          cmp = p;
        }

        if (isSetting(value)) {
          final JPanel panel = new JPanel(new BorderLayout());
          panel.setBackground(UIUtil.getListBackground(isSelected));
          panel.add(cmp, BorderLayout.CENTER);
          final Component rightComponent;
          final OnOffButton button = new OnOffButton();
          button.setSelected(((BooleanOptionDescription)value).isOptionEnabled());
          rightComponent = button;
          panel.add(rightComponent, BorderLayout.EAST);

          JLabel settingLabel = new JLabel(RunAnythingUtil.getSettingText((OptionDescription)value));
          settingLabel.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0));
          panel.add(settingLabel, BorderLayout.WEST);
          panel.add(rightComponent, BorderLayout.EAST);
          cmp = panel;
        }
      }

      Color bg = cmp.getBackground();
      if (bg == null) {
        cmp.setBackground(UIUtil.getListBackground(isSelected));
        bg = cmp.getBackground();
      }
      myMainPanel.removeAll();
      RunAnythingSearchListModel model = tryGetSearchingModel(RunAnythingAction.this.myList);
      if (model != null) {
        String title = model.titleIndex.getTitle(index);
        if (title != null) {
          myTitle.setText(title);
          myMainPanel.add(RunAnythingUtil.createTitle(" " + title), BorderLayout.NORTH);
        }
      }
      JPanel wrapped = new JPanel(new BorderLayout());
      wrapped.setBackground(bg);
      wrapped.setBorder(RENDERER_BORDER);
      wrapped.add(cmp, BorderLayout.CENTER);
      myMainPanel.add(wrapped, BorderLayout.CENTER);
      if (cmp instanceof Accessible) {
        myMainPanel.setAccessible((Accessible)cmp);
      }
      final int width = myMainPanel.getPreferredSize().width;
      if (width > myPopupActualWidth) {
        myPopupActualWidth = width;
        //schedulePopupUpdate();
      }
      return myMainPanel;
    }

    @Override
    protected void customizeCellRenderer(@NotNull JList list, final Object value, int index, final boolean selected, boolean hasFocus) {
    }

    public void recalculateWidth() {
      RunAnythingSearchListModel model = tryGetSearchingModel(RunAnythingAction.this.myList);
      if (model == null) return;

      myTitle.setIcon(EmptyIcon.ICON_16);
      myTitle.setFont(RunAnythingUtil.getTitleFont());
      int index = 0;
      while (index < model.getSize()) {
        String title = model.titleIndex.getTitle(index);
        if (title != null) {
          myTitle.setText(title);
        }
        index++;
      }

      myTitle.setForeground(Gray._122);
      myTitle.setAlignmentY(BOTTOM_ALIGNMENT);
    }
  }

  private static boolean isActionValue(Object o) {
    return o instanceof AnAction;
  }

  //todo remain bool options
  private static boolean isSetting(Object o) {
    return o instanceof BooleanOptionDescription;
  }

  private static boolean isRunConfigurationItem(Object o) {
    return o instanceof ChooseRunConfigurationPopup.ItemWrapper;
  }

  private static boolean isUndefined(Object o) {
    return o instanceof RunAnythingUndefinedItem;
  }

  enum WidgetID {PERMANENT, RAKE, BUNDLER, TEMPORARY, GENERATORS, UNDEFINED}

  @SuppressWarnings({"SSBasedInspection", "unchecked"})
  private class CalcThread implements Runnable {
    @NotNull private final Project myProject;
    @NotNull private final String myPattern;
    private final ProgressIndicator myProgressIndicator = new ProgressIndicatorBase();
    private final ActionCallback myDone = new ActionCallback();
    @NotNull private final RunAnythingSearchListModel myListModel;
    @Nullable private final Module myModule;

    public CalcThread(@NotNull Project project, @NotNull String pattern, boolean reuseModel) {
      myProject = project;
      myModule = getModule();
      myPattern = pattern;
      RunAnythingSearchListModel model = tryGetSearchingModel(RunAnythingAction.this.myList);
      myListModel = reuseModel ? model != null ? model : new RunAnythingSearchListModel() : new RunAnythingSearchListModel();
    }

    @Override
    public void run() {
      try {
        check();

        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(() -> {
          // this line must be called on EDT to avoid context switch at clear().append("text") Don't touch. Ask [kb]
          myList.getEmptyText().setText("Searching...");

          if (tryGetSearchingModel(myList) != null) {
            //noinspection unchecked
            myAlarm.cancelAllRequests();
            myAlarm.addRequest(() -> {
              if (!myDone.isRejected()) {
                myList.setModel(myListModel);
                updatePopup();
              }
            }, 50);
          }
          else {
            myList.setModel(myListModel);
          }
        });

        if (myPattern.trim().length() == 0) {
          buildModelFromRecentFiles();
          buildTemporaryConfigurations("");
          //updatePopup();
          return;
        }

        check();

        buildRecentUndefined();

        runReadAction(() -> buildTemporaryConfigurations(myPattern));
        runReadAction(() -> buildPermanentConfigurations(myPattern));
        check();
        buildGenerators(myPattern);
        updatePopup();
        check();

        buildRakeActions(myPattern);
        buildBundlerActions(myPattern);

        updatePopup();
      }
      catch (ProcessCanceledException ignore) {
        myDone.setRejected();
      }
      catch (Exception e) {
        LOG.error(e);
        myDone.setRejected();
      }
      finally {
        if (!isCanceled()) {
          //noinspection SSBasedInspection
          SwingUtilities.invokeLater(() -> myList.getEmptyText().setText(RBundle.message("run.command.empty.list.title")));
          updatePopup();
        }
        if (!myDone.isProcessed()) {
          myDone.setDone();
        }
      }
    }

    private void runReadAction(@NotNull Runnable action) {
      if (!DumbService.getInstance(myProject).isDumb()) {
        ApplicationManager.getApplication().runReadAction(action);
        updatePopup();
      }
    }

    protected void check() {
      myProgressIndicator.checkCanceled();
      if (myDone.isRejected()) throw new ProcessCanceledException();
      if (myBalloon == null || myBalloon.isDisposed()) throw new ProcessCanceledException();
      assert myCalcThread == this : "There are two CalcThreads running before one of them was cancelled";
    }

    private synchronized void buildRakeActions(String pattern) {
      SearchResult actions = getRakeTasks(pattern, MAX_RAKE);

      check();
      addActionsToModel(
        actions,
        () -> myListModel.titleIndex.rakeTasks = myListModel.size(),
        () -> myListModel.moreIndex.rakeTasks = actions.size() >= MAX_RAKE ? myListModel.size() - 1 : -1);
    }

    private SearchResult getActions(final String pattern, final int max, AnAction[] actions, String prefix) {
      final SearchResult result = new SearchResult();
      final MinusculeMatcher matcher = NameUtil.buildMatcher("*" + pattern).build();

      for (AnAction action : actions) {
        if (!myListModel.contains(action) && matcher.matches(prefix + " " + RunAnythingUtil.getPresentationText(action))) {
          if (result.size() == max) {
            result.needMore = true;
            break;
          }
          result.add(action);
        }
        check();
      }

      return result;
    }


    private synchronized void buildBundlerActions(String pattern) {
      SearchResult bundlerActions = getBundlerActions(pattern, MAX_BUNDLER_ACTIONS);

      check();
      addActionsToModel(
        bundlerActions,
        () -> myListModel.titleIndex.bundler = myListModel.size(),
        () -> myListModel.moreIndex.bundlerActions = bundlerActions.size() >= MAX_BUNDLER_ACTIONS ? myListModel.size() - 1 : -1);
    }

    private void addActionsToModel(final SearchResult actions, Runnable updateTitleIndex, Runnable updateModeIndex) {
      SwingUtilities.invokeLater(() -> {
        if (isCanceled()) return;
        if (actions.size() > 0) {
          updateTitleIndex.run();
          for (Object action : actions) {
            myListModel.addElement(action);
          }
        }
        updateModeIndex.run();
      });
    }

    private synchronized void buildGenerators(final String pattern) {
      SearchResult generators = getGenerators(pattern, MAX_GENERATORS);

      check();
      addActionsToModel(
        generators,
        () -> myListModel.titleIndex.generators = myListModel.size(),
        () -> myListModel.moreIndex.generators = generators.size() >= MAX_GENERATORS ? myListModel.size() - 1 : -1);
    }

    private synchronized void buildPermanentConfigurations(@NotNull String pattern) {
      SearchResult permanentRunConfigurations = getPermanentConfigurations(pattern, MAX_RUN_CONFIGURATION);

      if (permanentRunConfigurations.size() > 0) {
        SwingUtilities.invokeLater(() -> {
          if (isCanceled()) return;
          myListModel.titleIndex.permanentRunConfigurations = myListModel.size();
          for (Object runConfiguration : permanentRunConfigurations) {
            myListModel.addElement(runConfiguration);
          }
          myListModel.moreIndex.permanentRunConfigurations = permanentRunConfigurations.needMore ? myListModel.getSize() - 1 : -1;
        });
      }
    }

    private synchronized void buildTemporaryConfigurations(@NotNull String pattern) {
      SearchResult runConfigurations = getTemporaryRunConfigurations(pattern, MAX_RUN_CONFIGURATION);

      if (runConfigurations.size() > 0) {
        SwingUtilities.invokeLater(() -> {
          if (isCanceled()) return;
          myListModel.titleIndex.temporaryRunConfigurations = myListModel.size();
          for (Object runConfiguration : runConfigurations) {
            myListModel.addElement(runConfiguration);
          }
          myListModel.moreIndex.temporaryRunConfigurations = runConfigurations.needMore ? myListModel.getSize() - 1 : -1;
        });
      }
    }

    private SearchResult getConfigurations(String pattern, int max, Predicate<ChooseRunConfigurationPopup.ItemWrapper> filter) {
      SearchResult configurations = new SearchResult();

      final MinusculeMatcher matcher = NameUtil.buildMatcher(pattern).build();
      final ChooseRunConfigurationPopup.ItemWrapper[] wrappers =
        ChooseRunConfigurationPopup.createSettingsList(myProject, new ExecutorProvider() {
          @Override
          public Executor getExecutor() {
            return ExecutorRegistry.getInstance().getExecutorById(ToolWindowId.RUN);
          }
        }, false);
      check();

      for (ChooseRunConfigurationPopup.ItemWrapper wrapper : wrappers) {
        if (!filter.test(wrapper)) continue;

        if (matcher.matches(wrapper.getText()) && !myListModel.contains(wrapper)) {
          if (configurations.size() == max) {
            configurations.needMore = true;
            break;
          }
          configurations.add(wrapper);
        }
        check();
      }

      return configurations;
    }

    private boolean isTemporary(ChooseRunConfigurationPopup.ItemWrapper wrapper) {
      Object value = wrapper.getValue();
      return value instanceof RunnerAndConfigurationSettings && ((RunnerAndConfigurationSettings)value).isTemporary();
    }

    private SearchResult getTemporaryRunConfigurations(String pattern, final int max) {
      if (!Registry.is("run.anything.temporary.configurations")) return new SearchResult();

      return getConfigurations(pattern, max, it -> isTemporary(it));
    }

    private SearchResult getPermanentConfigurations(String pattern, final int max) {
      if (!Registry.is("run.anything.permanent.configurations")) return new SearchResult();

      return getConfigurations(pattern, max, it -> !isTemporary(it));
    }

    private synchronized void buildRecentUndefined() {
      SearchResult recentUndefined = getUndefinedCommands(myPattern, MAX_UNDEFINED_FILES);

      if (recentUndefined.size() > 0) {
        SwingUtilities.invokeLater(() -> {
          if (isCanceled()) return;
          myListModel.titleIndex.recentUndefined = myListModel.size();
          for (Object file : recentUndefined) {
            myListModel.addElement(file);
          }
          myListModel.moreIndex.undefined = recentUndefined.needMore ? myListModel.getSize() - 1 : -1;
        });
      }
    }

    private boolean isCanceled() {
      return myProgressIndicator.isCanceled() || myDone.isRejected();
    }

    private void buildModelFromRecentFiles() {
      buildRecentUndefined();
    }

    @SuppressWarnings("SSBasedInspection")
    private void updatePopup() {
      check();
      SwingUtilities.invokeLater(new Runnable() {
        @Override
        public void run() {
          myListModel.update();
          myList.revalidate();
          myList.repaint();

          myRenderer.recalculateWidth();
          if (myBalloon == null || myBalloon.isDisposed()) {
            return;
          }
          if (myPopup == null || !myPopup.isVisible()) {
            installActions();
            JBScrollPane content = new JBScrollPane(myList) {
              {
                if (UIUtil.isUnderDarcula()) {
                  setBorder(null);
                }
              }

              @Override
              public Dimension getPreferredSize() {
                Dimension size = super.getPreferredSize();
                Dimension listSize = myList.getPreferredSize();
                if (size.height > listSize.height || myList.getModel().getSize() == 0) {
                  size.height = Math.max(JBUI.scale(30), listSize.height);
                }

                if (myBalloon != null && size.width < myBalloon.getSize().width) {
                  size.width = myBalloon.getSize().width;
                }

                return size;
              }
            };
            content.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            content.setMinimumSize(new Dimension(myBalloon.getSize().width, 30));
            final ComponentPopupBuilder builder = JBPopupFactory.getInstance()
                                                                .createComponentPopupBuilder(content, null);
            myPopup = builder
              .setRequestFocus(false)
              .setCancelKeyEnabled(false)
              .setResizable(true)
              .setCancelCallback(() -> {
                final JBPopup balloon = myBalloon;
                final AWTEvent event = IdeEventQueue.getInstance().getTrueCurrentEvent();
                if (event instanceof MouseEvent) {
                  final Component comp = ((MouseEvent)event).getComponent();
                  if (balloon != null && UIUtil.getWindow(comp) == UIUtil.getWindow(balloon.getContent())) {
                    return false;
                  }
                }
                final boolean canClose =
                  balloon == null || balloon.isDisposed() || (!getField().getTextEditor().hasFocus() && !mySkipFocusGain);
                if (canClose) {
                  PropertiesComponent.getInstance()
                                     .setValue("run.anything.max.popup.width", Math.max(content.getWidth(), JBUI.scale(600)),
                                               JBUI.scale(600));
                }
                return canClose;
              })
              .setShowShadow(false)
              .setShowBorder(false)
              .createPopup();
            myProject.putUserData(RUN_ANYTHING_POPUP, myPopup);
            myPopup.getContent().setBorder(null);
            Disposer.register(myPopup, new Disposable() {
              @Override
              public void dispose() {
                myProject.putUserData(RUN_ANYTHING_POPUP, null);
                ApplicationManager.getApplication().executeOnPooledThread(() -> {
                  resetFields();
                  //noinspection SSBasedInspection
                  SwingUtilities.invokeLater(() -> ActionToolbarImpl.updateAllToolbarsImmediately());
                  if (myActionEvent != null && myActionEvent.getInputEvent() instanceof MouseEvent) {
                    final Component component = myActionEvent.getInputEvent().getComponent();
                    if (component != null) {
                      final JLabel label = UIUtil.getParentOfType(JLabel.class, component);
                      if (label != null) {
                        SwingUtilities.invokeLater(() -> label.setIcon(RubyIcons.RunAnything.Run_anything));
                      }
                    }
                  }
                  myActionEvent = null;
                  myLastInputText = null;
                });
              }
            });
            updatePopupBounds();
            myPopup.show(new RelativePoint(getField().getParent(), new Point(0, getField().getParent().getHeight())));

            ActionManager.getInstance().addAnActionListener(new AnActionListener.Adapter() {
              @Override
              public void beforeActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {
                if (action instanceof TextComponentEditorAction) {
                  return;
                }
                if (myPopup != null) {
                  myPopup.cancel();
                }
              }
            }, myPopup);
          }
          else {
            myList.revalidate();
            myList.repaint();
          }
          //ScrollingUtil.ensureSelectionExists(myList);
          if (myList.getModel().getSize() > 0) {
            updatePopupBounds();
          }
        }
      });
    }

    public ActionCallback cancel() {
      myProgressIndicator.cancel();
      //myDone.setRejected();
      return myDone;
    }

    public ActionCallback insert(final int index, final WidgetID id) {
      ApplicationManager.getApplication().executeOnPooledThread(() -> runReadAction(() -> {
        try {
          SearchResult result = new SearchResult();
          switch (id) {
            case PERMANENT:
              result = getPermanentConfigurations(myPattern, DEFAULT_MORE_STEP_COUNT);
              break;
            case RAKE:
              result = getRakeTasks(myPattern, DEFAULT_MORE_STEP_COUNT);
              break;
            case BUNDLER:
              result = getBundlerActions(myPattern, DEFAULT_MORE_STEP_COUNT);
              break;
            case TEMPORARY:
              result = getTemporaryRunConfigurations(myPattern, DEFAULT_MORE_STEP_COUNT);
              break;
            case GENERATORS:
              result = getGenerators(myPattern, DEFAULT_MORE_STEP_COUNT);
              break;
            case UNDEFINED:
              result = getUndefinedCommands(myPattern, DEFAULT_MORE_STEP_COUNT);
              break;
          }


          check();
          SearchResult finalResult = result;
          SwingUtilities.invokeLater(() -> {
            try {
              int shift = 0;
              int i = index + 1;
              for (Object o : finalResult) {
                //noinspection unchecked
                myListModel.insertElementAt(o, i);
                shift++;
                i++;
              }
              RunAnythingMoreIndex moreIndex = myListModel.moreIndex;
              myListModel.titleIndex.shift(index, shift);
              moreIndex.shift(index, shift);

              if (!finalResult.needMore) {
                switch (id) {
                  case PERMANENT:
                    moreIndex.permanentRunConfigurations = -1;
                    break;
                  case RAKE:
                    moreIndex.rakeTasks = -1;
                    break;
                  case BUNDLER:
                    moreIndex.bundlerActions = -1;
                    break;
                  case TEMPORARY:
                    moreIndex.temporaryRunConfigurations = -1;
                    break;
                  case GENERATORS:
                    moreIndex.generators = -1;
                    break;
                  case UNDEFINED:
                    moreIndex.undefined = -1;
                    break;
                }
              }

              clearSelection();
              ScrollingUtil.selectItem(myList, index);
              myDone.setDone();
            }
            catch (Exception e) {
              myDone.setRejected();
            }
          });
        }
        catch (Exception e) {
          myDone.setRejected();
        }
      }));
      return myDone;
    }

    @NotNull
    private SearchResult getUndefinedCommands(@NotNull String pattern, int max) {
      SearchResult result = new SearchResult();
      MinusculeMatcher matcher = NameUtil.buildMatcher("*" + pattern).build();

      if (!Registry.is("run.anything.undefined.commands")) {
        return result;
      }

      check();
      for (String command : ContainerUtil.iterateBackward(RunAnythingCache.getInstance(myProject).getState().undefinedCommands)) {
        RunAnythingUndefinedItem undefinedItem = new RunAnythingUndefinedItem(myProject, myModule, command);

        if (matcher.matches(command) && !myListModel.contains(undefinedItem)) {
          if (result.size() == max) {
            result.needMore = true;
            break;
          }
          result.add(undefinedItem);
        }
        check();
      }

      return result;
    }


    private SearchResult getRakeTasks(String pattern, int count) {
      if (!Registry.is("run.anything.rake.tasks")) return new SearchResult();

      return getActions(pattern, count, myRakeActions, "rake");
    }

    private SearchResult getGenerators(String pattern, int count) {
      if (!Registry.is("run.anything.generators")) return new SearchResult();

      return getActions(pattern, count, myGeneratorsActions, "generator");
    }

    private SearchResult getBundlerActions(String pattern, int count) {
      if (!Registry.is("run.anything.bundler.actions")) return new SearchResult();

      return getActions(pattern, count, myBundlerActions, "bundle");
    }

    public ActionCallback start() {
      ApplicationManager.getApplication().executeOnPooledThread(this);
      return myDone;
    }
  }

  private void installActions() {
    RunAnythingScrollingUtil.installActions(myList, getField().getTextEditor(), () -> {
      myIsItemSelected = true;
      getField().getTextEditor().setText(myLastInputText);
      clearSelection();
    });

    ScrollingUtil.installActions(myList, getField().getTextEditor());
  }

  protected void resetFields() {
    if (myBalloon != null) {
      final JBPopup balloonToBeCanceled = myBalloon;
      //noinspection SSBasedInspection
      SwingUtilities.invokeLater(() -> balloonToBeCanceled.cancel());
      myBalloon = null;
    }
    myCurrentWorker.doWhenProcessed(() -> {
      final Object lock = myCalcThread;
      if (lock != null) {
        synchronized (lock) {
          myFocusComponent = null;
          myContextComponent = null;
          myFocusOwner = null;
          myPopup = null;
          myHistoryIndex = 0;
          myPopupActualWidth = 0;
          myCurrentWorker = ActionCallback.DONE;
          myCalcThread = null;
          myEditor = null;
          myFileEditor = null;
        }
      }
    });
    mySkipFocusGain = false;
  }

  private void updatePopupBounds() {
    if (myPopup == null || !myPopup.isVisible()) {
      return;
    }
    final Container parent = getField().getParent();
    final Dimension size = myList.getParent().getParent().getPreferredSize();
    size.width = myPopupActualWidth - 2;
    if (size.width + 2 < parent.getWidth()) {
      size.width = parent.getWidth();
    }
    if (myList.getItemsCount() == 0) {
      size.height = JBUI.scale(30);
    }
    Dimension sz = new Dimension(size.width, myList.getPreferredSize().height);
    if (!SystemInfo.isMac) {
      if ((sz.width > RunAnythingUtil.getPopupMaxWidth() || sz.height > RunAnythingUtil.getPopupMaxWidth())) {
        final JBScrollPane pane = new JBScrollPane();
        final int extraWidth = pane.getVerticalScrollBar().getWidth() + 1;
        final int extraHeight = pane.getHorizontalScrollBar().getHeight() + 1;
        sz = new Dimension(Math.min(RunAnythingUtil.getPopupMaxWidth(), Math.max(getField().getWidth(), sz.width + extraWidth)),
                           Math.min(RunAnythingUtil.getPopupMaxWidth(), sz.height + extraHeight));
        sz.width += 20;
      }
      else {
        sz.width += 2;
      }
    }
    sz.height += 2;
    sz.width = Math.max(sz.width, myPopup.getSize().width);
    myPopup.setSize(sz);
    if (myActionEvent != null && myActionEvent.getInputEvent() == null) {
      final Point p = parent.getLocationOnScreen();
      p.y += parent.getHeight();
      if (parent.getWidth() < sz.width) {
        p.x -= sz.width - parent.getWidth();
      }
      myPopup.setLocation(p);
    }
    else {
      try {
        RunAnythingUtil.adjustPopup(myBalloon, myPopup);
      }
      catch (Exception ignore) {
      }
    }
  }

  static class SearchResult extends ArrayList<Object> {
    boolean needMore;
  }

  private enum HistoryType {PSI, FILE, SETTING, ACTION, RUN_CONFIGURATION}

  static class MySearchTextField extends SearchTextField implements DataProvider, Disposable {
    public MySearchTextField() {
      super(false, "RunAnythingHistory");
      JTextField editor = getTextEditor();
      editor.setOpaque(false);
      editor.putClientProperty("JTextField.Search.noBorderRing", Boolean.TRUE);
      if (UIUtil.isUnderDarcula()) {
        editor.setBackground(Gray._45);
        editor.setForeground(Gray._240);
      }
    }

    @Override
    protected boolean customSetupUIAndTextField(@NotNull TextFieldWithProcessing textField, @NotNull Consumer<TextUI> uiConsumer) {
      if (UIUtil.isUnderDarcula()) {
        uiConsumer.consume(new MyDarcula(ourIconsMap));
        textField.setBorder(new DarculaTextBorder());
      }
      else {
        if (SystemInfo.isMac) {
          uiConsumer.consume(new MyMacUI(ourIconsMap));
          textField.setBorder(new MacIntelliJTextBorder());
        }
        else {
          uiConsumer.consume(new MyWinUI(ourIconsMap));
          textField.setBorder(new WinIntelliJTextBorder());
        }
      }
      return true;
    }

    @Override
    protected boolean isSearchControlUISupported() {
      return true;
    }

    @Override
    protected boolean hasIconsOutsideOfTextField() {
      return false;
    }

    @Override
    protected void showPopup() {
    }

    @Nullable
    @Override
    public Object getData(@NonNls String dataId) {
      if (PlatformDataKeys.PREDEFINED_TEXT.is(dataId)) {
        return getTextEditor().getText();
      }
      return null;
    }

    @Override
    public void dispose() {
    }
  }

  private static class RunAnythingSettingsModel extends DefaultListModel {
  }
}