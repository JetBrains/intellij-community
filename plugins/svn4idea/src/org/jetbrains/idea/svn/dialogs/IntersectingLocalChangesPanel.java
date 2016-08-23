/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.dialogs;

import com.intellij.ide.actions.EditSourceAction;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.openapi.util.BooleanGetter;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNodeRenderer;
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.pom.Navigatable;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.ContentsUtil;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.EditSourceOnEnterKeyHandler;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class IntersectingLocalChangesPanel {
  private final JPanel myPanel;
  private final String myText;
  private final List<FilePath> myFilesToShow;
  private final Project myProject;
  private JTree myJTree;

  public IntersectingLocalChangesPanel(final Project project, final List<FilePath> filesToShow, String text) {
    myProject = project;
    myText = text;
    myPanel = new MyPanel(new BorderLayout());
    myFilesToShow = filesToShow;
    initUI();
  }

  private class MyPanel extends JPanel implements TypeSafeDataProvider {
    private MyPanel(LayoutManager layout) {
      super(layout);
    }

    public void calcData(DataKey key, DataSink sink) {
      if (CommonDataKeys.NAVIGATABLE_ARRAY.equals(key)) {
        final TreePath[] treePaths = myJTree.getSelectionModel().getSelectionPaths();
        final List<Navigatable> navigatables = new ArrayList<>(treePaths.length);
        for (TreePath treePath : treePaths) {
          final List<FilePath> filePaths = ((ChangesBrowserNode)treePath.getLastPathComponent()).getAllFilePathsUnder();
          for (FilePath filePath : filePaths) {
            if (filePath.getVirtualFile() != null) {
              navigatables.add(new OpenFileDescriptor(myProject, filePath.getVirtualFile()));
            }
          }
        }
        sink.put(key, navigatables.toArray(new Navigatable[navigatables.size()]));
      }
    }
  }

  public JPanel getPanel() {
    return myPanel;
  }

  private void initUI() {
    final DefaultMutableTreeNode root = new DefaultMutableTreeNode();

    myJTree = new JTree(root);
    myJTree.setRootVisible(false);
    myJTree.setShowsRootHandles(false);
    myJTree.setCellRenderer(new ChangesBrowserNodeRenderer(myProject, BooleanGetter.TRUE, false));

    TreeModelBuilder builder = new TreeModelBuilder(myProject, true);
    final DefaultTreeModel treeModel = builder.buildModelFromFilePaths(myFilesToShow);
    myJTree.setModel(treeModel);

    myJTree.expandPath(new TreePath(root.getPath()));

    final JLabel label = new JLabel(myText) {
      @Override
      public Dimension getPreferredSize() {
        final Dimension superValue = super.getPreferredSize();
        return new Dimension((int) superValue.getWidth(), (int) (superValue.getHeight() * 1.7));
      }
    };
    label.setUI(new MultiLineLabelUI());
    label.setBackground(UIUtil.getTextFieldBackground());
    label.setVerticalTextPosition(JLabel.TOP);
    myPanel.setBackground(UIUtil.getTextFieldBackground());
    myPanel.add(label, BorderLayout.NORTH);
    myPanel.add(myJTree, BorderLayout.CENTER);

    EditSourceOnDoubleClickHandler.install(myJTree);
    EditSourceOnEnterKeyHandler.install(myJTree);
    
    final EditSourceAction editSourceAction = new EditSourceAction();
    editSourceAction.registerCustomShortcutSet(CommonShortcuts.getEditSource(), myPanel);
  }

  private Component getPrefferedFocusComponent() {
    return myJTree;
  }

  public static void showInVersionControlToolWindow(final Project project, final String title, final List<FilePath> filesToShow,
                                                    final String prompt) {
    final IntersectingLocalChangesPanel component = new IntersectingLocalChangesPanel(project, filesToShow, prompt);

    ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.VCS);
    final ContentManager contentManager = toolWindow.getContentManager();

    Content content = ContentFactory
      .SERVICE.getInstance().createContent(component.getPanel(), title, true);
    ContentsUtil.addContent(contentManager, content, true);
    toolWindow.activate(new Runnable() {
      public void run() {
        IdeFocusManager.getInstance(project).requestFocus(component.getPrefferedFocusComponent(), true);
      }
    });

  }
}
