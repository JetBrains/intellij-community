package com.intellij.compiler;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;

public class JavacOutputParser extends OutputParser {
  private int myTabSize;

  public JavacOutputParser(Project project) {
    myTabSize = CodeStyleSettingsManager.getSettings(project).getTabSize(StdFileTypes.JAVA);
  }

  public boolean processMessageLine(Callback callback) {
    final String line = callback.getNextLine();
    if(line == null) {
      return false;
    }
    if (StringUtil.startsWithChar(line, '[') && StringUtil.endsWithChar(line, ']')){
      processLoading(line, callback);
      return true;
    }
    int colonIndex1 = line.indexOf(':');
    if (colonIndex1 == 1){ // drive letter
      colonIndex1 = line.indexOf(':', colonIndex1 + 1);
    }
    if(colonIndex1 == -1) {
      if(line.endsWith("errors") || line.endsWith("error")) {
        //addMessage(messageHandler,MessageCategory.STATISTICS, line);
        return true;
      }
      if(line.endsWith("warnings") || line.endsWith("warning")) {
        //addMessage(messageHandler,MessageCategory.STATISTICS, line);
        return true;
      }
    }

    if (colonIndex1 >= 0){
      String part1 = line.substring(0, colonIndex1).trim();

      if(part1.equals("error")) {
        addMessage(callback, CompilerMessageCategory.ERROR, line.substring(colonIndex1));
        return true;
      }
      if(part1.equals("javac")) {
        addMessage(callback, CompilerMessageCategory.ERROR, line);
        return true;
      }

      int colonIndex2 = line.indexOf(':', colonIndex1 + 1);
      if (colonIndex2 >= 0){
        final String filePath = part1.replace(File.separatorChar, '/');
        final VirtualFile[] file = new VirtualFile[1];
        ApplicationManager.getApplication().runReadAction(new Runnable(){
          public void run(){
            file[0] = LocalFileSystem.getInstance().findFileByPath(filePath);
          }
        });

        try {
          int lineNum = Integer.parseInt(line.substring(colonIndex1 + 1, colonIndex2).trim());
          String message = line.substring(colonIndex2 + 1).trim();
          CompilerMessageCategory category = CompilerMessageCategory.ERROR;
          if (message.startsWith("warning:")){
            message = message.substring("warning:".length()).trim();
            category = CompilerMessageCategory.WARNING;
          }

          ArrayList messages = new ArrayList();
          messages.add(message);
          int colNum = 0;
          String prevLine = null;
          do{
            final String nextLine = callback.getNextLine();
            if (nextLine == null) {
              return false;
            }
            if (nextLine.trim().equals("^")){
              final int fakeColNum = nextLine.indexOf('^') + 1;
              final CharSequence chars = (prevLine != null)? prevLine : line;
              final int offsetColNum = EditorUtil.calcOffset(null, chars, 0, chars.length(), fakeColNum, 8);
              colNum = EditorUtil.calcColumnNumber(null, chars,0, offsetColNum, myTabSize);
              break;
            }
            if (prevLine != null) {
              messages.add(prevLine);
            }
            prevLine = nextLine;
          }
          while(true);

          if (colNum > 0){
            messages = convertMessages(messages);
            StringBuffer buf = new StringBuffer();
            for (Iterator it = messages.iterator(); it.hasNext();) {
              String m = (String)it.next();
              if (buf.length() > 0) {
                buf.append("\n");
              }
              buf.append(m);
            }
            addMessage(callback, category, buf.toString(), VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, filePath), lineNum, colNum);
            return true;
          }
        }
        catch (NumberFormatException e) {
        }
      }
    }

    if(line.endsWith("java.lang.OutOfMemoryError")) {
      addMessage(callback, CompilerMessageCategory.ERROR, "Out of memory. Increase the maximum heap size in Project Properties|Compiler settings.");
      return true;
    }

