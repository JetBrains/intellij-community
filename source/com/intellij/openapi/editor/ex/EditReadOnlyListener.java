package com.intellij.openapi.editor.ex;

import com.intellij.openapi.editor.Document;

import java.util.EventListener;

public interface EditReadOnlyListener extends EventListener {
  void readOnlyModificationAttempt(Document document);
}
