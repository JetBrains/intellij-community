package com.intellij.pom;

import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.pom.event.PomModelListener;
import com.intellij.util.IncorrectOperationException;

import java.util.Set;

public interface PomModel extends UserDataHolder, ProjectComponent {
  <T extends PomModelAspect> T getModelAspect(Class<T> aClass);

  void registerAspect(PomModelAspect aspect,
                      Set<PomModelAspect> dependencies);

  PomProject getRoot();

  void addModelListener(PomModelListener listener);
  void removeModelListener(PomModelListener listener);

  void runTransaction(PomTransaction transaction, PomModelAspect aspect) throws IncorrectOperationException;
}