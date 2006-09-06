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
package com.intellij.refactoring.listeners;

import com.intellij.openapi.project.Project;

/**
 * This class managers <i>refactoring listeners</i> - a way for plugin/client code to get
 * notifications that particular refactoring has done something with some piece of Java code in
 * a project.<p>
 *
 * Listening to refactoring operations works as follows:
 * <ul>
 * <li> client wishing to receive notifications registers a {@link RefactoringElementListenerProvider}
 *  with this class.
 * <li> before some <code>PsiElement</code> is subjected to a refactoring, all registered providers
 *  are asked to provide a {@link RefactoringElementListener} for that element
 * ({@link RefactoringElementListenerProvider#getListener(com.intellij.psi.PsiElement)} is invoked)
 * <li>When refactoring is completed, listeners for all refactoring subjects are notified,
 * </ul>
 */
public abstract class RefactoringListenerManager {
  /**
   * Registers a provider of listeners.
   */
  public abstract void addListenerProvider(RefactoringElementListenerProvider provider);

  /**
   * Unregisters previously registered provider of listeners.   
   */
  public abstract void removeListenerProvider(RefactoringElementListenerProvider provider);

  /**
   * Registers a listener for moving member by pull up, push down and extract super class/interface refactorings.
   * @param moveMembersListener listener to register
   */
  public abstract void addMoveMembersListener(MoveMemberListener moveMembersListener);

  /**
   * Unregisters a previously registered listener.
   * @param moveMembersListener listener to unregister
   */
  public abstract void removeMoveMembersListener(MoveMemberListener moveMembersListener);

  public static RefactoringListenerManager getInstance(Project project) {
    return project.getComponent(RefactoringListenerManager.class);
  }
}
