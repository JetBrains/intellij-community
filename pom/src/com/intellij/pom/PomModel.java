/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.pom;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.Disposable;
import com.intellij.pom.event.PomModelListener;
import com.intellij.util.IncorrectOperationException;

import java.util.Set;

public interface PomModel extends UserDataHolder, ProjectComponent {
  <T extends PomModelAspect> T getModelAspect(Class<T> aClass);

  void registerAspect(Class<? extends PomModelAspect> aClass,
                      PomModelAspect aspect,
                      Set<PomModelAspect> dependencies);

  PomProject getRoot();

  void addModelListener(PomModelListener listener);
  void addModelListener(PomModelListener listener, Disposable parentDisposable);
  void removeModelListener(PomModelListener listener);

  void runTransaction(PomTransaction transaction) throws IncorrectOperationException;
}