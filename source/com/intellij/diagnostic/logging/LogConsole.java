package com.intellij.diagnostic.logging;

import com.intellij.diagnostic.DiagnosticBundle;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.FilterComponent;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.io.*;
import java.util.ArrayList;

/**
 * User: anna
 * Date: Apr 19, 2005
 */
public abstract class LogConsole extends AdditionalTabComponent implements ChangeListener {
  private ConsoleView myConsole;
  private final LightProcessHandler myProcessHandler = new LightProcessHandler();
  private ReaderThread myReaderThread;
  private final boolean mySkipContents;

  private StringBuffer myOriginalDocument = null;

  private String myPrevType = null;

  private FilterComponent myFilter = new FilterComponent("LOG_FILTER_HISTORY", 5) {
    public void filter() {
      final LogConsolePreferences preferences = LogConsolePreferences.getInstanceEx(myProject);
      preferences.updateCustomFilter(getFilter());
      filterConsoleOutput(new Condition<String>() {
        public boolean value(final String line) {
          return preferences.isApplicable(line, myPrevType);
        }
      });
    }
  };

  private String myTitle = null;
  private Project myProject;
  private String myPath;

  public LogConsole(Project project, File file, boolean skipContents, String title) {
    super(new BorderLayout());
    mySkipContents = skipContents;
    myTitle = title;
    myProject = project;
    myPath = file.getAbsolutePath();
    myReaderThread = new ReaderThread(file);
    TextConsoleBuilder builder = TextConsoleBuilderFactory.getInstance().createBuilder(project);
    myConsole = builder.getConsole();
    myConsole.attachToProcess(myProcessHandler);
  }

  private JComponent createToolbar(){
    DefaultActionGroup group = new DefaultActionGroup();
    final LogConsolePreferences registrar = myProject.getComponent(LogConsolePreferences.class);
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
          filterConsoleOutput(new Condition<String>() {
            public boolean value(final String line) {
              return filter.isAcceptable(line);
            }
          });
        }
      });
    }
    final ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true);
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(actionToolbar.getComponent(), BorderLayout.WEST);
    myFilter.reset();
    panel.add(myFilter, BorderLayout.EAST);
    return panel;
  }


  public JComponent getComponent() {
    removeAll();
    add(myConsole.getComponent(), BorderLayout.CENTER);
    add(createToolbar(), BorderLayout.NORTH);
    return this;
  }

  public abstract boolean isActive();

  public void activate() {
    if (myReaderThread == null) return;
    if (isActive()) {
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

  public String getPath() {
    return myPath;
  }

  public void dispose() {
    myConsole.dispose();
    if (myReaderThread.myFileStream != null) {
      try {
        myReaderThread.myFileStream.close();
      }
      catch (IOException e) {
        LOG.error(e);
      }
      myReaderThread.myFileStream = null;
    }
    myFilter.dispose();
    myConsole = null;
    myReaderThread = null;
    myFilter = null;
  }

  public void stopRunning(){
    if (myReaderThread != null && !isActive()) {
      myReaderThread.stopRunning();
    }
  }

  private void addMessage(final String text){
    final String key = LogConsolePreferences.getType(text);
    if (LogConsolePreferences.getInstanceEx(myProject).isApplicable(text, myPrevType)){
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
      final Editor editor = (Editor)((ConsoleViewImpl)myConsole).getData(DataConstants.EDITOR);
      if (editor != null){
        myOriginalDocument = new StringBuffer(editor.getDocument().getText());
      }
    }
    return myOriginalDocument;
  }

  private void filterConsoleOutput(Condition<String> isApplicable) {
    myOriginalDocument = getOriginalDocument();
    if (myOriginalDocument != null) {
      myConsole.clear();
      final String[] lines = myOriginalDocument.toString().split("\n");
      for (String line : lines) {
        final String contentType = LogConsolePreferences.getType(line);
        if (isApplicable.value(line)) {
          myConsole.print(line + "\n", contentType != null
                                       ? LogConsolePreferences.getContentType(contentType)
                                       : (myPrevType == LogConsolePreferences.ERROR
                                          ? ConsoleViewContentType.ERROR_OUTPUT
                                          : ConsoleViewContentType.NORMAL_OUTPUT));
        }
        if (contentType != null) {
          myPrevType = contentType;
        }
      }
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
        }
        catch (FileNotFoundException e) {
          FileUtil.createParentDirs(file);
          if (!file.createNewFile()) return;
          myFileStream = new BufferedReader(new FileReader(file));
        }
        if (mySkipContents) myFileStream.skip(file.length());
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
          LOG.error(e);
        }
      }
    }

    public void startRunning() {
      myRunning = true;
    }

    public void stopRunning() {
      myRunning = false;
    }
  }
}
