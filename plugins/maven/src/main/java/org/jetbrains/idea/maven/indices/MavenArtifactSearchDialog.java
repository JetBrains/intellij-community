package org.jetbrains.idea.maven.indices;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Alarm;
import com.intellij.util.ui.Tree;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.idea.maven.core.util.MavenId;
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

public class MavenArtifactSearchDialog extends DialogWrapper {
  private MavenId myResult;

  private JTabbedPane myTabbedPane;
  private JPanel myArtifactsTab;
  private JPanel myClassesTab;
  private JTextField myArtifactSearchField;
  private JTextField myClassSearchField;
  private Tree myArtifactsList;
  private Tree myClassesList;

  private Alarm myArtifactsAlarm = new Alarm(Alarm.ThreadToUse.OWN_THREAD);
  private Alarm myClassesAlarm = new Alarm(Alarm.ThreadToUse.OWN_THREAD);
  private Project myProject;

  public static MavenId searchForClass(Project project, String className) {
    MavenArtifactSearchDialog d = new MavenArtifactSearchDialog(project, className, true);
    d.show();
    if (!d.isOK()) return null;

    return d.getResult();
  }

  public static MavenId searchForArtifact(Project project) {
    MavenArtifactSearchDialog d = new MavenArtifactSearchDialog(project, "", false);
    d.show();
    if (!d.isOK()) return null;

    return d.getResult();
  }

  private MavenArtifactSearchDialog(Project project, String initialText, boolean classMode) {
    super(project, true);
    myProject = project;

    initComponents(classMode);

    if (classMode) {
      myClassSearchField.setText(initialText);
    }
    else {
      myArtifactSearchField.setText(initialText);
    }

    configComponents(false, myArtifactSearchField, myArtifactsList);
    configComponents(true, myClassSearchField, myClassesList);

    setTitle("Maven Artifact Search");
    setOKActionEnabled(false);
    init();

    scheduleSearch(classMode);
    scheduleSearch(!classMode);
  }

  private void initComponents(boolean classMode) {
    myTabbedPane = new JTabbedPane(JTabbedPane.TOP);

    myArtifactSearchField = new JTextField();
    myArtifactsList = new Tree();
    myArtifactsTab = new JPanel(new BorderLayout());
    myArtifactsTab.add(myArtifactSearchField, BorderLayout.NORTH);
    myArtifactsTab.add(new JScrollPane(myArtifactsList), BorderLayout.CENTER);

    myClassSearchField = new JTextField();
    myClassesList = new Tree();
    myClassesTab = new JPanel(new BorderLayout());
    myClassesTab.add(myClassSearchField, BorderLayout.NORTH);
    myClassesTab.add(new JScrollPane(myClassesList), BorderLayout.CENTER);

    myTabbedPane.addTab("Search for artifact", myArtifactsTab);
    myTabbedPane.addTab("Search for class", myClassesTab);
    myTabbedPane.setSelectedIndex(classMode ? 1 : 0);

    myTabbedPane.setMnemonicAt(0, KeyEvent.VK_A);
    myTabbedPane.setDisplayedMnemonicIndexAt(0, myTabbedPane.getTitleAt(0).indexOf("artifact"));
    myTabbedPane.setMnemonicAt(1, KeyEvent.VK_C);
    myTabbedPane.setDisplayedMnemonicIndexAt(1, myTabbedPane.getTitleAt(1).indexOf("class"));
  }

