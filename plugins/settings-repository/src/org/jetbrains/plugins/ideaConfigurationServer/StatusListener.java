package org.jetbrains.plugins.ideaConfigurationServer;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

public interface StatusListener extends EventListener {
  Topic<StatusListener> TOPIC = new Topic<StatusListener>("ICS status changes", StatusListener.class);

  void statusChanged(@NotNull IcsStatus status);
}
