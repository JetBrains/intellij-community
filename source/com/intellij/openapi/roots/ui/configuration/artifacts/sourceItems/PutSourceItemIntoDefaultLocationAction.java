package com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactEditorEx;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.packaging.ui.PackagingSourceItem;

import java.util.List;
import java.util.Set;
import java.util.HashSet;

/**
 * @author nik
 */
public class PutSourceItemIntoDefaultLocationAction extends AnAction {
  private final SourceItemsTree mySourceItemsTree;
  private final ArtifactEditorEx myArtifactEditor;

  public PutSourceItemIntoDefaultLocationAction(SourceItemsTree sourceItemsTree, ArtifactEditorEx artifactEditor) {
    mySourceItemsTree = sourceItemsTree;
    myArtifactEditor = artifactEditor;
  }

  @Override
  public void update(AnActionEvent e) {
    final ArtifactType type = myArtifactEditor.getArtifact().getArtifactType();
    final List<PackagingSourceItem> items = mySourceItemsTree.getSelectedItems();
    boolean enabled = false;
    final Presentation presentation = e.getPresentation();
    if (!items.isEmpty()) {
      enabled = true;
      Set<String> paths = new HashSet<String>();
      for (PackagingSourceItem item : items) {
        final String path = type.getDefaultPathFor(item);
        if (path == null) {
          enabled = false;
          break;
        }
        paths.add(StringUtil.trimStart(StringUtil.trimEnd(path, "/"), "/"));
      }
      if (paths.size() == 1) {
        presentation.setText("Put Into /" + paths.iterator().next());
      }
      else {
        presentation.setText("Put into default locations");
      }
    }
    presentation.setVisible(enabled);
    presentation.setEnabled(enabled);
  }

  public void actionPerformed(AnActionEvent e) {
    myArtifactEditor.getLayoutTreeComponent().putIntoDefaultLocations(mySourceItemsTree.getSelectedItems());
  }
}
