package com.intellij.compiler;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.util.text.StringUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.StringTokenizer;

public class JikesOutputParser extends OutputParser {
  private JikesSettings myJikesSettings;

  public JikesOutputParser(Project project) {
    myJikesSettings = JikesSettings.getInstance(project);
    myParserActions.add(new ParserActionJikes());
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public boolean processMessageLine(Callback callback) {
    if (super.processMessageLine(callback)) {
      return true;
    }
    String line = callback.getCurrentLine();
    if (line == null) {
      return false;
    }
    if (line.length() == 0) {
      return false;
    }
//sae
    if (myJikesSettings.IS_EMACS_ERRORS_MODE) {
      int colNum;
      int lineNum;

      String filePath = "";
      if (line.indexOf(".java:") > 5) filePath = line.substring(0, line.indexOf(".java:") + 5);
      filePath = filePath.replace(File.separatorChar, '/');
      final String url = VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, filePath);
      final VirtualFile[] file = new VirtualFile[1];
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          file[0] = VirtualFileManager.getInstance().findFileByUrl(url);
        }
      });
      if (line.indexOf(".java:") > 6) {
        line = line.substring(line.indexOf(".java:") + 6);

//second token = start line
        StringTokenizer tokenizer = new StringTokenizer(line, ":");
//first token = filename
        String token = tokenizer.nextToken();

        try {
          lineNum = Integer.parseInt(token);
        }
        catch (Exception e) {
          addMessage(callback, CompilerMessageCategory.INFORMATION, line);
          return true;
        }
//thrd token = start column
        token = tokenizer.nextToken();
        try {
          colNum = Integer.parseInt(token);
        }
        catch (Exception e) {
          addMessage(callback, CompilerMessageCategory.INFORMATION, line);
          return true;
        }
//4,5 token = end line/column   tmp not used
        tokenizer.nextToken();
        tokenizer.nextToken();
// 6 error type
        CompilerMessageCategory category = CompilerMessageCategory.INFORMATION;
        ArrayList messages = new ArrayList();
        String message;
        token = tokenizer.nextToken().trim();
        if ("Caution".equalsIgnoreCase(token)) {
          category = CompilerMessageCategory.WARNING;
        }
        else if ("Warning".equalsIgnoreCase(token) || "Semantic Warning".equalsIgnoreCase(token)) { // Semantic errors/warnings were introduced in jikes 1.18
          category = CompilerMessageCategory.WARNING;
        }
        else if ("Error".equalsIgnoreCase(token) || "Semantic Error".equalsIgnoreCase(token)) {
          category = CompilerMessageCategory.ERROR;
        }

        message = token;
        message = message.concat("  ");
        message = message.concat(tokenizer.nextToken(""));
        messages.add(message);

        if (colNum > 0 && messages.size() > 0) {
          StringBuffer buf = new StringBuffer();
          for (Iterator it = messages.iterator(); it.hasNext();) {
            String m = (String)it.next();
            if (buf.length() > 0) {
              buf.append("\n");
            }
            buf.append(m);
          }
          addMessage(callback, category, buf.toString(), url, lineNum, colNum);
          return true;
        }
      }
    }
//--sae

//Enter to continue
    if (!(line.matches(".*Enter\\s+to\\s+continue.*"))) {
      addMessage(callback, CompilerMessageCategory.INFORMATION, line);
    }
    return true;
  }
}