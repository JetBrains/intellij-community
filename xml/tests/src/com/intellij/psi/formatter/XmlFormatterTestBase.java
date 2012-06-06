package com.intellij.psi.formatter;

import com.intellij.psi.impl.source.xml.XmlFileImpl;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.application.ApplicationManager;

/**
 * @author yole
 */
public abstract class XmlFormatterTestBase extends XmlFormatterTestCase {
  protected void checkFormattingDoesNotProduceException(String name) throws Exception {

    String text = loadFile(name + ".xml", null);
    final XmlFileImpl file = (XmlFileImpl)createFile(name + ".xml", text);
    myTextRange = new TextRange(10000, 10001);
    CommandProcessor.getInstance().executeCommand(getProject(), new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            performFormatting(file);
          }
        });
      }
    }, "", "");

    myTextRange = new TextRange(1000000, 1000001);
    CommandProcessor.getInstance().executeCommand(getProject(), new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            performFormatting(file);
          }
        });
      }
    }, "", "");
    myTextRange = new TextRange(0, text.length());
    CommandProcessor.getInstance().executeCommand(getProject(), new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            performFormatting(file);
          }
        });
      }
    }, "", "");
  }
}
