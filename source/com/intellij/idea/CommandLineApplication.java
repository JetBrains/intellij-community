package com.intellij.idea;

import com.intellij.diagnostic.DialogAppender;
import com.intellij.ide.impl.DataManagerImpl;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.vfs.local.win32.FileWatcher;
import org.apache.log4j.Appender;
import org.apache.log4j.Category;
import org.jetbrains.annotations.NonNls;

import java.awt.*;
import java.util.Enumeration;

public class CommandLineApplication {
  private static final Logger LOG = Logger.getInstance("#com.intellij.idea.CommandLineApplication");
  protected static CommandLineApplication ourInstance = null;

  static {
    System.setProperty(FileWatcher.PROPERTY_WATCHER_DISABLED, Boolean.TRUE.toString());

    final Category category = Category.getRoot();
    final Enumeration enumeration = category.getAllAppenders();
    while (enumeration.hasMoreElements()) {
      Object o = enumeration.nextElement();
      if (o instanceof DialogAppender) {
        category.removeAppender((Appender)o);
        break;
      }
    }
  }

  protected CommandLineApplication() {}

  protected CommandLineApplication(boolean isInternal, boolean isUnitTestMode, @NonNls String componentsDescriptor) {
    this(isInternal, isUnitTestMode, componentsDescriptor, "idea");
  }

  protected CommandLineApplication(boolean isInternal, boolean isUnitTestMode, String componentsDescriptor, @NonNls String appName) {
    LOG.assertTrue(ourInstance == null, "Only one instance allowed.");
    ourInstance = this;
    ApplicationManagerEx.createApplication(componentsDescriptor, isInternal, isUnitTestMode, true, appName);
  }

  public Object getData(String dataId) {
    return null;
  }

  public static class MyDataManagerImpl extends DataManagerImpl {
    
    public DataContext getDataContext() {
      return new DataContext() {
        public Object getData(String dataId) {
          return ourInstance.getData(dataId);
        }
      };
    }

    public DataContext getDataContext(Component component) {
      return getDataContext();
    }

    public DataContext getDataContext(Component component, int x, int y) {
      return getDataContext();
    }
  }
}
