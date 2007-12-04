package com.intellij.openapi.vcs.configurable;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.IssueNavigationConfiguration;
import com.intellij.openapi.vcs.IssueNavigationLink;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class IssueNavigationConfigurationPanel extends JPanel {
  private JPanel myPanel;
  private JTable myLinkTable;
  private JButton myAddButton;
  private JButton myEditButton;
  private JButton myDeleteButton;
  private JButton myAddJiraPatternButton;
  private final Project myProject;
  private List<IssueNavigationLink> myLinks;
  private ListTableModel<IssueNavigationLink> myModel;

  private ColumnInfo<IssueNavigationLink, String> ISSUE_COLUMN = new ColumnInfo<IssueNavigationLink, String>(VcsBundle.message("issue.link.issue.column")) {
    public String valueOf(IssueNavigationLink issueNavigationLink) {
      return issueNavigationLink.getIssueRegexp();
    }
  };
  private ColumnInfo<IssueNavigationLink, String> LINK_COLUMN = new ColumnInfo<IssueNavigationLink, String>(VcsBundle.message("issue.link.link.column")) {
    public String valueOf(IssueNavigationLink issueNavigationLink) {
      return issueNavigationLink.getLinkRegexp();
    }
  };

  public IssueNavigationConfigurationPanel(Project project) {
    super(new BorderLayout());
    myProject = project;
    add(myPanel, BorderLayout.CENTER);
    reset();
    myAddButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        IssueLinkConfigurationDialog dlg = new IssueLinkConfigurationDialog(myProject);
        dlg.setTitle(VcsBundle.message("issue.link.add.title"));
        dlg.show();
        if (dlg.isOK()) {
          myLinks.add(dlg.getLink());
          myModel.fireTableDataChanged();
        }
      }
    });
    myAddJiraPatternButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        String s = Messages.showInputDialog(IssueNavigationConfigurationPanel.this, "Enter JIRA installation URL:",
                                            "Add JIRA Issue Navigation Pattern", Messages.getQuestionIcon());
        if (s == null) {
          return;
        }
        if (!s.endsWith("/")) {
          s += "/";
        }
        myLinks.add(new IssueNavigationLink("[A-Z]+\\-\\d+", s + "browse/$0"));
        myModel.fireTableDataChanged();
      }
    });
    myEditButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        IssueNavigationLink link = (IssueNavigationLink) myModel.getItem(myLinkTable.getSelectedRow());
        IssueLinkConfigurationDialog dlg = new IssueLinkConfigurationDialog(myProject);
        dlg.setTitle(VcsBundle.message("issue.link.edit.title"));
        dlg.setLink(link);
        dlg.show();
        if (dlg.isOK()) {
          final IssueNavigationLink editedLink = dlg.getLink();
          link.setIssueRegexp(editedLink.getIssueRegexp());
          link.setLinkRegexp(editedLink.getLinkRegexp());
          myModel.fireTableDataChanged();
        }
      }
    });
    myDeleteButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (Messages.showOkCancelDialog(myProject, VcsBundle.message("issue.link.delete.prompt"),
                                        VcsBundle.message("issue.link.delete.title"), Messages.getQuestionIcon()) == 0) {
          int selRow = myLinkTable.getSelectedRow();
          myLinks.remove(selRow);
          myModel.fireTableDataChanged();
          if (myLinkTable.getRowCount() > 0) {
            if (selRow >= myLinkTable.getRowCount()) {
              selRow--;
            }
            myLinkTable.getSelectionModel().setSelectionInterval(selRow, selRow);
          }
        }
      }
    });
    myLinkTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(final ListSelectionEvent e) {
        updateButtons();
      }
    });
    updateButtons();
  }

  private void updateButtons() {
    myEditButton.setEnabled(myLinkTable.getSelectedRow() >= 0);
    myDeleteButton.setEnabled(myEditButton.isEnabled());
  }

  public void apply() {
    IssueNavigationConfiguration configuration = IssueNavigationConfiguration.getInstance(myProject);
    configuration.setLinks(myLinks);
  }

  public boolean isModified() {
    IssueNavigationConfiguration configuration = IssueNavigationConfiguration.getInstance(myProject);
    return !myLinks.equals(configuration.getLinks());
  }

  public void reset() {
    IssueNavigationConfiguration configuration = IssueNavigationConfiguration.getInstance(myProject);
    myLinks = new ArrayList<IssueNavigationLink>();
    for(IssueNavigationLink link: configuration.getLinks()) {
      myLinks.add(new IssueNavigationLink(link.getIssueRegexp(), link.getLinkRegexp()));
    }
    myModel = new ListTableModel<IssueNavigationLink>(
      new ColumnInfo[] { ISSUE_COLUMN, LINK_COLUMN },
      myLinks,
      0);
    myLinkTable.setModel(myModel);
  }
}