// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.engine;

import com.intellij.util.messages.Topic;

//used in Rider
public interface SpellCheckerEngineListener {

  @Topic.ProjectLevel
  Topic<SpellCheckerEngineListener> TOPIC = new Topic<>(SpellCheckerEngineListener.class, Topic.BroadcastDirection.TO_PARENT);

  void onSpellerInitialized();
}
