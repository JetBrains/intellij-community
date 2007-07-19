package com.intellij.ide.util.importProject;

import com.intellij.ide.util.newProjectWizard.ProjectFromSourcesBuilder;
import com.intellij.openapi.ui.Splitter;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Jul 16, 2007
 */
abstract class ProjectLayoutPanel<T> extends JPanel {
  private JList myEntriesList;
  private JList myDependenciesList;
  private final ProjectFromSourcesBuilder myBuilder;

  public ProjectLayoutPanel(final ProjectFromSourcesBuilder builder) {
    super(new BorderLayout());
    myBuilder = builder;

    myEntriesList = createList();
    myDependenciesList = createList();
    
    final Splitter splitter = new Splitter(false);
    splitter.setFirstComponent(new JScrollPane(myEntriesList));
    splitter.setSecondComponent(new JScrollPane(myDependenciesList));
    
    add(splitter, BorderLayout.CENTER);
    
    myEntriesList.addListSelectionListener(new ListSelectionListener() {
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
  
  public final ProjectFromSourcesBuilder getBuilder() {
    return myBuilder; 
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
    final Object[] values = myEntriesList.getSelectedValues();
    final List<T> list = new ArrayList<T>(values.length);
    for (Object value : values) {
      list.add((T)value);
    }
    return list;
  }
  
  public void rebuild() {
    myEntriesList.getSelectionModel().clearSelection();
    final DefaultListModel model = (DefaultListModel)myEntriesList.getModel();
    model.clear();
    for (T entry : alphaSortList(getEntries())) {
      model.addElement(entry);
    }
    if (model.getSize() > 0) {
      myEntriesList.setSelectedIndex(0);
    }
  }

  private <T> List<T> alphaSortList(final List<T> entries) {
    Collections.sort(entries, new Comparator<T>() {
      public int compare(final T o1, final T o2) {
        return getElementText(o1).compareToIgnoreCase(getElementText(o2));
      }
    });
    return entries;
  }

  protected Icon getElementIcon(Object element) {
    return null;
  }
  
  protected abstract String getElementText(Object element);
  
  protected abstract List<T> getEntries();
  
  protected abstract Collection getDependencies(T entry);
  
  @Nullable
  protected abstract T merge(List<T> entries);
  
  private class MyListCellRenderer extends DefaultListCellRenderer {
    public Component getListCellRendererComponent(final JList list, final Object value, final int index, final boolean isSelected, final boolean cellHasFocus) {
      final Component comp = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      setText(getElementText(value));
      setIcon(getElementIcon(value));
      return comp;
    }
  }
}
