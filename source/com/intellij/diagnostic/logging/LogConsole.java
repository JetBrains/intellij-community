package com.intellij.diagnostic.logging;

import com.intellij.execution.filters.TextConsoleBuidlerFactory;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.*;

/**
 * User: anna
 * Date: Apr 19, 2005
 */
public abstract class LogConsole extends JPanel implements Disposable{
  private final ConsoleView myConsole;
  private final LightProcessHandler myProcessHandler = new LightProcessHandler();
  private ReaderThread myReaderThread;

  private static final long PROCESS_IDLE_TIMEOUT = 200;
  public LogConsole(Project project, File file) {
    super(new BorderLayout());
    myReaderThread = new ReaderThread(file);
    TextConsoleBuilder builder = TextConsoleBuidlerFactory.getInstance().createBuilder(project);
    myConsole = builder.getConsole();
    myConsole.attachToProcess(myProcessHandler);
    add(myConsole.getComponent(), BorderLayout.CENTER);
    myReaderThread.start();
  }

  public abstract boolean isActive();

  public void dispose() {
    myConsole.dispose();
    myReaderThread.stopRunning(false);
  }

  public void stopRunning(){
    myReaderThread.stopRunning(true);
  }

  public JComponent getComponent() {
    return this;
  }

  private void addMessage(String text){
    myProcessHandler.notifyTextAvailable(text + "\n", ProcessOutputTypes.STDOUT);
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
    public ReaderThread(File file){
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
        myFileStream.skip(file.length());
      }
      catch (Throwable e) {
        myFileStream = null;
      }
    }

    public void run() {
      if (myFileStream == null) return;
      while (myRunning){
        try {
          long endTime = System.currentTimeMillis() + PROCESS_IDLE_TIMEOUT;
          while (System.currentTimeMillis() < endTime){
            if (myRunning && myFileStream != null && myFileStream.ready()){
              addMessage(myFileStream.readLine());
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

    public void stopRunning(boolean flush){
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
