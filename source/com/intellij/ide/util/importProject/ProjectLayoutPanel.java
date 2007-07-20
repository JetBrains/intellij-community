package com.intellij.ide.util.importProject;

import com.intellij.ide.util.ElementsChooser;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.io.File;
import java.util.*;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Jul 16, 2007
 */
abstract class ProjectLayoutPanel<T> extends JPanel {
  private ElementsChooser<T> myEntriesChooser;
  private JList myDependenciesList;
  private final ModuleInsight myInsight; 
  private static final Icon RENAME_ICON = IconLoader.getIcon("/toolbar/unknown.png"); 
  private static final Icon MERGE_ICON = IconLoader.getIcon("/toolbar/unknown.png");

  public ProjectLayoutPanel(final ModuleInsight insight) {
    super(new BorderLayout());
    myInsight = insight;

    myEntriesChooser = new ElementsChooser<T>(true) {
      public String getItemText(T element) {
        return getElementText(element);
      }
    }; 
    myDependenciesList = createList();
    
    final Splitter splitter = new Splitter(false);
    final JPanel entriesPanel = new JPanel(new BorderLayout());
    entriesPanel.add(createEntriesActionToolbar().getComponent(), BorderLayout.NORTH);
    entriesPanel.add(new JScrollPane(myEntriesChooser), BorderLayout.CENTER);
    splitter.setFirstComponent(entriesPanel);
    splitter.setSecondComponent(new JScrollPane(myDependenciesList));
    
    add(splitter, BorderLayout.CENTER);
    
    myEntriesChooser.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(final ListSelectionEvent e) {
        final List<T> entries = getSelectedEntries();
        final Collection deps = getDependencies(entries);
        
        final DefaultListModel depsModel = (DefaultListModel)myDependenciesList.getModel();
        depsModel.clear();
        for (Object dep : alphaSortList(new ArrayList(deps))) {
          depsModel.addElement(dep);
        }
      }
    });
  }

  private ActionToolbar createEntriesActionToolbar() {
    final DefaultActionGroup entriesActions = new DefaultActionGroup();
    entriesActions.add(new RenameAction());
    entriesActions.add(new MergeAction());
    return ActionManager.getInstance().createActionToolbar("ProjectLayoutPanel.Entries", entriesActions, true);
  }

  public final ModuleInsight getInsight() {
    return myInsight; 
  }
  
  private JList createList() {
    final JList list = new JList(new DefaultListModel());
    list.getSelectionModel().setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    list.setCellRenderer(new MyListCellRenderer());
    return list;
  }

  public final Collection getDependencies(final List<T> entries) {
    final Set deps = new HashSet();
    for (T et : entries) {
      deps.addAll(getDependencies(et));
    }
    return deps;
  }

  public List<T> getSelectedEntries() {
    return myEntriesChooser.getSelectedElements();
  }

  public List<T> getChosenEntries() {
    return myEntriesChooser.getMarkedElements();
  }
  
  public void rebuild() {
    myEntriesChooser.clear();
    for (final T entry : alphaSortList(getEntries())) {
      myEntriesChooser.addElement(entry, true, new ElementsChooser.ElementProperties() {
        public Icon getIcon() {
          return getElementIcon(entry);
        }

        public Color getColor() {
          return null;
        }
      });
    }
    if (myEntriesChooser.getElementCount() > 0) {
      myEntriesChooser.selectElements(Collections.singleton(myEntriesChooser.getElementAt(0)));
    }
  }

  private <T> List<T> alphaSortList(final List<T> entries) {
    Collections.sort(entries, new Comparator<T>() {
      public int compare(final T o1, final T o2) {
        final int w1 = getWeight(o1);
        final int w2 = getWeight(o2);
        if (w1 != w2) {
          return w1 - w2;
        }
        return getElementText(o1).compareToIgnoreCase(getElementText(o2));
      }
    });
    return entries;
  }

  protected Icon getElementIcon(Object element) {
    return null;
  }
  
  protected int getWeight(Object element) {
    return Integer.MAX_VALUE;
  }
  
  protected abstract String getElementText(Object element);
  
  protected abstract List<T> getEntries();
  
  protected abstract Collection getDependencies(T entry);
  
  @Nullable
  protected abstract T merge(List<T> entries);
  
  protected abstract String getElementName(T entry);
  
  protected abstract void setElementName(T entry, String name);
  
  private class MergeAction extends AnAction {
    private MergeAction() {
      super("Merge", "", MERGE_ICON); // todo
    }

    public void actionPerformed(final AnActionEvent e) {
      final List<T> elements = myEntriesChooser.getSelectedElements();
      if (elements.size() > 1) {
        final String newName = Messages.showInputDialog(
          ProjectLayoutPanel.this, 
          "Enter new name for merge result:", 
          "Merge", 
          Messages.getQuestionIcon(), getElementName(elements.get(0)), null);
        final T merged = merge(elements);
        setElementName(merged, newName);
        rebuild();
        myEntriesChooser.selectElements(Collections.singleton(merged));
      }
    }
  }

  private class RenameAction extends AnAction {
    private RenameAction() {
      super("Rename", "", RENAME_ICON); // todo
    }

    public void actionPerformed(final AnActionEvent e) {
      final List<T> elements = myEntriesChooser.getSelectedElements();
      if (elements.size() == 1) {
        final T element = elements.get(0);
        final String newName = Messages.showInputDialog(
          ProjectLayoutPanel.this, 
          "Enter new name for " + getElementText(element), 
          "Rename", 
          Messages.getQuestionIcon(), 
          getElementName(element), 
          null
        );
        setElementName(element, newName);
        rebuild();
        myEntriesChooser.selectElements(Collections.singleton(element));
      }
    }

    public void update(final AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabled(myEntriesChooser.getSelectedElements().size() == 1);
    }
  }
  
  protected static String getDisplayText(File file) {
    final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append(file.getName());
      final File parentFile = file.getParentFile();
      if (parentFile != null) {
        builder.append(" (");
        builder.append(parentFile.getPath());
        builder.append(")");
      }
      return builder.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }
  
  private class MyListCellRenderer extends DefaultListCellRenderer {
    public Component getListCellRendererComponent(final JList list, final Object value, final int index, final boolean isSelected, final boolean cellHasFocus) {
      final Component comp = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      setText(getElementText(value));
      setIcon(getElementIcon(value));
      return comp;
    }
  }
  
  
}
