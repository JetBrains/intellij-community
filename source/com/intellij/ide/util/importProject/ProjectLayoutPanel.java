package com.intellij.ide.util.importProject;

import com.intellij.openapi.ui.Splitter;
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
  private JList myEntriesList;
  private JList myDependenciesList;
  private final ModuleInsight myInsight;

  public ProjectLayoutPanel(final ModuleInsight insight) {
    super(new BorderLayout());
    myInsight = insight;

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
