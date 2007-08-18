/**
 * @author cdr
 */
package com.intellij.ide.projectView.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MoveModuleToGroup extends ActionGroup {
  private final ModuleGroup myModuleGroup;

  public MoveModuleToGroup(ModuleGroup moduleGroup) {
    myModuleGroup = moduleGroup;
    setPopup(true);
  }

  public void update(AnActionEvent e){
    final DataContext dataContext = e.getDataContext();
    final Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    final Module[] modules = (Module[])dataContext.getData(DataConstants.MODULE_CONTEXT_ARRAY);
    boolean active = project != null && modules != null && modules.length != 0;
    final Presentation presentation = e.getPresentation();
    presentation.setVisible(active);
    presentation.setText(myModuleGroup.presentableText());
  }

  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    if (e == null) return AnAction.EMPTY_ARRAY;

    List<ModuleGroup> children = new ArrayList<ModuleGroup>(myModuleGroup.childGroups(e.getDataContext()));
    Collections.sort ( children, new Comparator<ModuleGroup>() {
      public int compare(final ModuleGroup moduleGroup1, final ModuleGroup moduleGroup2) {
        assert moduleGroup1.getGroupPath().length == moduleGroup2.getGroupPath().length;
        return moduleGroup1.toString().compareToIgnoreCase(moduleGroup2.toString());
      }
    });

    List<AnAction> result = new ArrayList<AnAction>();
    result.add(new MoveModulesToGroupAction(myModuleGroup, IdeBundle.message("action.move.module.to.this.group")));
    result.add(new MoveModulesToSubGroupAction(myModuleGroup));
     result.add(Separator.getInstance());
    for (final ModuleGroup child : children) {
      result.add(new MoveModuleToGroup(child));
    }

    return result.toArray(new AnAction[result.size()]);
  }
}
