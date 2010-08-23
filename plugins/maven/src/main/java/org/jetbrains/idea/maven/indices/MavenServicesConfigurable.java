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

import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.ListUtil;
import com.intellij.ui.components.JBList;
import com.intellij.util.Processor;
import com.intellij.util.ui.AnimatedIcon;
import com.intellij.util.ui.AsyncProcessIcon;
import org.jetbrains.idea.maven.model.MavenRepositoryInfo;
import org.jetbrains.idea.maven.services.MavenServicesManager;
import org.jetbrains.idea.maven.utils.RepositoryAttachHandler;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

public class MavenServicesConfigurable extends BaseConfigurable implements SearchableConfigurable{
  private final MavenProjectIndicesManager myManager;

  private JPanel myMainPanel;
  private JTable myTable;
  private JButton myUpdateButton;
  private JButton myRemoveNexusButton;
  private JButton myAddNexusButton;
  private JBList myNexusList;
  private JButton myTestButton;

  private AnimatedIcon myUpdatingIcon;
  private final Icon myWaitingIcon = IconLoader.getIcon("/process/step_passive.png");
  private Timer myRepaintTimer;
  private ActionListener myTimerListener;
  private ArrayList<String> myServiceUrls = new ArrayList<String>();
  private final Project myProject;

  public MavenServicesConfigurable(Project project) {
    myProject = project;
    myManager = MavenProjectIndicesManager.getInstance(project);
    configControls();
  }

  @Override
  public boolean isModified() {
    return !myServiceUrls.equals(MavenServicesManager.getInstance().getUrls());
  }

