package org.jetbrains.idea.maven.indices;

import com.intellij.openapi.project.Project;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Alarm;
import com.intellij.util.ui.Tree;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.idea.maven.core.MavenLog;
import org.jetbrains.idea.maven.utils.MavenId;
import org.sonatype.nexus.index.ArtifactInfo;

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
  private Project myProject;
  private boolean myClassMode;
  private Listener myListener;

  private JTextField mySearchField;
  private Tree myResultList;

  private Alarm myAlarm = new Alarm(Alarm.ThreadToUse.OWN_THREAD);

  public MavenArtifactSearchPanel(Project project, String initialText, boolean classMode, Listener listener) {
    myProject = project;
    myClassMode = classMode;
    myListener = listener;

    initComponents(initialText);
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
        myListener.selectedChanged(hasSelection);
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
    myListener.searchStarted();

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

        myListener.searchFinished();
      }
    });
  }

  public MavenId getResult() {
    Object sel = myResultList.getLastSelectedPathComponent();
    ArtifactInfo info;
    if (sel instanceof ArtifactInfo) {
      info = (ArtifactInfo)sel;
    }
    else {
      info = ((MavenArtifactSearchResult)sel).versions.get(0);
    }

    return new MavenId(info.groupId, info.artifactId, info.version);
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
    private JTree myTree;

    private MyArtifactCellRenderer() {
      setLayout(new BorderLayout());
    }

    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row,
                                                  boolean hasFocus) {
      myLeftComponent.clear();
      myRightComponent.clear();
      myTree = tree;

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
      else if (value instanceof ArtifactInfo) {
        ArtifactInfo info = (ArtifactInfo)value;
        myLeftComponent.append(info.groupId + ":" + info.artifactId + ":" + info.version,
                               SimpleTextAttributes.GRAY_ATTRIBUTES);
        //myRightComponent.append(info.repository, SimpleTextAttributes.GRAY_ATTRIBUTES);
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

    //@Override
    //public Dimension getPreferredSize() {
    //  Dimension size = super.getPreferredSize();
    //  size.width = myTree.getVisibleRect().width - 30;
    //  return size;
    //}

    protected void formatSearchResult(JTree tree, MavenArtifactSearchResult searchResult) {
      ArtifactInfo first = searchResult.versions.get(0);
      ArtifactInfo last = searchResult.versions.get(searchResult.versions.size() - 1);
      myLeftComponent.append("" + first.packaging + "-", SimpleTextAttributes.REGULAR_ATTRIBUTES);
      myLeftComponent.append(first.groupId + ":" + first.artifactId, SimpleTextAttributes.REGULAR_ATTRIBUTES);
      myLeftComponent.append(":" + last.version + "-" + first.version, SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
  }

  private static class MyClassCellRenderer extends MyArtifactCellRenderer {
    @Override
    protected void formatSearchResult(JTree tree, MavenArtifactSearchResult searchResult) {
      MavenClassSearchResult classResult = (MavenClassSearchResult)searchResult;
      ArtifactInfo info = searchResult.versions.get(0);

      myLeftComponent.append(classResult.className, SimpleTextAttributes.REGULAR_ATTRIBUTES);
      myLeftComponent.append(" (" + classResult.packageName + ")", SimpleTextAttributes.GRAY_ATTRIBUTES);

      myRightComponent.append(" " + info.groupId + ":" + info.artifactId,
                              SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
  }

  public interface Listener {
    void doubleClicked();
    void selectedChanged(boolean hasSelection);
    void searchStarted();
    void searchFinished();
  }
}