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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Alarm;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

public class MavenArtifactSearchPanel extends JPanel {
  private final Project myProject;
  private final boolean myClassMode;
  private final Listener myListener;

  private JTextField mySearchField;
  private Tree myResultList;

  private final Alarm myAlarm;

  public MavenArtifactSearchPanel(Project project, String initialText, boolean classMode, Listener listener, Disposable parent) {
    myProject = project;
    myClassMode = classMode;
    myListener = listener;

    initComponents(initialText);
    myAlarm = new Alarm(Alarm.ThreadToUse.OWN_THREAD, parent);
  }

  public JTextField getSearchField() {
    return mySearchField;
  }

  private void initComponents(String initialText) {
    mySearchField = new JTextField(initialText);
    myResultList = new Tree();

    setLayout(new BorderLayout());
    add(mySearchField, BorderLayout.NORTH);
    add(new JScrollPane(myResultList), BorderLayout.CENTER);

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

    mySearchField.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        final Object action = getAction(e, myResultList);
        if ("selectNext".equals(action)) {
          TreeUtil.moveDown(myResultList);
        }
        else if ("selectPrevious".equals(action)) {
          TreeUtil.moveUp(myResultList);
        }
        else if ("scrollUpChangeSelection".equals(action)) {
          TreeUtil.movePageUp(myResultList);
        }
        else if ("scrollDownChangeSelection".equals(action)) {
          TreeUtil.movePageDown(myResultList);
        }
      }

      private Object getAction(final KeyEvent e, final JComponent comp) {
        final KeyStroke stroke = KeyStroke.getKeyStroke(e.getKeyCode(), e.getModifiers());
        return comp.getInputMap().get(stroke);
      }
    });

    myResultList.setRootVisible(false);
    myResultList.setShowsRootHandles(true);
    myResultList.setModel(null);
    myResultList.setFocusable(false);
    myResultList.setCellRenderer(myClassMode ? new MyClassCellRenderer() : new MyArtifactCellRenderer());

    myResultList.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2) {
          Object sel = myResultList.getLastSelectedPathComponent();
          if (sel != null && myResultList.getModel().isLeaf(sel)) {
            myListener.doubleClicked();
          }
        }

        if (!mySearchField.hasFocus()) {
          mySearchField.requestFocus();
        }
      }
    });
  }

  public void scheduleSearch() {
    myListener.canSelectStateChanged(this, false);

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
        if (myProject.isDisposed()) return;
        myResultList.setModel(model);
        myResultList.setSelectionRow(0);
      }
    });
  }

  public MavenId getResult() {
    Object sel = myResultList.getLastSelectedPathComponent();
    MavenArtifactInfo info;
    if (sel instanceof MavenArtifactInfo) {
      info = (MavenArtifactInfo)sel;
    }
    else {
      info = ((MavenArtifactSearchResult)sel).versions.get(0);
    }

    return new MavenId(info.getGroupId(), info.getArtifactId(), info.getVersion());
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
      return getList(node) == null;
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

    private MyArtifactCellRenderer() {
      setLayout(new BorderLayout());
    }

    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row,
                                                  boolean hasFocus) {
      myLeftComponent.clear();
      myRightComponent.clear();

      if (UIUtil.isUnderQuaquaLookAndFeel()) {
        setBackground(selected ? UIUtil.getTreeSelectionBackground() : null);
      }
      else {
        if (selected) {
          setBackground(UIUtil.getTreeSelectionBackground());
          setForeground(UIUtil.getTreeSelectionForeground());
        }
        else {
          setBackground(null);
          setForeground(tree.getForeground());
        }
      }

      if (getFont() == null) setFont(tree.getFont());

      if (value == tree.getModel().getRoot()) {
        myLeftComponent.append("Results", SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
      else if (value instanceof MavenArtifactSearchResult) {
        formatSearchResult(tree, (MavenArtifactSearchResult)value);
      }
      else if (value instanceof MavenArtifactInfo) {
        MavenArtifactInfo info = (MavenArtifactInfo)value;
        myLeftComponent.append(info.getGroupId() + ":" + info.getArtifactId() + ":" + info.getVersion(),
                               SimpleTextAttributes.GRAY_ATTRIBUTES);
      }

      removeAll();
      add(myLeftComponent, BorderLayout.WEST);
      JPanel spacer = new JPanel();
      spacer.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
      spacer.setBackground(selected ? UIUtil.getTreeSelectionBackground() : tree.getBackground());
      add(spacer, BorderLayout.CENTER);
      add(myRightComponent, BorderLayout.EAST);
      return this;
    }

    protected void formatSearchResult(JTree tree, MavenArtifactSearchResult searchResult) {
      MavenArtifactInfo first = searchResult.versions.get(0);
      MavenArtifactInfo last = searchResult.versions.get(searchResult.versions.size() - 1);
      myLeftComponent.append(first.getGroupId() + ":" + first.getArtifactId(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      myLeftComponent.append(":" + last.getVersion() + "-" + first.getVersion(), SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
  }

  private static class MyClassCellRenderer extends MyArtifactCellRenderer {
    @Override
    protected void formatSearchResult(JTree tree, MavenArtifactSearchResult searchResult) {
      MavenClassSearchResult classResult = (MavenClassSearchResult)searchResult;
      MavenArtifactInfo info = searchResult.versions.get(0);

      myLeftComponent.append(classResult.className, SimpleTextAttributes.REGULAR_ATTRIBUTES);
      myLeftComponent.append(" (" + classResult.packageName + ")", SimpleTextAttributes.GRAY_ATTRIBUTES);

      myRightComponent.append(" " + info.getGroupId() + ":" + info.getArtifactId(),
                              SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
  }

  public interface Listener {
    void doubleClicked();

    void canSelectStateChanged(MavenArtifactSearchPanel from, boolean canSelect);
  }
}
