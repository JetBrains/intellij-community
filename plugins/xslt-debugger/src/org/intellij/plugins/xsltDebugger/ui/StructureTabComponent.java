// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.xsltDebugger.ui;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.DefaultTreeExpander;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.util.Key;
import com.intellij.ui.ScrollPaneFactory;
import org.intellij.plugins.xsltDebugger.XsltDebuggerBundle;
import org.intellij.plugins.xsltDebugger.ui.actions.HideWhitespaceAction;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class StructureTabComponent extends AbstractTabComponent {
  private static final Key<StructureTabComponent> KEY = Key.create("STRUCTURE");

  private final DefaultActionGroup myToolbarActions;
  private final JComponent myComponent;
  private final GeneratedStructureModel myEventModel;

  private StructureTabComponent(@NotNull Disposable disposable) {
    super(XsltDebuggerBundle.message("tab.title.structure"));

    myEventModel = new GeneratedStructureModel();

    final StructureTree tree = new StructureTree(myEventModel);
    myComponent = ScrollPaneFactory.createScrollPane(tree);
    myEventModel.addTreeModelListener(new SmartStructureTracker(tree, disposable));

    final DefaultActionGroup structureActions = new DefaultActionGroup();
    final DefaultTreeExpander expander = new DefaultTreeExpander(tree);
    final CommonActionsManager actionsManager = CommonActionsManager.getInstance();
    structureActions.add(new HideWhitespaceAction(tree, myEventModel));
    structureActions.add(actionsManager.createExpandAllAction(expander, tree));
    structureActions.add(actionsManager.createCollapseAllAction(expander, tree));

    myToolbarActions = structureActions;
  }

  public GeneratedStructureModel getEventModel() {
    return myEventModel;
  }

  public static StructureTabComponent create(ProcessHandler process, @NotNull Disposable disposable) {
    final StructureTabComponent component = new StructureTabComponent(disposable);
    process.putUserData(KEY, component);
    return component;
  }

  public static StructureTabComponent getInstance(ProcessHandler process) {
    return process.getUserData(KEY);
  }

  @Override
  public @NotNull JComponent getComponent() {
    return myComponent;
  }

  @Override
  public JComponent getPreferredFocusableComponent() {
    return getComponent();
  }

  @Override
  public ActionGroup getToolbarActions() {
    return myToolbarActions;
  }
}