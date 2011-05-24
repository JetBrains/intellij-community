package org.intellij.plugins.xsltDebugger.ui;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.ide.CommonActionsManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.util.Key;
import com.intellij.ui.treeStructure.Tree;
import org.intellij.plugins.xsltDebugger.ui.actions.HideWhitespaceAction;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class StructureTabComponent extends AbstractTabComponent {
  private static final Key<StructureTabComponent> KEY = Key.create("STRUCTURE");

  private final DefaultActionGroup myToolbarActions;
  private final Tree myStructureTree;
  private final GeneratedStructureModel myEventModel;

  private StructureTabComponent(Disposable disposable) {
    super("Structure");

    myEventModel = new GeneratedStructureModel();
    myStructureTree = new StructureTree(myEventModel);
    myEventModel.addTreeModelListener(new SmartStructureTracker(myStructureTree, disposable));

    final DefaultActionGroup structureActions = new DefaultActionGroup();
    final StructureTreeExpander expander = new StructureTreeExpander(myStructureTree);
    final CommonActionsManager actionsManager = CommonActionsManager.getInstance();
    structureActions.add(new HideWhitespaceAction(myStructureTree, myEventModel));
    structureActions.add(actionsManager.createExpandAllAction(expander, myStructureTree));
    structureActions.add(actionsManager.createCollapseAllAction(expander, myStructureTree));

    myToolbarActions = structureActions;
  }

  public GeneratedStructureModel getEventModel() {
    return myEventModel;
  }

  public static StructureTabComponent create(ProcessHandler process, Disposable disposable) {
    final StructureTabComponent component = new StructureTabComponent(disposable);
    process.putUserData(KEY, component);
    return component;
  }

  public static StructureTabComponent getInstance(ProcessHandler process) {
    return process.getUserData(KEY);
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myStructureTree;
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