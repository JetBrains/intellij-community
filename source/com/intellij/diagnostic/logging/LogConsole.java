package com.intellij.diagnostic.logging;

import com.intellij.diagnostic.DiagnosticBundle;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.FilterComponent;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.*;
import java.util.ArrayList;

/**
 * User: anna
 * Date: Apr 19, 2005
 */
public abstract class LogConsole extends AdditionalTabComponent implements ChangeListener, LogConsolePreferences.FilterListener {
  private ConsoleView myConsole;
  private final LightProcessHandler myProcessHandler = new LightProcessHandler();
  private ReaderThread myReaderThread;
  private final long mySkippedContents;

  private StringBuffer myOriginalDocument = null;

  private String myPrevType = null;
  private String myLineUnderSelection = null;
  private int myLineOffset = -1;

  private FilterComponent myFilter = new FilterComponent("LOG_FILTER_HISTORY", 5) {
    public void filter() {
      getPreferences().updateCustomFilter(getFilter());
    }
  };

  private String myTitle = null;
  private Project myProject;
  private String myPath;
  private boolean myWasInitialized;

  public LogConsole(Project project, File file, long skippedContents, String title) {
    super(new BorderLayout());
    mySkippedContents = skippedContents;
    myTitle = title;
    myProject = project;
    myPath = file.getAbsolutePath();
    myReaderThread = new ReaderThread(file);
    TextConsoleBuilder builder = TextConsoleBuilderFactory.getInstance().createBuilder(project);
    myConsole = builder.getConsole();
    myConsole.attachToProcess(myProcessHandler);
    getPreferences().addFilterListener(this);
  }

