package com.intellij.idea;

import com.intellij.ExtensionPoints;
import com.intellij.execution.JUnitPatcher;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.InvalidDataException;

import java.io.IOException;

public class IdeaTestApplication extends CommandLineApplication {
  private DataProvider myDataContext;

  private IdeaTestApplication() {
    super(false, true, "componentSets/IdeaTestComponents");
  }

  public void setDataProvider(DataProvider dataContext) {
    myDataContext = dataContext;
  }

  public Object getData(String dataId) {
    return myDataContext == null ? null : myDataContext.getData(dataId);
  }

  public synchronized static IdeaTestApplication getInstance() throws IOException, InvalidDataException {
    if (ourInstance == null) {
      //Logger.setFactory(LoggerFactory.getInstance());
      new IdeaTestApplication();
      ApplicationManagerEx.getApplicationEx().load(null);
      Extensions.registerAreaClass("IDEA_PROJECT", null);
      Extensions.registerAreaClass("IDEA_MODULE", "IDEA_PROJECT");
      Extensions.getRootArea().registerExtensionPoint(ExtensionPoints.JUNIT_PATCHER, JUnitPatcher.class.getName());
    }
    return (IdeaTestApplication)ourInstance;
  }
}
