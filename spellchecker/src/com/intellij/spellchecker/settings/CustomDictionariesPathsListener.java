package com.intellij.spellchecker.settings;

import java.util.EventListener;
import java.util.List;

public interface CustomDictionariesPathsListener extends EventListener {
  void dictionariesChanged(List<String> paths);
}