    addMessage(callback, CompilerMessageCategory.INFORMATION, line);
    return true;
  }


  private static ArrayList convertMessages(ArrayList messages) {
    if(messages.size() <= 1) {
      return messages;
    }
    String line0 = (String)messages.get(0);
    String line1 = (String)messages.get(1);
    int colonIndex = line1.indexOf(':');
    if (colonIndex > 0){
      String part1 = line1.substring(0, colonIndex).trim();
      if (part1.equals("symbol")){
        String symbol = line1.substring(colonIndex + 1).trim();
        messages.remove(1);
        if(messages.size() >= 2) {
          messages.remove(1);
        }
        messages.set(0, line0+" " + symbol);
      }
    }
    return messages;
  }

  /*
  private boolean isError(String line) {
    for (int idx = 0; idx < myErrorPatterns.length; idx++) {
      Pattern errorPattern = myErrorPatterns[idx];
      if (errorPattern.matcher(line).matches()) {
        return true;
      }
    }
    return false;
  }

  private  boolean isWarning(String line) {
    for (int idx = 0; idx < myWarningPatterns.length; idx++) {
      Pattern errorPattern = myWarningPatterns[idx];
      if (errorPattern.matcher(line).matches()) {
        return true;
      }
    }
    return false;
  }


  private Pattern[] myErrorPatterns;
  private Pattern[] myWarningPatterns;

  private static final String COMPILER_RB = "com.sun.tools.javac.v8.resources.compiler";

  private void precompilePatterns(Project project) {
    final ClassLoader loader = createLoader((ProjectJdkEx)ProjectRootManagerEx.getInstanceEx(project).getJdk());
    if (loader == null) {
      return;
    }
    final ResourceBundle bundle = ResourceBundle.getBundle(COMPILER_RB, Locale.getDefault(), loader);
    final Enumeration keys = bundle.getKeys();
    List errorPatterns = new ArrayList();
    List warningPatterns = new ArrayList();
    while (keys.hasMoreElements()) {
      final Object elem = keys.nextElement();
      if (!(elem instanceof String)) {
        continue;
      }
      String key = (String)elem;
      if (key.startsWith("compiler.err.")) {
        addPattern(errorPatterns, bundle.getString(key));
      }
      else if (key.startsWith("compiler.warn.")) {
        addPattern(warningPatterns, bundle.getString(key));
      }
    }
    myErrorPatterns = (Pattern[])errorPatterns.toArray(new Pattern[errorPatterns.size()]);
    myWarningPatterns = (Pattern[])warningPatterns.toArray(new Pattern[warningPatterns.size()]);
  }

  private static ClassLoader createLoader(final ProjectJdkEx jdk){
    if (jdk == null) {
      return null;
    }
    final String toolsJarPath = jdk.getToolsPath();
    try {
      final URL url = new URL("file:"+toolsJarPath.replace(File.separatorChar, '/'));
      ClassLoader loader = new URLClassLoader(new URL[] {url}, null);

      if (LOG.isDebugEnabled()) {
        LOG.debug("COMPILER RESOURCE BUNDLE URL = " + url);
      }

      return loader;
    }
    catch (MalformedURLException e) {
      LOG.error(e);
      return null;
    }
  }

  private static void addPattern(Collection patterns, String patternString) {
    final int length = patternString.length();
    StringBuffer buf = new StringBuffer(length);
    boolean insideParam = false;
    for (int idx = 0; idx < length; idx++ ) {
      final char ch = patternString.charAt(idx);
      if (ch == '{') {
        insideParam = true;
        continue;
      }
      if (ch == '}') {
        insideParam = false;
        buf.append(".*?");
        continue;
      }
      if (!insideParam) {
        if (ch == '\\') {
          continue; // quote character
        }
        if (ch == '\'') { // check double quotes
          if (idx + 1 < length) {
            if (patternString.charAt(idx+1) == '\'') {
              idx += 1;
              buf.append('\'');
              continue;
            }
          }
        }
        if (ch == '(' || ch == ')' || ch == '{' || ch == '}' || ch == '[' || ch == ']' || ch == '.' || ch == '*'){
          buf.append('\\');
        }
        buf.append(ch);
      }
    }
    final Pattern pattern = Pattern.compile(buf.toString());
    patterns.add(pattern);
  }
  */


}