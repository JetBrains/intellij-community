// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml.ui;

import com.intellij.util.xml.DomElement;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;

/**
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
      public void afterCommit(final @NotNull DomUIControl control) {
        reset();
      }
    });
  }
}
