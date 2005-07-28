package com.intellij.compiler.impl.javaCompiler;

import com.intellij.compiler.OutputParser;
import com.intellij.compiler.make.CacheCorruptedException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.diagnostic.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 30, 2004
 */
public abstract class CompilerParsingThread extends Thread implements OutputParser.Callback {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.javaCompiler.JavaCompilerParsingThread");
  public static final String TERMINATION_STRING = "__terminate_read__";
  private Reader myCompilerOutStreamReader;
  private Process myProcess;
  private OutputParser myOutputParser;
  private boolean mySkipLF = false;
  private Throwable myError = null;
  private final boolean myIsUnitTestMode;
  private String myClassFileToProcess = null;

  
  public CompilerParsingThread(Process process, OutputParser outputParser, final boolean readErrorStream) {
    super("CompilerParsingThread");
    myProcess = process;
    myOutputParser = outputParser;
    myCompilerOutStreamReader = new BufferedReader(new InputStreamReader(readErrorStream? process.getErrorStream() : process.getInputStream()), 16384);
    myIsUnitTestMode = ApplicationManager.getApplication().isUnitTestMode();
  }

  public void run() {
    try {
      while(true){
        if (!myIsUnitTestMode && myProcess == null) {
          break;
        }
        if(!myOutputParser.processMessageLine(this)) {
          break;
        }
        if (isCancelled()) {
          killProcess();
          break;
        }
      }
      if (myClassFileToProcess != null) {
        processCompiledClass(myClassFileToProcess);
        myClassFileToProcess = null;
      }
    }
    catch (Throwable e) {
      myError = e;
      e.printStackTrace();
      killProcess();
    }
    killProcess();
  }

  private void killProcess() {
    if (myProcess != null) {
      myProcess.destroy();
      myProcess = null;
    }
  }

  public Throwable getError() {
    return myError;
  }

  public final String getNextLine() {
    try {
      final String line = readLine(myCompilerOutStreamReader);
      if (LOG.isDebugEnabled()) {
        LOG.debug("LIne read: #" + line + "#");
      }
      if (TERMINATION_STRING.equals(line)) {
        return null;
      }
      return (line != null ? line.trim() : null);
    }
    catch(IOException e) {
      if (LOG.isDebugEnabled()) {
        LOG.debug(e);
      }
      return null;
    }
  }

  public final void fileGenerated(String path) {
    String previousPath = myClassFileToProcess;
    myClassFileToProcess = path;
    if (previousPath != null) {
      try {
        processCompiledClass(previousPath);
      }
      catch (CacheCorruptedException e) {
        myError = e;
        killProcess();
      }
    }
  }

  public abstract void setProgressText(String text);

  public abstract void fileProcessed(String path);

  public abstract void message(CompilerMessageCategory category, String message, String url, int lineNum, int columnNum);

  protected abstract boolean isCancelled();

  protected abstract void processCompiledClass(final String classFileToProcess) throws CacheCorruptedException;


  private String readLine(final Reader reader) throws IOException {
    boolean first = true;
    StringBuffer buffer = new StringBuffer();
    while(true){
      int c = reader.read();
      if (c == -1) break;
      first = false;
      if (c == '\n'){
        if (mySkipLF){
          mySkipLF = false;
          continue;
        }
        break;
      }
      else if (c == '\r'){
        mySkipLF = true;
        break;
      }
      else{
        mySkipLF = false;
        buffer.append((char)c);
      }
    }
    if (first) return null;
    String s = buffer.toString();
    return s;
  }
}
