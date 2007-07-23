package com.intellij.ide.util.importProject;

import com.intellij.ide.util.ElementsChooser;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.ui.DialogWrapper;
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
  private static final Icon RENAME_ICON = IconLoader.getIcon("/modules/edit.png"); 
  private static final Icon MERGE_ICON = IconLoader.getIcon("/modules/merge.png");
  private static final Icon SPLIT_ICON = IconLoader.getIcon("/modules/split.png");

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
    splitter.setFirstComponent(new JScrollPane(myEntriesChooser));
    splitter.setSecondComponent(new JScrollPane(myDependenciesList));
    
    add(createEntriesActionToolbar().getComponent(), BorderLayout.NORTH);
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
    entriesActions.add(new SplitAction());
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
  
  @Nullable
  protected abstract T split(T entry, String newEntryName, Collection<File> extractedData);
  
  protected abstract Collection<File> getContent(T entry);
  
  protected abstract String getElementName(T entry);
  
  protected abstract void setElementName(T entry, String name);
  
  protected abstract String getSplitDialogTitle();
  
  protected abstract String getSplitDialogChooseFilesPrompt();
  
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

  private class SplitAction extends AnAction {
    private SplitAction() {
      super("Split", "", SPLIT_ICON); // todo
    }

    public void actionPerformed(final AnActionEvent e) {
      final List<T> elements = myEntriesChooser.getSelectedElements();
      
      if (elements.size() == 1) {
        final T entry = elements.get(0);
        final Collection<File> files = getContent(entry);
        
        final SplitDialog dialog = new SplitDialog(files);
        dialog.show();

        if (dialog.isOK()) {
          final String newName = dialog.getName();
          final Collection<File> chosenFiles = dialog.getChosenFiles();
          
          final T extracted = split(entry, newName, chosenFiles);
          if (extracted != null) {
            rebuild();
            myEntriesChooser.selectElements(Collections.singleton(extracted));
          }
        }
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
  
  private class SplitDialog extends DialogWrapper {
    final JTextField myNameField;
    final ElementsChooser<File> myChooser;

    private SplitDialog(final Collection<File> files) {
      super(myEntriesChooser, true);
      setTitle(getSplitDialogTitle());
      
      myNameField = new JTextField();
      myChooser = new ElementsChooser<File>(true) {
        protected String getItemText(final File value) {
          return getElementText(value);
        }
      };
      for (final File file : files) {
        myChooser.addElement(file, false, new ElementsChooser.ElementProperties() {
          public Icon getIcon() {
            return getElementIcon(file);
          }
          public Color getColor() {
            return null;
          }
        });
      }
      init();
    }

    @Nullable
  protected JComponent createCenterPanel() {
      final JPanel panel = new JPanel(new BorderLayout());
      
      final JPanel labelNameField = new JPanel(new BorderLayout());
      labelNameField.add(new JLabel("Name:"), BorderLayout.NORTH);
      labelNameField.add(myNameField, BorderLayout.CENTER);
      labelNameField.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

      final JPanel labelChooser = new JPanel(new BorderLayout());
      labelChooser.add(new JLabel(getSplitDialogChooseFilesPrompt()), BorderLayout.NORTH);
      labelChooser.add(new JScrollPane(myChooser), BorderLayout.CENTER);
      labelChooser.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
      
      panel.add(labelNameField, BorderLayout.NORTH);
      panel.add(labelChooser, BorderLayout.CENTER);
      panel.setPreferredSize(new Dimension(450, 300));
      return panel;
    }

    public JComponent getPreferredFocusedComponent() {
      return myNameField;
    }

    public String getName() {
      return myNameField.getText().trim();
    }

    public Collection<File> getChosenFiles() {
      return myChooser.getMarkedElements();
    }
  }
  
}
