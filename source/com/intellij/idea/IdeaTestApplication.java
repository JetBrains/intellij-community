package com.intellij.idea;

import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
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
    }
    return (IdeaTestApplication)ourInstance;
  }
}
