package com.intellij.diagnostic.logging;

import com.intellij.diagnostic.DiagnosticBundle;
import com.intellij.execution.filters.TextConsoleBuidlerFactory;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.FilterComponent;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.util.ArrayList;

/**
 * User: anna
 * Date: Apr 19, 2005
 */
public abstract class LogConsole extends AdditionalTabComponent {
  private final ConsoleView myConsole;
  private final LightProcessHandler myProcessHandler = new LightProcessHandler();
  private ReaderThread myReaderThread;
  private final boolean mySkipContents;

  @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
  private Document myOriginalDocument = null;

  @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
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

  private static final long PROCESS_IDLE_TIMEOUT = 1000;
  private String myTitle = null;
  private Project myProject;

  public LogConsole(Project project, File file, boolean skipContents, String title) {
    super(new BorderLayout());
    mySkipContents = skipContents;
    myTitle = title;
    myProject = project;
    myReaderThread = new ReaderThread(file);
    TextConsoleBuilder builder = TextConsoleBuidlerFactory.getInstance().createBuilder(project);
    myConsole = builder.getConsole();
    myConsole.attachToProcess(myProcessHandler);
    myReaderThread.start();
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

  public String getTabTitle() {
    return myTitle;
  }

  public void dispose() {
    myConsole.dispose();
    myReaderThread.stopRunning(false);
    myFilter.dispose();
  }

  public void stopRunning(){
    myReaderThread.stopRunning(true);
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
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              myOriginalDocument.insertString(myOriginalDocument.getTextLength(), text + "\n");
            }
          });
        }
      }, ModalityState.NON_MMODAL);
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

  private Document getOriginalDocument(){
    if (myOriginalDocument == null) {
      final Editor editor = (Editor)((ConsoleViewImpl)myConsole).getData(DataConstants.EDITOR);
      if (editor != null){
        myOriginalDocument = new DocumentImpl(editor.getDocument().getText());
      }
    }
    return myOriginalDocument;
  }

  private void filterConsoleOutput(Condition<String> isApplicable) {
    myOriginalDocument = getOriginalDocument();
    if (myOriginalDocument != null){
      myConsole.clear();
      final int lineCount = myOriginalDocument.getLineCount();
      for (int line = 0; line < lineCount; line++) {
        final String text =
          myOriginalDocument.getCharsSequence().subSequence(myOriginalDocument.getLineStartOffset(line), myOriginalDocument.getLineEndOffset(line)).toString();
        final String contentType = LogConsolePreferences.getType(text);
        if (isApplicable.value(text)){
          myConsole.print(text + "\n", contentType != null ? LogConsolePreferences.getContentType(contentType) :
                                       (myPrevType == LogConsolePreferences.ERROR ? ConsoleViewContentType.ERROR_OUTPUT : ConsoleViewContentType.NORMAL_OUTPUT));
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

  private class ReaderThread extends Thread{
    private BufferedReader myFileStream;
    private boolean myRunning = true;
    @SuppressWarnings({"IOResourceOpenedButNotSafelyClosed"})
    public ReaderThread(File file){
      //noinspection HardCodedStringLiteral
      super("Reader Thread");
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

    public synchronized void run() {
      if (myFileStream == null) return;
      while (myRunning){
        try {
          long endTime = System.currentTimeMillis() + PROCESS_IDLE_TIMEOUT/1000;
          while (System.currentTimeMillis() < endTime){
            if (myRunning && myFileStream != null && myFileStream.ready()){
              addMessage(myFileStream.readLine());
            } else {
              break;
            }
          }
          synchronized (this) {
            wait(PROCESS_IDLE_TIMEOUT);
            while (myRunning && !isActive()){
              wait(PROCESS_IDLE_TIMEOUT/4);
            }
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

    public synchronized void stopRunning(boolean flush){
      myRunning = false;
      try {
        if (myFileStream != null){
          if (flush) {//flush everything to log on stop
            String line = myFileStream.readLine();
            while (line != null){
              addMessage(line);
              line = myFileStream.readLine();
            }
          }
          myFileStream.close();
          myFileStream = null;
        }
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
  }
}
