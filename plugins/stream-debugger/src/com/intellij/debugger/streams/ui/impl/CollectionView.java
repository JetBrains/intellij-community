// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.ui.impl;

import com.intellij.debugger.streams.trace.TraceElement;
import com.intellij.debugger.streams.ui.TraceContainer;
import com.intellij.debugger.streams.ui.ValuesSelectionListener;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public class CollectionView extends JPanel implements TraceContainer {
  private final CollectionTree myInstancesTree;

  CollectionView(@NotNull String header,
                 @NotNull CollectionTree collectionTree) {
    super(new BorderLayout());
    add(new JBLabel(header), BorderLayout.NORTH);

    myInstancesTree = collectionTree;

    final JBScrollPane scroll = new JBScrollPane(myInstancesTree);

    add(scroll, BorderLayout.CENTER);
    Disposer.register(this, myInstancesTree);
  }

  @Override
  public void dispose() {
  }

  @Override
  public void highlight(@NotNull List<TraceElement> elements) {
    myInstancesTree.highlight(elements);
  }

  @Override
  public void select(@NotNull List<TraceElement> elements) {
    myInstancesTree.select(elements);
  }

  @Override
  public void addSelectionListener(@NotNull ValuesSelectionListener listener) {
    myInstancesTree.addSelectionListener(listener);
  }

  @Override
  public boolean highlightedExists() {
    return myInstancesTree.highlightedExists();
  }

  @NotNull
  protected CollectionTree getInstancesTree() {
    return myInstancesTree;
  }
}
