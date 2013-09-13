package org.jetbrains.plugins.ideaConfigurationServer;

import com.intellij.util.messages.Topic;

import java.util.EventListener;

public interface StatusListener extends EventListener {
  Topic<StatusListener> TOPIC = new Topic<StatusListener>("ICS status changes", StatusListener.class);

  void statusChanged(IcsStatus status);
}