  @SuppressWarnings({"NonStaticInitializer"})
  private JComponent createToolbar(){
    DefaultActionGroup group = new DefaultActionGroup();
    final LogConsolePreferences registrar = getPreferences();
    final ArrayList<LogFilter> filters = new ArrayList<LogFilter>();
    filters.add(new LogFilter(DiagnosticBundle.message("log.console.filter.by.type", LogConsolePreferences.INFO), IconLoader.getIcon("/ant/filterInfo.png")){
      public boolean isAcceptable(String line) {
        return registrar.isApplicable(line, myPrevType);
      }
    });
    filters.add(new LogFilter(DiagnosticBundle.message("log.console.filter.by.type", LogConsolePreferences.WARNING), IconLoader.getIcon("/ant/filterWarning.png")){
      public boolean isAcceptable(String line) {
        return registrar.isApplicable(line, myPrevType);
      }
    });
    filters.add(new LogFilter(DiagnosticBundle.message("log.console.filter.by.type", LogConsolePreferences.ERROR), IconLoader.getIcon("/ant/filterError.png")){
      public boolean isAcceptable(String line) {
        return registrar.isApplicable(line, myPrevType);
      }
    });
    filters.addAll(registrar.getRegisteredLogFilters());
    for (final LogFilter filter : filters) {
      group.add(new ToggleAction(filter.getName(), filter.getName(), filter.getIcon()){
        public boolean isSelected(AnActionEvent e) {
          return registrar.isFilterSelected(filter);
        }

        public void setSelected(AnActionEvent e, boolean state) {
          registrar.setFilterSelected(filter, state);
        }
      });
    }
    final ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true);
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(actionToolbar.getComponent(), BorderLayout.WEST);
    myFilter.reset();
    myFilter.setSelectedItem(registrar.CUSTOM_FILTER != null ? registrar.CUSTOM_FILTER : "");
    panel.add(myFilter, BorderLayout.EAST);
    new AnAction(){
      {
        registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, KeyEvent.SHIFT_DOWN_MASK)), LogConsole.this);
      }
      public void actionPerformed(final AnActionEvent e) {
        myFilter.requestFocusInWindow();
      }
    };
    return panel;
  }

  public void onFilterStateChange(final LogFilter filter) {
    filterConsoleOutput(new Condition<String>() {
      public boolean value(final String line) {
        return filter.isAcceptable(line);
      }
    });
  }

  public void onTextFilterChange() {
    filterConsoleOutput(new Condition<String>() {
      public boolean value(final String line) {
        return getPreferences().isApplicable(line, myPrevType);
      }
    });
  }

  public JComponent getComponent() {
    if (!myWasInitialized) {
      myWasInitialized = true;
      add(myConsole.getComponent(), BorderLayout.CENTER);
      add(createToolbar(), BorderLayout.NORTH);
    }
    return this;
  }

  public abstract boolean isActive();

  public void activate() {
    if (myReaderThread == null) return;
    if (isActive()) {
      myFilter.setSelectedItem(getPreferences().CUSTOM_FILTER);
      myReaderThread.startRunning();
      ApplicationManager.getApplication().executeOnPooledThread(myReaderThread);     
    } else {
      myReaderThread.stopRunning();
    }
  }

  public void stateChanged(final ChangeEvent e) {
    activate();
  }

  public String getTabTitle() {
    return myTitle;
  }

  @Nullable
  public String getTooltip() {
    return myPath;
  }

  public String getPath() {
    return myPath;
  }

  public void dispose() {
    getPreferences().removeFilterListener(this);
    if (myReaderThread != null && myReaderThread.myFileStream != null) {
      myReaderThread.stopRunning();
      try {
        myReaderThread.myFileStream.close();
      }
      catch (IOException e) {
        LOG.error(e);
      }
      myReaderThread.myFileStream = null;
      myReaderThread = null;
    }
    if (myConsole != null) {
      myConsole.dispose();
      myConsole = null;
    }
    if (myFilter != null) {
      myFilter.dispose();
      myFilter = null;
    }
    myOriginalDocument = null;
  }

  private void stopRunning(){
    if (myReaderThread != null && !isActive()) {
      myReaderThread.stopRunning();
    }
  }

  private void addMessage(final String text){
    if (text == null) return;
    final String key = LogConsolePreferences.getType(text);
    if (getPreferences().isApplicable(text, myPrevType)){
      myProcessHandler.notifyTextAvailable(text + "\n", key != null ?
                                                        LogConsolePreferences.getProcessOutputTypes(key) :
                                                        (myPrevType == LogConsolePreferences.ERROR ? ProcessOutputTypes.STDERR : ProcessOutputTypes.STDOUT));
    }
    if (key != null) {
      myPrevType = key;
    }
    myOriginalDocument = getOriginalDocument();
    if (myOriginalDocument != null){
      myOriginalDocument.append(text).append("\n");
    }
  }

  private LogConsolePreferences getPreferences() {
    return LogConsolePreferences.getInstance(myProject);
  }

  public void attachStopLogConsoleTrackingListener(final ProcessHandler process) {
    if (process != null) {
      final ProcessAdapter stopListener = new ProcessAdapter() {
        public void processTerminated(final ProcessEvent event) {
          process.removeProcessListener(this);
          stopRunning();
        }
      };
      process.addProcessListener(stopListener);
    }
  }

  private StringBuffer getOriginalDocument(){
    if (myOriginalDocument == null) {
      final Editor editor = getEditor();
      if (editor != null){
        myOriginalDocument = new StringBuffer(editor.getDocument().getText());
      }
    }
    return myOriginalDocument;
  }

  @Nullable
  private Editor getEditor() {
    return myConsole != null ? (Editor)((DataProvider)myConsole).getData(DataConstants.EDITOR) : null;
  }

  private void filterConsoleOutput(Condition<String> isApplicable) {
    myOriginalDocument = getOriginalDocument();
    if (myOriginalDocument != null) {
      final Editor editor = getEditor();
      LOG.assertTrue(editor != null);
      final Document document = editor.getDocument();
      final int caretOffset = editor.getCaretModel().getOffset();
      if (caretOffset > -1) {
        int line = document.getLineNumber(caretOffset);
        if (line > -1 && line < document.getLineCount()) {
          final int startOffset = document.getLineStartOffset(line);
          myLineUnderSelection = document.getText().substring(startOffset, document.getLineEndOffset(line));
          myLineOffset = caretOffset - startOffset;
        }
      }
      myConsole.clear();
      final String[] lines = myOriginalDocument.toString().split("\n");
      int offset = 0;
      boolean caretPositioned = false;
      for (String line : lines) {
        final String contentType = LogConsolePreferences.getType(line);
        if (isApplicable.value(line)) {
          myConsole.print(line + "\n", contentType != null
                                       ? LogConsolePreferences.getContentType(contentType)
                                       : (myPrevType == LogConsolePreferences.ERROR
                                          ? ConsoleViewContentType.ERROR_OUTPUT
                                          : ConsoleViewContentType.NORMAL_OUTPUT));
          if (!caretPositioned) {
            if (Comparing.strEqual(myLineUnderSelection, line)) {
              caretPositioned = true;
              offset += myLineOffset != -1 ? myLineOffset : 0;
            } else {
              offset += line.length() + 1;
            }
          }
        }
        if (contentType != null) {
          myPrevType = contentType;
        }
      }
      myConsole.scrollTo(offset);
    }
  }

  private static class LightProcessHandler extends ProcessHandler {
    protected void destroyProcessImpl() {
      throw new UnsupportedOperationException();
    }

    protected void detachProcessImpl() {
      throw new UnsupportedOperationException();
    }

    public boolean detachIsDefault() {
      return false;
    }

    @Nullable
    public OutputStream getProcessInput() {
      return null;
    }
  }

  private static final Logger LOG = Logger.getInstance("com.intellij.diagnostic.logging.LogConsole");

  private class ReaderThread implements Runnable {
    private BufferedReader myFileStream;
    private boolean myRunning = true;
    @SuppressWarnings({"IOResourceOpenedButNotSafelyClosed"})
    public ReaderThread(File file){
      try {
        try {
          myFileStream = new BufferedReader(new FileReader(file));
          if (file.length() >= mySkippedContents) { //do not skip forward
            myFileStream.skip(mySkippedContents);
          }
        }
        catch (FileNotFoundException e) {
          FileUtil.createParentDirs(file);
          if (!file.createNewFile()) return;
          myFileStream = new BufferedReader(new FileReader(file));
        }
      }
      catch (Throwable e) {
        myFileStream = null;
      }
    }

    public void run() {
      if (myFileStream == null) return;
      while (myRunning){
        try {
          int i = 0;
          while (i++ < 100){
            if (myRunning && myFileStream != null && myFileStream.ready()){
              addMessage(myFileStream.readLine());
            } else {
              break;
            }
          }
          synchronized (this) {
            wait(100);
          }
        }
        catch (IOException e) {
          LOG.error(e);
        }
        catch (InterruptedException e) {
          dispose();
        }
      }
    }

    public void startRunning() {
      myRunning = true;
    }

    public void stopRunning() {
      myRunning = false;
      synchronized (this) {
        notifyAll();
      }
    }
  }


  public JComponent getPreferredFocusableComponent() {
    return myConsole.getPreferredFocusableComponent();
  }
}
