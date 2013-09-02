package org.jetbrains.plugins.ideaConfigurationServer;

import java.util.EventListener;

public interface StatusListener extends EventListener {
  void statusChanged(IdeaServerStatus status);
}
