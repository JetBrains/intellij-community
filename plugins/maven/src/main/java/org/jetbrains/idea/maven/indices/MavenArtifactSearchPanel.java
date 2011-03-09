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

import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Alarm;
import com.intellij.util.ui.AbstractLayoutManager;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenArtifactInfo;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.utils.MavenIcons;
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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

public class MavenArtifactSearchPanel extends JPanel {
  private final Project myProject;
  private final MavenArtifactSearchDialog myDialog;
  private final boolean myClassMode;
  private final Listener myListener;

  private JTextField mySearchField;
  private Tree myResultList;

  private final Alarm myAlarm;

  public MavenArtifactSearchPanel(Project project,
                                  String initialText,
                                  boolean classMode,
                                  Listener listener,
                                  MavenArtifactSearchDialog dialog) {
    myProject = project;
    myDialog = dialog;
    myClassMode = classMode;
    myListener = listener;

    initComponents(initialText);
    myAlarm = new Alarm(Alarm.ThreadToUse.OWN_THREAD, dialog.getDisposable());
  }

  public JTextField getSearchField() {
    return mySearchField;
  }

  private void initComponents(String initialText) {
    mySearchField = new JTextField(initialText);
    myResultList = new Tree();
    myResultList.getExpandableItemsHandler().setEnabled(false);
    myResultList.getEmptyText().setText("Loading...");
    myResultList.setRootVisible(false);
    myResultList.setShowsRootHandles(true);
    myResultList.setModel(null);
    MyArtifactCellRenderer renderer = myClassMode ? new MyClassCellRenderer(myResultList)
                                                  : new MyArtifactCellRenderer(myResultList);
    myResultList.setCellRenderer(renderer);
    myResultList.setRowHeight(renderer.getPreferredSize().height);

    setLayout(new BorderLayout());
    add(mySearchField, BorderLayout.NORTH);
    JScrollPane pane = ScrollPaneFactory.createScrollPane(myResultList);
    pane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    add(pane, BorderLayout.CENTER);

    mySearchField.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        scheduleSearch();
      }
    });

    myResultList.addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        if (myAlarm.getActiveRequestCount() > 0) return;

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

    myResultList.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2) {
          Object sel = myResultList.getLastSelectedPathComponent();
          if (sel != null && myResultList.getModel().isLeaf(sel)) {
            myListener.itemSelected();
            e.consume();
          }
        }
      }
    });
  }

  public void scheduleSearch() {
    myListener.canSelectStateChanged(this, false);
    myResultList.setPaintBusy(true);

    // evaluate text value in the swing thread
    final String text = mySearchField.getText();

    myAlarm.cancelAllRequests();
    myAlarm.addRequest(new Runnable() {
        public void run() {
          try {
            doSearch(text);
          }
          catch (Throwable e) {
            MavenLog.LOG.warn(e);
          }
        }
      }, 500);
  }

  private void doSearch(String searchText) {
    MavenSearcher searcher = myClassMode ? new MavenClassSearcher() : new MavenArtifactSearcher();
    List<MavenArtifactSearchResult> result = searcher.search(myProject, searchText, 200);
    final TreeModel model = new MyTreeModel(result);

    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        if (!myDialog.isVisible()) return;

        myResultList.getEmptyText().setText("No results");
        myResultList.setModel(model);
        myResultList.setSelectionRow(0);
        myResultList.setPaintBusy(false);
      }
    });
  }

  @NotNull
  public List<MavenId> getResult() {
    List<MavenId> result = new ArrayList<MavenId>();

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

    public Object getRoot() {
      return myItems;
    }

    public Object getChild(Object parent, int index) {
      return getList(parent).get(index);
    }

    public int getChildCount(Object parent) {
      return getList(parent).size();
    }

    public List getList(Object parent) {
      if (parent == myItems) return myItems;
      if (parent instanceof MavenArtifactSearchResult) return ((MavenArtifactSearchResult)parent).versions;
      return null;
    }

    public boolean isLeaf(Object node) {
      return node != myItems && (getList(node) == null || getChildCount(node) < 2);
    }

    public int getIndexOfChild(Object parent, Object child) {
      return getList(parent).indexOf(child);
    }

    public void valueForPathChanged(TreePath path, Object newValue) {
    }

    public void addTreeModelListener(TreeModelListener l) {
    }

    public void removeTreeModelListener(TreeModelListener l) {
    }
  }

  private static class MyArtifactCellRenderer extends JPanel implements TreeCellRenderer {
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

          Container parent = tree.getParent();
          if (parent != null) {
            Container parentParent = parent.getParent();
            if (parentParent instanceof JScrollPane) {
              JScrollBar sb = ((JScrollPane)parentParent).getVerticalScrollBar();
              if (sb != null) {
                w -= sb.getWidth();
              }
            }
          }
          return w;
        }
      });
    }

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
        myLeftComponent.append(info.getVersion(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }

      return this;
    }

    protected void formatSearchResult(JTree tree, MavenArtifactSearchResult searchResult, boolean selected) {
      MavenArtifactInfo info = searchResult.versions.get(0);
      myLeftComponent.setIcon(MavenIcons.DEPENDENCY_ICON);
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

  private static class MyClassCellRenderer extends MyArtifactCellRenderer {
    public static final Icon CLASS_ICON = IconLoader.getIcon("/nodes/class.png");

    private MyClassCellRenderer(Tree tree) {
      super(tree);
    }

    @Override
    protected void formatSearchResult(JTree tree, MavenArtifactSearchResult searchResult, boolean selected) {
      MavenClassSearchResult classResult = (MavenClassSearchResult)searchResult;
      MavenArtifactInfo info = searchResult.versions.get(0);

      myLeftComponent.setIcon(CLASS_ICON);
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
