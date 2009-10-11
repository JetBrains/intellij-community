/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.util.xml.ui;

import com.intellij.util.xml.DomElement;

import javax.swing.*;

/**
 * @author peter
 * 
 * @see DomUIFactory
 */
public abstract class DomUIControl<T extends DomElement> implements CommittablePanel {

  public abstract T getDomElement();

  public abstract void bind(JComponent component);

  public abstract void addCommitListener(CommitListener listener);

  public abstract void removeCommitListener(CommitListener listener);

  public abstract boolean canNavigate(DomElement element);

  public abstract void navigate(DomElement element);

  public void addDependency(final DomUIControl control) {
    control.addCommitListener(new CommitAdapter() {
      @Override
      public void afterCommit(final DomUIControl control) {
        reset();
      }
    });
  }
}
