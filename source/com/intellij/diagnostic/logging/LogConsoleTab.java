package com.intellij.diagnostic.logging;

import com.intellij.execution.filters.TextConsoleBuidlerFactory;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.Disposeable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;

import javax.swing.*;
import java.awt.*;
import java.io.*;

/**
 * User: anna
 * Date: Apr 19, 2005
 */
public class LogConsoleTab extends JPanel implements Disposeable{
  private final ConsoleView myConsole;
  private final Project myProject;
  private final LightProcessHandler myProcessHandler = new LightProcessHandler();
  private ReaderThread myReaderThread;


  public LogConsoleTab(Project project, File file) {
    super(new BorderLayout());
    myProject = project;
    try {
      myReaderThread = new ReaderThread(file);
    }
    catch (FileNotFoundException e) {
      LOG.error(e);
    }
    TextConsoleBuilder builder = TextConsoleBuidlerFactory.getInstance().createBuilder(myProject);
    myConsole = builder.getConsole();
    myConsole.attachToProcess(myProcessHandler);
    add(myConsole.getComponent(), BorderLayout.CENTER);    
    myReaderThread.start();
  }


  public void dispose() {
    myConsole.dispose();
    myReaderThread.stopRunning();
  }

  public JComponent getComponent() {
    return this;
  }

  private void addMessage(int text){
    myProcessHandler.notifyTextAvailable(String.valueOf ((char)text), ProcessOutputTypes.STDOUT);
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

    public OutputStream getProcessInput() {
      return null;
    }
  }

  private static final Logger LOG = Logger.getInstance("com.intellij.diagnostic.logging.LogConsoleTab");

  private class ReaderThread extends Thread{
    private FileInputStream myFileStream;
    private boolean myRunning = true;
    public ReaderThread(File file) throws FileNotFoundException {
      super("Reader Thread");
      myFileStream = new FileInputStream(file);
      try {
        myFileStream.skip(file.length());
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }

    public void run() {
      while (myRunning){
        try {
          while (myFileStream.available() > 0){
            addMessage(myFileStream.read());
          }
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    }

    public void stopRunning(){
      myRunning = false;
    }

  }

}
