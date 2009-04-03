package com.intellij.openapi.util.objectTree;

public interface ObjectTreeListener {

  void objectRegistered(Object node);
  void objectExecuted(Object node);

}