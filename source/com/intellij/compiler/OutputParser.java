package com.intellij.compiler;

import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;

import java.io.File;

public abstract class OutputParser {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.OutputParser");
  public static interface Callback {
    String getNextLine();
    void setProgressText(String text);
    void fileProcessed(String path);
    void fileGenerated(String path);
    void message(CompilerMessageCategory category, String message, String url, int lineNum, int columnNum);
  }

  public abstract boolean processMessageLine(Callback callback);

  protected void processLoading(final String line, final Callback callback) {
    //if (LOG.isDebugEnabled()) {
    //  LOG.debug(line);
    //}
    if (line.startsWith("[parsing started")){ // javac
      String filePath = line.substring("[parsing started".length(), line.length() - 1).trim();
      filePath = filePath.replace(File.separatorChar, '/');
      processParsingMessage(callback, filePath);
    }
    else if (line.startsWith("[parsed") && line.indexOf(".java") >= 0) { // javac version 1.2.2
      int index = line.indexOf(".java");
      String filePath = line.substring("[parsed".length(), index + ".java".length()).trim();
      processParsingMessage(callback, filePath.replace(File.separatorChar, '/'));
    }
    else if (line.startsWith("[read") && line.endsWith(".java]")){ // jikes
      String filePath = line.substring("[read".length(), line.length() - 1).trim();
      processParsingMessage(callback, filePath.replace(File.separatorChar, '/'));
    }
    else if (line.startsWith("[parsing completed")){
    }
    else if (line.startsWith("[loading") || line.startsWith("[loaded") || line.startsWith("[read")){
      if (LOG.isDebugEnabled()) {
        LOG.debug(line);
      }
      callback.setProgressText("Loading classes...");
    }
    else if (line.startsWith("[checking")){
      String className = line.substring("[checking".length(), line.length() - 1).trim();
      callback.setProgressText("Compiling " + className + "...");
    }
    else if (line.startsWith("[wrote") || line.startsWith("[write")){
      String filePath = line.substring("[wrote".length(), line.length() - 1).trim();
      processParsingMessage(callback, filePath.replace(File.separatorChar, '/'));
    }
    else {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Failed to interpret: #" + line + "#");
      }
    }
  }

  private void processParsingMessage(final Callback callback, final String filePath) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Process parsing message: " + filePath);
    }
    int index = filePath.lastIndexOf('/');
    final String name = index >= 0 ? filePath.substring(index + 1) : filePath;

    final FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(name);
    if (StdFileTypes.JAVA.equals(fileType)) {
      callback.fileProcessed(filePath);
      callback.setProgressText("Parsing " + name + "...");
    }
    else if (StdFileTypes.CLASS.equals(fileType)) {
      callback.fileGenerated(filePath);
    }
  }

  protected void addMessage(Callback callback, CompilerMessageCategory type, String message) {
    if(message == null || message.trim().length() == 0) return;
    addMessage(callback, type, message, null, -1, -1);
  }

  protected void addMessage(Callback callback, CompilerMessageCategory type, String text, String url, int line, int column){
    callback.message(type, text, url, line, column);
  }
}