  private void configControls() {
    myNexusList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myAddNexusButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final String value = (String)myNexusList.getSelectedValue();
        final String text = Messages.showInputDialog("Artifactory or Nexus Service URL", "Add Service URL", Messages.getQuestionIcon(), value == null? "http://": value, new URLInputVaslidator());
        ((CollectionListModel)myNexusList.getModel()).add(text);
        myNexusList.setSelectedValue(text, true);
      }
    });
    ListUtil.addRemoveListener(myRemoveNexusButton, myNexusList);
    ListUtil.disableWhenNoSelection(myTestButton, myNexusList);
    myTestButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final String value = (String)myNexusList.getSelectedValue();
        if (value != null) {
          testNexusConnection(value);
        }
      }
    });

    myUpdateButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        doUpdateIndex();
      }
    });

    myTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        updateButtonsState();
      }
    });

    myTable.addMouseMotionListener(new MouseMotionListener() {
      public void mouseDragged(MouseEvent e) {
      }

      public void mouseMoved(MouseEvent e) {
        int row = myTable.rowAtPoint(e.getPoint());
        if (row == -1) return;
        updateIndexHint(row);
      }
    });

    myTable.setDefaultRenderer(Object.class, new MyCellRenderer());
    myTable.setDefaultRenderer(MavenIndicesManager.IndexUpdatingState.class,
                               new MyIconCellRenderer());

    updateButtonsState();
  }

  private void testNexusConnection(String url) {
    RepositoryAttachHandler.searchRepositories(myProject, Collections.singletonList(url), new Processor<Collection<MavenRepositoryInfo>>() {
      @Override
      public boolean process(Collection<MavenRepositoryInfo> infos) {
        if (infos.isEmpty()) {
          Messages.showMessageDialog("No repositories found", "Service Connection Failed", Messages.getWarningIcon());
        }
        else {
          final StringBuilder sb = new StringBuilder();
          sb.append(infos.size()).append(infos.size() == 1? "repository" :" repositories").append(" found");
          for (MavenRepositoryInfo info : infos) {
            sb.append("\n  ");
            sb.append(info.getId()).append(" (").append(info.getName()).append(")").append(": ").append(info.getUrl());
          }
          Messages.showMessageDialog(sb.toString(), "Service Connection Successfull", Messages.getInformationIcon());
        }
        return true;
      }
    });
  }

  private void updateButtonsState() {
    boolean hasSelection = !myTable.getSelectionModel().isSelectionEmpty();
    myUpdateButton.setEnabled(hasSelection);
  }

  public void updateIndexHint(int row) {
    MavenIndex index = getIndexAt(row);
    String message = index.getFailureMessage();
    if (message == null) {
      myTable.setToolTipText(null);
    }
    else {
      myTable.setToolTipText(message);
    }
  }

  private void doUpdateIndex() {
    myManager.scheduleUpdate(getSelectedIndices());
  }

  private List<MavenIndex> getSelectedIndices() {
    List<MavenIndex> result = new ArrayList<MavenIndex>();
    for (int i : myTable.getSelectedRows()) {
      result.add(getIndexAt(i));
    }
    return result;
  }

  private MavenIndex getIndexAt(int i) {
    MyTableModel model = (MyTableModel)myTable.getModel();
    return model.getIndex(i);
  }

  public String getDisplayName() {
    return IndicesBundle.message("maven.services.title");
  }

  public Icon getIcon() {
    return null;
  }

  public String getHelpTopic() {
    return "reference.settings.project.maven.repository.indices";
  }

  public String getId() {
    return getHelpTopic();
  }

  public Runnable enableSearch(String option) {
    return null;
  }

  public JComponent createComponent() {
    return myMainPanel;
  }

  public void apply() throws ConfigurationException {
    MavenServicesManager.getInstance().setUrls(myServiceUrls);
  }

  public void reset() {
    myServiceUrls.clear();
    myServiceUrls.addAll(MavenServicesManager.getInstance().getUrls());
    myNexusList.setModel(new CollectionListModel(myServiceUrls));

    myTable.setModel(new MyTableModel(myManager.getIndices()));
    myTable.getColumnModel().getColumn(0).setPreferredWidth(400);
    myTable.getColumnModel().getColumn(1).setPreferredWidth(50);
    myTable.getColumnModel().getColumn(2).setPreferredWidth(50);
    myTable.getColumnModel().getColumn(3).setPreferredWidth(20);

    myUpdatingIcon = new AsyncProcessIcon(IndicesBundle.message("maven.indices.updating"));
    myUpdatingIcon.resume();

    myTimerListener = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myTable.repaint();
      }
    };
    myRepaintTimer = new Timer(AsyncProcessIcon.CYCLE_LENGTH / AsyncProcessIcon.COUNT, myTimerListener);
    myRepaintTimer.start();
  }

  public void disposeUIResources() {
    myRepaintTimer.removeActionListener(myTimerListener);
    myRepaintTimer.stop();
    Disposer.dispose(myUpdatingIcon);
  }

  private class MyTableModel extends AbstractTableModel {
    private final String[] COLUMNS =
      new String[]{
        IndicesBundle.message("maven.index.url"),
        IndicesBundle.message("maven.index.type"),
        IndicesBundle.message("maven.index.updated"),
        ""};

    private final List<MavenIndex> myIndices;

    public MyTableModel(List<MavenIndex> indices) {
      myIndices = indices;
    }

    public int getColumnCount() {
      return COLUMNS.length;
    }

    @Override
    public String getColumnName(int index) {
      return COLUMNS[index];
    }

    public int getRowCount() {
      return myIndices.size();
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
      if (columnIndex == 3) return MavenIndicesManager.IndexUpdatingState.class;
      return super.getColumnClass(columnIndex);
    }

    public Object getValueAt(int rowIndex, int columnIndex) {
      MavenIndex i = getIndex(rowIndex);
      switch (columnIndex) {
        case 0:
          return i.getRepositoryPathOrUrl();
        case 1:
          if (i.getKind() == MavenIndex.Kind.LOCAL) return "Local";
          return "Remote";
        case 2:
          if (i.getFailureMessage() != null) {
            return IndicesBundle.message("maven.index.updated.error");
          }
          long timestamp = i.getUpdateTimestamp();
          if (timestamp == -1) return IndicesBundle.message("maven.index.updated.never");
          return SimpleDateFormat.getDateInstance(SimpleDateFormat.SHORT).format(new Date(timestamp));
        case 3:
          return myManager.getUpdatingState(i);
      }
      throw new RuntimeException();
    }

    public MavenIndex getIndex(int rowIndex) {
      return myIndices.get(rowIndex);
    }
  }

  private class MyCellRenderer extends DefaultTableCellRenderer {
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      // reset custom colors and let DefaultTableCellRenderer to set ones
      setForeground(null);
      setBackground(null);

      Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

      MavenIndex index = getIndexAt(row);
      if (index.getFailureMessage() != null) {
        if (isSelected) {
          setForeground(Color.PINK);
        }
        else {
          setBackground(Color.PINK);
        }
      }

      return c;
    }
  }

  private class MyIconCellRenderer extends MyCellRenderer {
    MavenIndicesManager.IndexUpdatingState myState;

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      myState = (MavenIndicesManager.IndexUpdatingState)value;
      return super.getTableCellRendererComponent(table, "", isSelected, hasFocus, row, column);
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      Dimension size = getSize();
      switch (myState) {
        case UPDATING:
          myUpdatingIcon.setBackground(getBackground());
          myUpdatingIcon.setSize(size.width, size.height);
          myUpdatingIcon.paint(g);
          break;
        case WAITING:
          int x = (size.width - myWaitingIcon.getIconWidth()) / 2;
          int y = (size.height - myWaitingIcon.getIconHeight()) / 2;
          myWaitingIcon.paintIcon(this, g, x, y);
          break;
      }
    }
  }

  private static class URLInputVaslidator implements InputValidator {
    @Override
    public boolean checkInput(String inputString) {
      try {
        final URL url = new URL(inputString);
        return StringUtil.isNotEmpty(url.getHost());
      }
      catch (MalformedURLException e) {
        return false;
      }
    }

    @Override
    public boolean canClose(String inputString) {
      return checkInput(inputString);
    }
  }
}