  private void configComponents(final boolean classMode, final JTextField searchField, final Tree resultTree) {
    searchField.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        scheduleSearch(classMode);
      }
    });

    resultTree.addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        Alarm alarm = classMode ? myClassesAlarm : myArtifactsAlarm;
        if (alarm.getActiveRequestCount() > 0) return;

        boolean hasSelection = !resultTree.isSelectionEmpty();
        setOKActionEnabled(hasSelection);
      }
    });

    searchField.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        final Object action = getAction(e, resultTree);
        if ("selectNext".equals(action)) {
          TreeUtil.moveDown(resultTree);
        }
        else if ("selectPrevious".equals(action)) {
          TreeUtil.moveUp(resultTree);
        }
        else if ("scrollUpChangeSelection".equals(action)) {
          TreeUtil.movePageUp(resultTree);
        }
        else if ("scrollDownChangeSelection".equals(action)) {
          TreeUtil.movePageDown(resultTree);
        }
      }

      private Object getAction(final KeyEvent e, final JComponent comp) {
        final KeyStroke stroke = KeyStroke.getKeyStroke(e.getKeyCode(), e.getModifiers());
        return comp.getInputMap().get(stroke);
      }
    });

    resultTree.setRootVisible(false);
    resultTree.setShowsRootHandles(true);
    resultTree.setModel(null);
    resultTree.setFocusable(false);
    resultTree.setCellRenderer(classMode ? new MyClassCellRenderer() : new MyArtifactCellRenderer());

    resultTree.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2) {
          Object sel = resultTree.getLastSelectedPathComponent();
          if (sel != null && resultTree.getModel().isLeaf(sel)) {
            clickDefaultButton();
          }
        }

        if (!searchField.hasFocus()) {
          searchField.requestFocus();
        }
      }
    });
  }

  @Override
  protected Action getOKAction() {
    Action result = super.getOKAction();
    result.putValue(Action.NAME, "Add");
    return result;
  }

  protected JComponent createCenterPanel() {
    return myTabbedPane;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myTabbedPane.getSelectedIndex() == 0 ? myArtifactSearchField : myClassSearchField;
  }

  @Override
  protected String getDimensionServiceKey() {
    return "Maven.ArtifactSearchDialog";
  }

  public MavenId getResult() {
    return myResult;
  }

  private void scheduleSearch(final boolean classMode) {
    setOKActionEnabled(false);

    // evaluate text value in the swing thread
    final String text = classMode ? myClassSearchField.getText() : myArtifactSearchField.getText();

    Alarm alarm = classMode ? myClassesAlarm : myArtifactsAlarm;
    alarm.cancelAllRequests();
    alarm.addRequest(new Runnable() {
      public void run() {
        doSearch(classMode, text);
      }
    }, 500);
  }

  private void doSearch(final boolean classMode, String searchText) {
    MavenSearcher searcher = classMode ? new MavenClassSearcher() : new MavenArtifactSearcher();
    List<MavenArtifactSearchResult> result = searcher.search(myProject, searchText, 200);
    final TreeModel model = new MyTreeModel(result);

    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        if (myProject.isDisposed()) return;
        Tree list = classMode ? myClassesList : myArtifactsList;
        list.setModel(model);
        list.setSelectionRow(0);
      }
    }, ModalityState.stateForComponent(myTabbedPane));
  }

  @Override
  protected void doOKAction() {
    Tree list = myTabbedPane.getSelectedIndex() == 0 ? myArtifactsList : myClassesList;
    Object sel = list.getLastSelectedPathComponent();
    ArtifactInfo info;
    if (sel instanceof ArtifactInfo) {
      info = (ArtifactInfo)sel;
    }
    else {
      info = ((MavenArtifactSearchResult)sel).versions.get(0);
    }

    myResult = new MavenId(info.groupId, info.artifactId, info.version);
    super.doOKAction();
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
      else if (value instanceof ArtifactInfo) {
        ArtifactInfo info = (ArtifactInfo)value;
        myLeftComponent.append(info.groupId + ":" + info.artifactId + ":" + info.version,
                               SimpleTextAttributes.GRAY_ATTRIBUTES);
        myRightComponent.append(info.repository, SimpleTextAttributes.GRAY_ATTRIBUTES);
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
      ArtifactInfo first = searchResult.versions.get(0);
      ArtifactInfo last = searchResult.versions.get(searchResult.versions.size() - 1);
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
}
