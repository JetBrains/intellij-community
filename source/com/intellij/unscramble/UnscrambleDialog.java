/**
 * @author cdr
 */
package com.intellij.unscramble;

import com.intellij.execution.ExecutionManager;
import com.intellij.execution.Executor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.actions.CloseAction;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class UnscrambleDialog extends DialogWrapper{
  @NonNls private static final String PROPERTY_LOG_FILE_HISTORY_URLS = "UNSCRAMBLE_LOG_FILE_URL";
  @NonNls private static final String PROPERTY_LOG_FILE_LAST_URL = "UNSCRAMBLE_LOG_FILE_LAST_URL";
  @NonNls private static final String PROPERTY_UNSCRAMBLER_NAME_USED = "UNSCRAMBLER_NAME_USED";

  private static final Icon PAUSE_ICON = IconLoader.getIcon("/debugger/threadStates/paused.png");
  private static final Icon LOCKED_ICON = IconLoader.getIcon("/debugger/threadStates/locked.png");
  private static final Icon RUNNING_ICON = IconLoader.getIcon("/debugger/threadStates/running.png");
  private static final Icon SOCKET_ICON = IconLoader.getIcon("/debugger/threadStates/socket.png");
  private static final Icon IDLE_ICON = IconLoader.getIcon("/debugger/threadStates/idle.png");
  private static final Icon EDT_BUSY_ICON = IconLoader.getIcon("/debugger/threadStates/edtBusy.png");
  private static final Icon IO_ICON = IconLoader.getIcon("/debugger/threadStates/io.png");

  private final Project myProject;
  private JPanel myEditorPanel;
  private JPanel myLogFileChooserPanel;
  private JComboBox myUnscrambleChooser;
  private JPanel myPanel;
  private Editor myEditor;
  private TextFieldWithHistory myLogFile;
  private JCheckBox myUseUnscrambler;
  private JPanel myUnscramblePanel;

  public UnscrambleDialog(Project project) {
    super(false);
    myProject = project;

    populateRegisteredUnscramblerList();
    myUnscrambleChooser.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        UnscrambleSupport unscrambleSupport = getSelectedUnscrambler();
        GuiUtils.enableChildren(myLogFileChooserPanel, unscrambleSupport != null);
      }
    });
    myUseUnscrambler.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        useUnscramblerChanged();
      }
    });
    createLogFileChooser();
    createEditor();
    reset();

    setTitle(IdeBundle.message("unscramble.dialog.title"));
    init();
  }

  private void useUnscramblerChanged() {
    boolean selected = myUseUnscrambler.isSelected();
    GuiUtils.enableChildren(myUnscramblePanel, selected, myUseUnscrambler);
  }

  private void reset() {
    final List<String> savedUrls = getSavedLogFileUrls();
    myLogFile.setHistorySize(10);
    myLogFile.setHistory(savedUrls);

    String lastUrl = getLastUsedLogUrl();
    if (lastUrl == null && !savedUrls.isEmpty()) {
      lastUrl = savedUrls.get(savedUrls.size() - 1);
    }
    if (lastUrl != null) {
      myLogFile.setText(lastUrl);
      myLogFile.setSelectedItem(lastUrl);
    }
    final UnscrambleSupport selectedUnscrambler = getSavedUnscrambler();

    final int count = myUnscrambleChooser.getItemCount();
    int index = 0;
    if (selectedUnscrambler != null) {
      for (int i = 0; i < count; i++) {
        final UnscrambleSupport unscrambleSupport = (UnscrambleSupport)myUnscrambleChooser.getItemAt(i);
        if (unscrambleSupport != null && Comparing.strEqual(unscrambleSupport.getPresentableName(), selectedUnscrambler.getPresentableName())) {
          index = i;
          break;
        }
      }
    }
    if (count > 0) {
      myUseUnscrambler.setEnabled(true);
      myUnscrambleChooser.setSelectedIndex(index);
      myUseUnscrambler.setSelected(selectedUnscrambler != null);
    }
    else {
      myUseUnscrambler.setEnabled(false);
    }

    useUnscramblerChanged();
    pasteTextFromClipboard();
  }

  public static String getLastUsedLogUrl() {
    return PropertiesComponent.getInstance().getValue(PROPERTY_LOG_FILE_LAST_URL);
  }

  @Nullable
  public static UnscrambleSupport getSavedUnscrambler() {
    final List<UnscrambleSupport> registeredUnscramblers = getRegisteredUnscramblers();
    final String savedUnscramblerName = PropertiesComponent.getInstance().getValue(PROPERTY_UNSCRAMBLER_NAME_USED);
    UnscrambleSupport selectedUnscrambler = null;
    for (final UnscrambleSupport unscrambleSupport : registeredUnscramblers) {
      if (Comparing.strEqual(unscrambleSupport.getPresentableName(), savedUnscramblerName)) {
        selectedUnscrambler = unscrambleSupport;
      }
    }
    return selectedUnscrambler;
  }

  public static List<String> getSavedLogFileUrls() {
    final List<String> res = new ArrayList<String>();
    final String savedUrl = PropertiesComponent.getInstance().getValue(PROPERTY_LOG_FILE_HISTORY_URLS);
    final String[] strings = savedUrl == null ? ArrayUtil.EMPTY_STRING_ARRAY : savedUrl.split(":::");
    for (int i = 0; i != strings.length; ++i) {
      res.add(strings[i]);
    }
    return res;
  }

  private void pasteTextFromClipboard() {
    String text = getTextInClipboard();
    if (text != null) {
      setText(text);
    }
  }

  public final void setText(@NotNull final String text) {
    Runnable runnable = new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            final Document document = myEditor.getDocument();
            document.replaceString(0, document.getTextLength(), StringUtil.convertLineSeparators(text));
          }
        });
      }
    };
    CommandProcessor.getInstance().executeCommand(myProject, runnable, "", this);
  }

  public static String getTextInClipboard() {
    String text = null;
    try {
      Transferable contents = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(UnscrambleDialog.class);
      if (contents != null) {
        text = (String)contents.getTransferData(DataFlavor.stringFlavor);
      }
    }
    catch (Exception ex) {
    }
    return text;
  }

  @Nullable
  private UnscrambleSupport getSelectedUnscrambler() {
    if (!myUseUnscrambler.isSelected()) return null;
    return (UnscrambleSupport)myUnscrambleChooser.getSelectedItem();
  }

  private void createEditor() {
    EditorFactory editorFactory = EditorFactory.getInstance();
    Document document = editorFactory.createDocument("");
    myEditor = editorFactory.createEditor(document);
    EditorSettings settings = myEditor.getSettings();
    settings.setFoldingOutlineShown(false);
    settings.setLineMarkerAreaShown(false);
    settings.setLineNumbersShown(false);
    settings.setRightMarginShown(false);

    EditorPanel editorPanel = new EditorPanel(myEditor);
    editorPanel.setPreferredSize(new Dimension(600, 400));

    myEditorPanel.setLayout(new BorderLayout());
    myEditorPanel.add(editorPanel, BorderLayout.CENTER);
  }

  protected Action[] createActions(){
    return new Action[]{new NormalizeTextAction(), getOKAction(), getCancelAction(), getHelpAction()};
  }

  private void createLogFileChooser() {
    myLogFile = new TextFieldWithHistory();
    JPanel panel = GuiUtils.constructFieldWithBrowseButton(myLogFile, new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor();
        VirtualFile[] files = FileChooser.chooseFiles(myLogFile, descriptor);
        if (files.length != 0) {
          myLogFile.setText(FileUtil.toSystemDependentName(files[files.length-1].getPath()));
        }
      }
    });
    myLogFileChooserPanel.setLayout(new BorderLayout());
    myLogFileChooserPanel.add(panel, BorderLayout.CENTER);
  }

  private void populateRegisteredUnscramblerList() {
    List<UnscrambleSupport> unscrambleComponents = getRegisteredUnscramblers();

    //myUnscrambleChooser.addItem(null);
    for (final UnscrambleSupport unscrambleSupport : unscrambleComponents) {
      myUnscrambleChooser.addItem(unscrambleSupport);
    }
    myUnscrambleChooser.setRenderer(new DefaultListCellRenderer() {
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        UnscrambleSupport unscrambleSupport = (UnscrambleSupport)value;
        setText(unscrambleSupport == null ? IdeBundle.message("unscramble.no.unscrambler.item") : unscrambleSupport.getPresentableName());
        return this;
      }
    });
  }

  private static List<UnscrambleSupport> getRegisteredUnscramblers() {
    final UnscrambleSupport[] components = Extensions.getExtensions(UnscrambleSupport.EP_NAME);
    return Arrays.asList(components);
  }

  protected JComponent createCenterPanel() {
    return myPanel;
  }

  public void dispose() {
    EditorFactory editorFactory = EditorFactory.getInstance();
    editorFactory.releaseEditor(myEditor);

    if (isOK()){
      final List list = myLogFile.getHistory();
      String res = null;
      for (Object aList : list) {
        final String s = (String)aList;
        if (res == null) {
          res = s;
        }
        else {
          res = res + ":::" + s;
        }
      }
      PropertiesComponent.getInstance().setValue(PROPERTY_LOG_FILE_HISTORY_URLS, res);
      UnscrambleSupport selectedUnscrambler = getSelectedUnscrambler();
      PropertiesComponent.getInstance().setValue(PROPERTY_UNSCRAMBLER_NAME_USED, selectedUnscrambler == null ? null : selectedUnscrambler.getPresentableName());

      PropertiesComponent.getInstance().setValue(PROPERTY_LOG_FILE_LAST_URL, myLogFile.getText());
    }
    super.dispose();
  }

  private final class NormalizeTextAction extends AbstractAction {
    public NormalizeTextAction(){
      putValue(Action.NAME, IdeBundle.message("unscramble.normalize.button"));
      putValue(DEFAULT_ACTION, Boolean.FALSE);
    }

    public void actionPerformed(ActionEvent e){
      String text = myEditor.getDocument().getText();
      text = normalizeText(text);

      final String newText = text;
      CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              myEditor.getDocument().replaceString(0, myEditor.getDocument().getTextLength(), newText);
            }
          });
        }
      }, "", null);
    }

  }

  static String normalizeText(@NonNls String text) {
    StringBuilder builder = new StringBuilder(text.length());
    String[] lines = text.split("\n");
    boolean first = true;
    for (String line : lines) {
      if (!first && mustHaveNewLineBefore(line)) {
        builder.append("\n");
        if (line.startsWith("\"")) builder.append("\n"); // Additional linebreak for thread names
      }
      first = false;
      int i = builder.lastIndexOf("\n");
      CharSequence lastLine = i == -1 ? builder : builder.subSequence(i + 1, builder.length());
      if (lastLine.toString().matches("\\s*at") && !line.matches("\\s+.*")) builder.append(" "); // separate 'at' from file name
      builder.append(line);
    }
    return builder.toString();
  }

  private static boolean mustHaveNewLineBefore(String line) {
    final int nonws = CharArrayUtil.shiftForward(line, 0, " \t");
    if (nonws < line.length()) {
      line = line.substring(nonws);
    }

    if (line.startsWith("at")) return true;        // Start of the new stackframe entry
    if (line.startsWith("Caused")) return true;    // Caused by message
    if (line.startsWith("- locked")) return true;  // "Locked a monitor" logging
    if (line.startsWith("- waiting")) return true; // "Waiting for monitor" logging
    if (line.startsWith("- parking to wait")) return true;
    if (line.startsWith("java.lang.Thread.State")) return true;
    if (line.startsWith("\"")) return true;        // Start of the new thread (thread name)

    return false;
  }

  private static final class EditorPanel extends JPanel implements DataProvider{
    private final Editor myEditor;

    public EditorPanel(Editor editor) {
      super(new BorderLayout());
      myEditor = editor;
      add(myEditor.getComponent());
    }

    public Object getData(String dataId) {
      if (DataConstants.EDITOR.equals(dataId)) {
        return myEditor;
      }
      return null;
    }
  }

  protected void doOKAction() {
    if (performUnscramble()) {
      myLogFile.addCurrentTextToHistory();
      close(OK_EXIT_CODE);
    }
  }

  public void doHelpAction() {
    HelpManager.getInstance().invokeHelp("find.analyzeStackTrace");
  }

  private boolean performUnscramble() {
    UnscrambleSupport selectedUnscrambler = getSelectedUnscrambler();
    return showUnscrambledText(selectedUnscrambler, myLogFile.getText(), myProject, myEditor.getDocument().getText());
  }

  static boolean showUnscrambledText(UnscrambleSupport unscrambleSupport, String logName, Project project, String textToUnscramble) {
    String unscrambledTrace = unscrambleSupport == null ? textToUnscramble : unscrambleSupport.unscramble(project,textToUnscramble, logName);
    if (unscrambledTrace == null) return false;
    List<ThreadState> threadStates = ThreadDumpParser.parse(unscrambledTrace);
    final ConsoleView consoleView = addConsole(project, threadStates);
    printStacktrace(consoleView, unscrambledTrace);
    return true;
  }

  private static void printStacktrace(final ConsoleView consoleView, final String unscrambledTrace) {
    consoleView.clear();
    consoleView.print(unscrambledTrace+"\n", ConsoleViewContentType.ERROR_OUTPUT);
    consoleView.performWhenNoDeferredOutput(
      new Runnable() {
        public void run() {
          consoleView.scrollTo(0);
        }
      }
    );
  }

  private static ConsoleView addConsole(final Project project, final List<ThreadState> threadDump) {
    final ConsoleView consoleView = TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();
    final DefaultActionGroup toolbarActions = new DefaultActionGroup();
    JComponent consoleComponent = threadDump.size() > 1
                                  ? new ThreadDumpPanel(consoleView, toolbarActions, threadDump)
                                  : new MyConsolePanel(consoleView, toolbarActions);
    final RunContentDescriptor descriptor =
      new RunContentDescriptor(consoleView, null, consoleComponent,
                               IdeBundle.message("unscramble.unscrambled.stacktrace.tab")) {
      public boolean isContentReuseProhibited() {
        return true;
      }
    };

    final Executor executor = DefaultRunExecutor.getRunExecutorInstance();
    toolbarActions.add(new CloseAction(executor, descriptor, project));
    for (AnAction action: consoleView.createUpDownStacktraceActions()) {
      toolbarActions.add(action);
    }
    ExecutionManager.getInstance(project).getContentManager().showRunContent(executor, descriptor);
    return consoleView;
  }

  private static final class MyConsolePanel extends JPanel {
    public MyConsolePanel(ExecutionConsole consoleView, ActionGroup toolbarActions) {
      super(new BorderLayout());
      JPanel toolbarPanel = new JPanel(new BorderLayout());
      toolbarPanel.add(ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, toolbarActions,false).getComponent());
      add(toolbarPanel, BorderLayout.WEST);
      add(consoleView.getComponent(), BorderLayout.CENTER);
    }
  }

  private static class ThreadDumpPanel extends JPanel {
    public ThreadDumpPanel(final ConsoleView consoleView, final ActionGroup toolbarActions, final List<ThreadState> threadDump) {
      super(new BorderLayout());
      JPanel leftPanel = new JPanel(new BorderLayout());
      leftPanel.add(ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, toolbarActions,false).getComponent(),
                    BorderLayout.WEST);

      final JList threadList = new JList(threadDump.toArray(new ThreadState[threadDump.size()]));
      threadList.setCellRenderer(new ColoredListCellRenderer() {
        protected void customizeCellRenderer(final JList list,
                                             final Object value,
                                             final int index,
                                             final boolean selected,
                                             final boolean hasFocus) {
          ThreadState threadState = (ThreadState) value;
          setIcon(getThreadStateIcon(threadState));
          if (!selected) {
            ThreadState selectedThread = (ThreadState)list.getSelectedValue();
            if (threadState.isDeadlocked()) {
              setBackground(LightColors.RED);
            }
            else if (selectedThread != null && threadState.isHoldingLock(selectedThread)) {
              setBackground(Color.YELLOW);
            }
            else {
              setBackground(UIUtil.getListBackground());
            }
          }
          SimpleTextAttributes attrs = getAttributes(threadState);
          append(threadState.getName() + " (", attrs);
          String detail = threadState.getThreadStateDetail();
          if (detail != null) {
            append(detail, attrs);
          }
          else {
            append(threadState.getState().trim(), attrs);
          }
          append(")", attrs);
          if (threadState.getExtraState() != null) {
            append(" [" + threadState.getExtraState() + "]", attrs);
          }
        }
      });
      threadList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      threadList.addListSelectionListener(new ListSelectionListener() {
        public void valueChanged(final ListSelectionEvent e) {
          int index = threadList.getSelectedIndex();
          if (index >= 0) {
            ThreadState selection = threadDump.get(index);
            printStacktrace(consoleView, selection.getStackTrace());
          }
          else {
            printStacktrace(consoleView, "");
          }
          threadList.repaint();
        }
      });
      leftPanel.add(new JScrollPane(threadList), BorderLayout.CENTER);


      add(leftPanel, BorderLayout.WEST);
      add(consoleView.getComponent(), BorderLayout.CENTER);
    }

    private static Icon getThreadStateIcon(final ThreadState threadState) {
      if (threadState.isSleeping()) {
        return PAUSE_ICON;
      }
      if (threadState.isLocked()) {
        return LOCKED_ICON;
      }
      if (threadState.getOperation() == ThreadOperation.Socket) {
        return SOCKET_ICON;
      }
      if (threadState.getOperation() == ThreadOperation.IO) {
        return IO_ICON;
      }
      if (threadState.isEDT()) {
        if ("idle".equals(threadState.getThreadStateDetail())) {
          return IDLE_ICON;
        }
        return EDT_BUSY_ICON;
      }
      return RUNNING_ICON;
    }

    private static SimpleTextAttributes getAttributes(final ThreadState threadState) {
      if (threadState.isSleeping()) {
        return SimpleTextAttributes.GRAY_ATTRIBUTES;
      }
      if (threadState.isEmptyStackTrace() || ThreadDumpParser.isKnownJdkThread(threadState)) {
        return new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, Color.GRAY.brighter());
      }
      if (threadState.isEDT()) {
        return SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES;
      }
      return SimpleTextAttributes.REGULAR_ATTRIBUTES;
    }
  }

  protected String getDimensionServiceKey(){
    return "#com.intellij.unscramble.UnscrambleDialog";
  }

  public JComponent getPreferredFocusedComponent() {
    return myEditor.getContentComponent();
  }
}