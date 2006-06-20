package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ide.actions.CollapseAllToolbarAction;
import com.intellij.ide.TreeExpander;
import com.intellij.util.containers.HashMap;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.util.Map;
import java.util.Vector;
import java.util.Iterator;

import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.ISVNPropertyHandler;
import org.tmatesoft.svn.core.wc.SVNPropertyData;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;

/**
 * Created by IntelliJ IDEA.
 * User: alex
 * Date: Jun 20, 2006
 * Time: 4:39:46 PM
 */
public class PropertiesComponent extends JPanel {

  public static final String ID = "SVN Properties";
  private JTable myTable;
  private File myFile;
  private SvnVcs myVcs;

  public PropertiesComponent() {
    // register toolwindow and add listener to the selection.
    init();
  }

  public void init() {
    setLayout(new BorderLayout());
    myTable = new JTable();
    add(new JScrollPane(myTable), BorderLayout.CENTER);
    add(createToolbar(), BorderLayout.WEST);
    myTable.setModel(new DefaultTableModel(createTableModel(new HashMap()), new Object[] {"Name", "Value"}));
  }

  public void setFile(SvnVcs vcs, File file) {
    final Map props = new HashMap();
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
    }
    return result;
  }

  private JComponent createToolbar() {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new AnAction() {
        public void update(AnActionEvent e) {
          e.getPresentation().setText("Close");
          e.getPresentation().setDescription("Close this tool window");
          e.getPresentation().setIcon(IconLoader.findIcon("/actions/cancel.png"));
        }
        public void actionPerformed(AnActionEvent e) {
          Project p = (Project) e.getDataContext().getData(DataConstants.PROJECT);
          ToolWindowManager.getInstance(p).unregisterToolWindow(ID);
        }
      });
    return ActionManager.getInstance().createActionToolbar("", group, false).getComponent();
  }
}
