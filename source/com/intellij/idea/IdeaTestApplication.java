package com.intellij.idea;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.impl.PluginsFacade;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.InvalidDataException;

import java.io.IOException;

public class IdeaTestApplication extends CommandLineApplication {
  private DataProvider myDataContext;

  private IdeaTestApplication() {
    super(false, true, "componentSets/IdeaComponents");

    PluginsFacade.INSTANCE = new PluginsFacade() {
      public IdeaPluginDescriptor getPlugin(PluginId id) {
        return PluginManager.getPlugin(id);
      }

      public IdeaPluginDescriptor[] getPlugins() {
        return PluginManager.getPlugins();
      }
    };
  }

  public void setDataProvider(DataProvider dataContext) {
    myDataContext = dataContext;
  }

  public Object getData(String dataId) {
    return myDataContext == null ? null : myDataContext.getData(dataId);
  }



  public synchronized static IdeaTestApplication getInstance() throws IOException, InvalidDataException {
    if (ourInstance == null) {
      new IdeaTestApplication();
      PluginsFacade.INSTANCE.getPlugins(); //initialization
      ApplicationManagerEx.getApplicationEx().load(null);
    }
    return (IdeaTestApplication)ourInstance;
  }
}
