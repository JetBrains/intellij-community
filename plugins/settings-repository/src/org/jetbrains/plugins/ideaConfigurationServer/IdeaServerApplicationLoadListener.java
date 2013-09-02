package org.jetbrains.plugins.ideaConfigurationServer;

import com.intellij.ide.ApplicationLoadListener;
import com.intellij.openapi.application.Application;


public class IdeaServerApplicationLoadListener implements ApplicationLoadListener {

  public IdeaServerApplicationLoadListener() {
  }

  public void beforeApplicationLoaded(final Application application) {
    if (!application.isUnitTestMode()) {
      IdeaServerManagerImpl.getInstance().registerApplicationLevelProviders(application);
    }
  }
}
