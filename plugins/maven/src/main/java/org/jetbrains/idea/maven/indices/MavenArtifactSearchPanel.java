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
package org.jetbrains.idea.maven.indices;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.*;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Alarm;
import com.intellij.util.ui.AbstractLayoutManager;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenArtifactInfo;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.utils.MavenLog;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

public class MavenArtifactSearchPanel extends JPanel {

  private static final int MAX_RESULT = 1000;
  private final Project myProject;
  private final MavenArtifactSearchDialog myDialog;
  private final boolean myClassMode;
  private final Listener myListener;

  private JTextField mySearchField;
  private Tree myResultList;

  private final Alarm myAlarm;

  private final Map<Pair<String, String>, String> myManagedDependenciesMap;

  public MavenArtifactSearchPanel(Project project,
                                  String initialText,
                                  boolean classMode,
                                  Listener listener,
                                  MavenArtifactSearchDialog dialog, Map<Pair<String, String>, String> managedDependenciesMap) {
    myProject = project;
    myDialog = dialog;
    myClassMode = classMode;
    myListener = listener;
    myManagedDependenciesMap = managedDependenciesMap;

    initComponents(initialText);
    myAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, dialog.getDisposable());
  }

  public JTextField getSearchField() {
    return mySearchField;
  }

  private void initComponents(String initialText) {
    myResultList = new Tree();
    myResultList.setExpandableItemsEnabled(false);
    myResultList.getEmptyText().setText("Loading...");
    myResultList.setRootVisible(false);
    myResultList.setShowsRootHandles(true);
    myResultList.setModel(null);
    MyArtifactCellRenderer renderer = myClassMode ? new MyClassCellRenderer(myResultList)
                                                  : new MyArtifactCellRenderer(myResultList);
    myResultList.setCellRenderer(renderer);
    myResultList.setRowHeight(renderer.getPreferredSize().height);

    mySearchField = new JTextField(initialText);
    mySearchField.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        int d;
        if (e.getKeyCode() == KeyEvent.VK_DOWN) {
          d = 1;
        }
        else if (e.getKeyCode() == KeyEvent.VK_UP) {
          d = -1;
        }
        else {
          return;
        }

        int row = myResultList.getSelectionModel().getLeadSelectionRow();
        row += d;

        if (row >=0 && row < myResultList.getRowCount()) {
          myResultList.setSelectionRow(row);
        }
      }
    });

    setLayout(new BorderLayout());
    add(mySearchField, BorderLayout.NORTH);
    JScrollPane pane = ScrollPaneFactory.createScrollPane(myResultList);
    pane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    pane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS); // Don't remove this line.
                                                                                    // Without VERTICAL_SCROLLBAR_ALWAYS policy our custom layout
                                                                                    // works incorrectly, see https://youtrack.jetbrains.com/issue/IDEA-72986

    add(pane, BorderLayout.CENTER);

    mySearchField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        scheduleSearch();
      }
    });

    myResultList.addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        if (!myAlarm.isEmpty()) return;

        boolean hasSelection = !myResultList.isSelectionEmpty();
        myListener.canSelectStateChanged(MavenArtifactSearchPanel.this, hasSelection);
      }
    });

    myResultList.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER && myResultList.getLastSelectedPathComponent() != null) {
          myListener.itemSelected();
          e.consume();
        }
      }
    });

    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(MouseEvent e) {
        final TreePath path = myResultList.getPathForLocation(e.getX(), e.getY());
        if (path != null && myResultList.isPathSelected(path)) {
          Object sel = path.getLastPathComponent();
          if (sel != null && myResultList.getModel().isLeaf(sel)) {
            myListener.itemSelected();
            return true;
          }
        }
        return false;
      }
    }.installOn(myResultList);
  }

  public void scheduleSearch() {
    myListener.canSelectStateChanged(this, false);
    myResultList.setPaintBusy(true);

    // evaluate text value in the swing thread
    final String text = mySearchField.getText();

    myAlarm.cancelAllRequests();
    myAlarm.addRequest(() -> {
      try {
        doSearch(text);
      }
      catch (Throwable e) {
        MavenLog.LOG.warn(e);
      }
    }, 500);
  }

  private void resortUsingDependencyVersionMap(List<MavenArtifactSearchResult> result) {
    for (MavenArtifactSearchResult searchResult : result) {
      if (searchResult.versions.isEmpty()) continue;

      MavenArtifactInfo artifactInfo = searchResult.versions.get(0);
      final String managedVersion = myManagedDependenciesMap.get(Pair.create(artifactInfo.getGroupId(), artifactInfo.getArtifactId()));
      if (managedVersion != null) {
        Collections.sort(searchResult.versions, (o1, o2) -> {
          String v1 = o1.getVersion();
          String v2 = o2.getVersion();
          if (Comparing.equal(v1, v2)) return 0;
          if (managedVersion.equals(v1)) return -1;
          if (managedVersion.equals(v2)) return 1;
          return 0;
        });
      }
    }
  }

  private void doSearch(String searchText) {
    MavenSearcher searcher = myClassMode ? new MavenClassSearcher() : new MavenArtifactSearcher();
    List<MavenArtifactSearchResult> result = searcher.search(myProject, searchText, MAX_RESULT);

    resortUsingDependencyVersionMap(result);

    final TreeModel model = new MyTreeModel(result);

    SwingUtilities.invokeLater(() -> {
      if (!myDialog.isVisible()) return;

      myResultList.getEmptyText().setText("No results");
      myResultList.setModel(model);
      myResultList.setSelectionRow(0);
      myResultList.setPaintBusy(false);
    });
  }

  @NotNull
  public List<MavenId> getResult() {
    List<MavenId> result = new ArrayList<>();

    for (TreePath each : myResultList.getSelectionPaths()) {
      Object sel = each.getLastPathComponent();
      MavenArtifactInfo info;
      if (sel instanceof MavenArtifactInfo) {
        info = (MavenArtifactInfo)sel;
      }
      else {
        info = ((MavenArtifactSearchResult)sel).versions.get(0);
      }
      result.add(new MavenId(info.getGroupId(), info.getArtifactId(), info.getVersion()));
    }

    return result;
  }

  private static class MyTreeModel implements TreeModel {
    List<? extends MavenArtifactSearchResult> myItems;

    private MyTreeModel(List<? extends MavenArtifactSearchResult> items) {
      myItems = items;
    }

    @Override
    public Object getRoot() {
      return myItems;
    }

    @Override
    public Object getChild(Object parent, int index) {
      return getList(parent).get(index);
    }

    @Override
    public int getChildCount(Object parent) {
      List list = getList(parent);
      assert list != null : parent;
      return list.size();
    }

    public List getList(Object parent) {
      if (parent == myItems) return myItems;
      if (parent instanceof MavenArtifactSearchResult) return ((MavenArtifactSearchResult)parent).versions;
      return null;
    }

    @Override
    public boolean isLeaf(Object node) {
      return node != myItems && (getList(node) == null || getChildCount(node) < 2);
    }

    @Override
    public int getIndexOfChild(Object parent, Object child) {
      return getList(parent).indexOf(child);
    }

    @Override
    public void valueForPathChanged(TreePath path, Object newValue) {
    }

    @Override
    public void addTreeModelListener(TreeModelListener l) {
    }

    @Override
    public void removeTreeModelListener(TreeModelListener l) {
    }
  }

  private class MyArtifactCellRenderer extends JPanel implements TreeCellRenderer {
    protected SimpleColoredComponent myLeftComponent = new SimpleColoredComponent();
    protected SimpleColoredComponent myRightComponent = new SimpleColoredComponent();

    private MyArtifactCellRenderer(final Tree tree) {
      myLeftComponent.setOpaque(false);
      myRightComponent.setOpaque(false);
      myLeftComponent.setIconOpaque(false);
      myRightComponent.setIconOpaque(false);
      add(myLeftComponent);
      add(myRightComponent);

      Font font = EditorColorsManager.getInstance().getGlobalScheme().getFont(EditorFontType.PLAIN);
      myLeftComponent.setFont(font);
      myRightComponent.setFont(font);

      setPreferredSize(new Dimension(2000, myLeftComponent.getPreferredSize().height));

      setLayout(new AbstractLayoutManager() {
        @Override
        public Dimension preferredLayoutSize(Container parent) {
          return new Dimension(getVisibleWidth(), myLeftComponent.getPreferredSize().height);
        }

        @Override
        public void layoutContainer(Container parent) {
          int w = getVisibleWidth();

          Dimension ls = myLeftComponent.getPreferredSize();
          Dimension rs = myRightComponent.getPreferredSize();

          int lw = w - rs.width - 10;
          int rw = rs.width;

          myLeftComponent.setBounds(0, 0, lw, ls.height);
          myRightComponent.setBounds(w - rw, 0, rw, rs.height);
        }

        private int getVisibleWidth() {
          int w = tree.getVisibleRect().width - 10;
          Insets insets = tree.getInsets();
          w -= insets.left + insets.right;

          JScrollPane scrollPane = JBScrollPane.findScrollPane(tree);
          if (scrollPane != null) {
            JScrollBar sb = scrollPane.getVerticalScrollBar();
            if (sb != null) {
              w -= sb.getWidth();
            }
          }
          return w;
        }
      });
    }

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row,
                                                  boolean hasFocus) {
      myLeftComponent.clear();
      myRightComponent.clear();

      setBackground(selected ? UIUtil.getTreeSelectionBackground() : tree.getBackground());

      myLeftComponent.setForeground(selected ? UIUtil.getTreeSelectionForeground() : null);
      myRightComponent.setForeground(selected ? UIUtil.getTreeSelectionForeground() : null);

      if (value == tree.getModel().getRoot()) {
        myLeftComponent.append("Results", SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
      else if (value instanceof MavenArtifactSearchResult) {
        formatSearchResult(tree, (MavenArtifactSearchResult)value, selected);
      }
      else if (value instanceof MavenArtifactInfo) {
        MavenArtifactInfo info = (MavenArtifactInfo)value;
        String version = info.getVersion();

        String managedVersion = myManagedDependenciesMap.get(Pair.create(info.getGroupId(), info.getArtifactId()));

        if (managedVersion != null && managedVersion.equals(version)) {
          myLeftComponent.append(version, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
          myLeftComponent.append(" (from <dependencyManagement>)", SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }
        else {
          myLeftComponent.append(version, SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
      }

      return this;
    }

    protected void formatSearchResult(JTree tree, MavenArtifactSearchResult searchResult, boolean selected) {
      MavenArtifactInfo info = searchResult.versions.get(0);
      myLeftComponent.setIcon(AllIcons.Nodes.PpLib);
      appendArtifactInfo(myLeftComponent, info, selected);
    }

    protected void appendArtifactInfo(SimpleColoredComponent component, MavenArtifactInfo info, boolean selected) {
      component.append(info.getGroupId() + ":", getGrayAttributes(selected));
      component.append(info.getArtifactId(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      component.append(":" + info.getVersion(), getGrayAttributes(selected));
    }

    protected SimpleTextAttributes getGrayAttributes(boolean selected) {
      return !selected ? SimpleTextAttributes.GRAY_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES;
    }
  }

  private class MyClassCellRenderer extends MyArtifactCellRenderer {

    private MyClassCellRenderer(Tree tree) {
      super(tree);
    }

    @Override
    protected void formatSearchResult(JTree tree, MavenArtifactSearchResult searchResult, boolean selected) {
      MavenClassSearchResult classResult = (MavenClassSearchResult)searchResult;
      MavenArtifactInfo info = searchResult.versions.get(0);

      myLeftComponent.setIcon(AllIcons.Nodes.Class);
      myLeftComponent.append(classResult.className, SimpleTextAttributes.REGULAR_ATTRIBUTES);
      myLeftComponent.append(" (" + classResult.packageName + ")", getGrayAttributes(selected));

      appendArtifactInfo(myRightComponent, info, selected);
    }
  }

  public interface Listener {
    void itemSelected();

    void canSelectStateChanged(MavenArtifactSearchPanel from, boolean canSelect);
  }
}
