// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.javaee;

import com.intellij.util.messages.Topic;

public interface ExternalResourceListener {
  @Topic.AppLevel
  Topic<ExternalResourceListener> TOPIC =
    new Topic<>("ExternalResourceListener", ExternalResourceListener.class, Topic.BroadcastDirection.TO_DIRECT_CHILDREN);

  void externalResourceChanged();
}
