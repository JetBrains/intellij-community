package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.containers.HashMap;
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.ISVNPropertyHandler;
import org.tmatesoft.svn.core.wc.SVNPropertyData;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

/**
 * Created by IntelliJ IDEA.
 * User: alex
 * Date: Jun 20, 2006
 * Time: 4:39:46 PM
 */
public class PropertiesComponent extends JPanel {

  public static final String ID = "SVN Properties";
  private JTable myTable;
  private JTextArea myTextArea;
  private File myFile;
  private SvnVcs myVcs;
  private JSplitPane mySplitPane;
  private static final String CONTEXT_ID = "context";

  public PropertiesComponent() {
    // register toolwindow and add listener to the selection.
    init();
  }

  public void init() {
    setLayout(new BorderLayout());
    myTable = new JTable();
    myTextArea = new JTextArea(0, 0);
    myTextArea.setEditable(false);
    JScrollPane scrollPane = new JScrollPane(myTable);
    mySplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, true, scrollPane, new JScrollPane(myTextArea));
    add(mySplitPane, BorderLayout.CENTER);
    add(createToolbar(), BorderLayout.WEST);
    myTable.setModel(new DefaultTableModel(createTableModel(new HashMap()), new Object[] {"Name", "Value"}));
    myTable.setShowVerticalLines(true);
    myTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        int index = myTable.getSelectedRow();
        if (index >= 0) {
          Object value = myTable.getValueAt(index, 1);
          if (value instanceof String) {
            myTextArea.setText(((String) value));
          } else {
            myTextArea.setText("");
          }
        } else {
          myTextArea.setText("");
        }
      }
    });
    myTable.addMouseListener(new PopupAdapter());
    scrollPane.addMouseListener(new PopupAdapter());
  }

  public void setFile(SvnVcs vcs, File file) {
    final Map props = new TreeMap();
    boolean firstTime = myFile == null;
    myFile = file;
    myVcs = vcs;
    if (file != null) {
      try {
        vcs.createWCClient().doGetProperty(file, null, SVNRevision.UNDEFINED, SVNRevision.WORKING, false, new ISVNPropertyHandler() {
          public void handleProperty(File path, SVNPropertyData property) throws SVNException {
            props.put(property.getName(), property.getValue());
          }
          public void handleProperty(SVNURL url, SVNPropertyData property) throws SVNException {
          }
          public void handleProperty(long revision, SVNPropertyData property) throws SVNException {
          }
        });
      } catch (SVNException e) {
        props.clear();
      }
    }
    DefaultTableModel model = (DefaultTableModel) myTable.getModel();
    model.setDataVector(createTableModel(props), new Object[] {"Name", "Value"});

    myTable.getColumnModel().setColumnSelectionAllowed(false);
    myTable.getColumnModel().getColumn(1).setCellRenderer(new DefaultTableCellRenderer() {
      protected void setValue(Object value) {
        if (value != null) {
          if (value.toString().indexOf('\r') >= 0) {
            value = value.toString().substring(0, value.toString().indexOf('\r')) + " [...]";
          }
          if (value.toString().indexOf('\n') >= 0) {
            value = value.toString().substring(0, value.toString().indexOf('\n')) + " [...]";
          }
        }
        super.setValue(value);
      }
    });
    if (firstTime) {
      mySplitPane.setDividerLocation(.5);
    }
    if (myTable.getRowCount() > 0) {
      myTable.getSelectionModel().setSelectionInterval(0, 0);
    }
  }

  private Object[][] createTableModel(Map model) {
    Object[][] result = new Object[model.size()][2];
    int index = 0;
    for (Iterator names = model.keySet().iterator(); names.hasNext();) {
      String name = (String) names.next();
      String value = (String) model.get(name);
      if (value == null) {
        value = "";
      }
      result[index][0] = name;
      result[index][1] = value;
      index++;
    }
    return result;
  }

  private JComponent createToolbar() {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new AddPropertyAction());
    group.add(new EditPropertyAction());
    group.add(new DeletePropertyAction());
    group.addSeparator();
    group.add(new SetKeywordsAction());
    group.addSeparator();
    group.add(new RefreshAction());
    group.add(new CloseAction());
    return ActionManager.getInstance().createActionToolbar("", group, false).getComponent();
  }

  private JPopupMenu createPopup() {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new AddPropertyAction());
    group.add(new EditPropertyAction());
    group.add(new DeletePropertyAction());
    group.addSeparator();
    group.add(new SetKeywordsAction());
    group.addSeparator();
    group.add(new RefreshAction());
    return ActionManager.getInstance().createActionPopupMenu(CONTEXT_ID, group).getComponent();
  }

  private String getSelectedPropertyName() {
    int row = myTable.getSelectedRow();
    if (row < 0) {
      return null;
    }
    return (String) myTable.getValueAt(row, 0);
  }

  private class CloseAction extends AnAction {
    public void update(AnActionEvent e) {
      e.getPresentation().setText("Close");
      e.getPresentation().setDescription("Close this tool window");
      e.getPresentation().setIcon(IconLoader.findIcon("/actions/cancel.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      Project p = (Project) e.getDataContext().getData(DataConstants.PROJECT);
      ToolWindowManager.getInstance(p).unregisterToolWindow(ID);
    }
  }

  private class RefreshAction extends AnAction {
    public void update(AnActionEvent e) {
      e.getPresentation().setText("Refresh");
      e.getPresentation().setDescription("Reload properties");
      e.getPresentation().setIcon(IconLoader.findIcon("/actions/sync.png"));
      e.getPresentation().setEnabled(myFile != null);
    }

    public void actionPerformed(AnActionEvent e) {
      setFile(myVcs, myFile);
    }
  }

  private class SetKeywordsAction extends AnAction {

    public void update(AnActionEvent e) {
      e.getPresentation().setText("Edit Keywords");
      e.getPresentation().setDescription("Manage svn:keywords property");
      if (!CONTEXT_ID.equals(e.getPlace())) {
        e.getPresentation().setIcon(IconLoader.findIcon("/actions/properties.png"));
      }
      e.getPresentation().setEnabled(myFile != null && myFile.isFile());
    }

    public void actionPerformed(AnActionEvent e) {
      Project project = (Project) e.getDataContext().getData(DataConstants.PROJECT);
      SVNWCClient wcClient = new SVNWCClient(null, null);
      SVNPropertyData propValue = null;
      try {
        propValue = wcClient.doGetProperty(myFile, SVNProperty.KEYWORDS, SVNRevision.UNDEFINED, SVNRevision.WORKING, false);
      } catch (SVNException e1) {
        // show error message
      }
      SetKeywordsDialog dialog = new SetKeywordsDialog(project, propValue != null ? propValue.getValue() : null);
      dialog.show();
      if (dialog.isOK()) {
        String value = dialog.getKeywords();
        try {
          wcClient.doSetProperty(myFile, SVNProperty.KEYWORDS, value, false, false, null);
        }
        catch (SVNException err) {
          // show error message
        }
      }
      setFile(myVcs, myFile);
    }
  }

  private class DeletePropertyAction extends AnAction {
    public void update(AnActionEvent e) {
      e.getPresentation().setText("Delete Property");
      e.getPresentation().setDescription("Delete selected property");
      if (!CONTEXT_ID.equals(e.getPlace())) {
        e.getPresentation().setIcon(IconLoader.findIcon("/general/remove.png"));
      }
      e.getPresentation().setEnabled(myFile != null && getSelectedPropertyName() != null);
    }

    public void actionPerformed(AnActionEvent e) {
      try {
        myVcs.createWCClient().doSetProperty(myFile, getSelectedPropertyName(), null, true, false, null);
      } catch (SVNException error) {
        // show error message.
      }
      setFile(myVcs, myFile);
    }
  }

  private class AddPropertyAction extends AnAction {

    public void update(AnActionEvent e) {
      e.getPresentation().setText("Add Property");
      e.getPresentation().setDescription("Add new property");
      if (!CONTEXT_ID.equals(e.getPlace())) {
        e.getPresentation().setIcon(IconLoader.findIcon("/general/add.png"));
      }
      e.getPresentation().setEnabled(myFile != null);
    }

    public void actionPerformed(AnActionEvent e) {
      Project project = (Project) e.getDataContext().getData(DataConstants.PROJECT);
      SetPropertyDialog dialog = new SetPropertyDialog(project, new File[] {myFile}, null,
              myFile.isDirectory());
      dialog.show();
      if (dialog.isOK()) {
        String name = dialog.getPropertyName();
        String value = dialog.getPropertyValue();
        boolean recursive = dialog.isRecursive();
        SVNWCClient wcClient = new SVNWCClient(null, null);
        try {
          wcClient.doSetProperty(myFile, name, value, false, recursive, null);
        }
        catch (SVNException err) {
          // show error message
        }
      }
      setFile(myVcs, myFile);
    }
  }

  private class EditPropertyAction extends AnAction {
    public void update(AnActionEvent e) {
      e.getPresentation().setText("Edit Property");
      e.getPresentation().setDescription("Edit selected property value");
      if (!CONTEXT_ID.equals(e.getPlace())) {
        e.getPresentation().setIcon(IconLoader.findIcon("/actions/editSource.png"));
      }
      e.getPresentation().setEnabled(myFile != null && getSelectedPropertyName() != null);
    }

    public void actionPerformed(AnActionEvent e) {
      Project project = (Project) e.getDataContext().getData(DataConstants.PROJECT);
      SetPropertyDialog dialog = new SetPropertyDialog(project, new File[] {myFile}, getSelectedPropertyName(),
              myFile.isDirectory());
      dialog.show();
      if (dialog.isOK()) {
        String name = dialog.getPropertyName();
        String value = dialog.getPropertyValue();
        boolean recursive = dialog.isRecursive();
        SVNWCClient wcClient = new SVNWCClient(null, null);
        try {
          wcClient.doSetProperty(myFile, name, value, false, recursive, null);
        }
        catch (SVNException err) {
          // show error message
        }
      }
      setFile(myVcs, myFile);
    }
  }

  private class PopupAdapter extends MouseAdapter {
    public void mouseClicked(MouseEvent e) {
      showPopup(e);
    }

    public void mousePressed(MouseEvent e) {
      showPopup(e);
    }

    public void mouseReleased(MouseEvent e) {
      showPopup(e);
    }

    private void showPopup(MouseEvent e) {
      if (e.isPopupTrigger()) {
        createPopup().show(e.getComponent(), e.getX(), e.getY());
      }
    }
  }
}
