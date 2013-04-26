package com.jetbrains.python.packaging.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.ui.table.JBTable;
import com.intellij.util.CatchingConsumer;
import com.intellij.util.Function;
import com.intellij.util.ui.PlatformColors;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.UiNotifyConnector;
import com.jetbrains.python.packaging.PyPIPackageUtil;
import com.jetbrains.python.packaging.PyPackageManagerImpl;
import com.jetbrains.python.packaging.PyPackageService;
import com.jetbrains.python.packaging.RepoPackage;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLFrameHyperlinkEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.List;

/**
 * User: catherine
 * <p/>
 * UI for installing python packages
 */
@SuppressWarnings("UseOfObsoleteCollectionType")
public class ManagePackagesDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance(ManagePackagesDialog.class);

  private final PackageManagerController myController;

  private JPanel myFilter;
  private JPanel myMainPanel;
  private JEditorPane myDescriptionTextArea;
  private JBList myPackages;
  private JButton myInstallButton;
  private JCheckBox myOptionsCheckBox;
  private JTextField myOptionsField;
  private JCheckBox myInstallToUser;
  private JComboBox myVersionComboBox;
  private JCheckBox myVersionCheckBox;
  private JButton myManageButton;
  private final PyPackagesNotificationPanel myNotificationArea;
  private JPanel myPackagesPanel;
  private JSplitPane mySplitPane;
  private JPanel myNotificationsAreaPlaceholder;
  private PackagesModel myPackagesModel;
  private final Set<String> myInstalledPackages;
  private final PyPackagesPanel myPackageListPanel;

  private Set<String> currentlyInstalling = new HashSet<String>();
  protected final ListSpeedSearch myListSpeedSearch;

  public ManagePackagesDialog(@NotNull Project project, @NotNull final Sdk sdk, @NotNull final PyPackagesPanel packageListPanel,
                              final PackageManagerController packageManagerController) {
    super(project, true);
    myController = packageManagerController;

    myInstallToUser.setEnabled(!PythonSdkType.isVirtualEnv(sdk));
    myInstallToUser.setSelected(false);

    myPackageListPanel = packageListPanel;
    init();
    final JBTable table = myPackageListPanel.getPackagesTable();
    setTitle("Available Packages");
    myPackages = new JBList();
    myNotificationArea = new PyPackagesNotificationPanel(project);
    myNotificationsAreaPlaceholder.add(myNotificationArea.getComponent(), BorderLayout.CENTER);

    final AnActionButton reloadButton = new AnActionButton("Reload List of Packages", AllIcons.Actions.Refresh) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        myPackages.setPaintBusy(true);
        final Application application = ApplicationManager.getApplication();
        application.executeOnPooledThread(new Runnable() {
          @Override
          public void run() {
            try {
              myController.reloadPackagesList();
              myPackages.setPaintBusy(false);
            }
            catch (IOException e) {
              application.invokeLater(new Runnable() {
                @Override
                public void run() {
                  Messages.showErrorDialog("Failed to update package list. Please, check your internet connection.",
                                           "Update Package List Failed");
                  myPackages.setPaintBusy(false);
                }
              }, ModalityState.any());
            }
          }
        });
      }
    };
    myListSpeedSearch = new ListSpeedSearch(this.myPackages, new Function<Object, String>() {
      @Override
      public String fun(Object o) {
        if (o instanceof RepoPackage)
          return ((RepoPackage)o).getName();
        return "";
      }
    });
    myPackagesPanel = ToolbarDecorator.createDecorator(this.myPackages).disableAddAction().
      disableUpDownActions().disableRemoveAction().addExtraAction(reloadButton).createPanel();
    myPackagesPanel.setPreferredSize(new Dimension(400, -1));
    mySplitPane.setLeftComponent(myPackagesPanel);

    myPackages.addListSelectionListener(new MyPackageSelectionListener());
    if (myInstallToUser.isEnabled())
      myInstallToUser.setSelected(PyPackageService.getInstance().useUserSite(sdk.getHomePath()));
    myInstallToUser.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent event) {
        PyPackageService.getInstance().addSdkToUserSite(sdk.getHomePath(), myInstallToUser.isSelected());
      }
    });
    myOptionsCheckBox.setEnabled(false);
    myVersionCheckBox.setEnabled(false);
    myVersionCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent event) {
        myVersionComboBox.setEnabled(myVersionCheckBox.isSelected());
      }
    });

    UiNotifyConnector.doWhenFirstShown(myPackages, new Runnable() {
      @Override
      public void run() {
        initModel();
      }
    });
    myOptionsCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent event) {
        myOptionsField.setEnabled(myOptionsCheckBox.isSelected());
      }
    });
    myInstallButton.setEnabled(false);
    myDescriptionTextArea.addHyperlinkListener(new MyHyperlinkListener());
    addInstallAction(sdk, table);
    myInstalledPackages = new HashSet<String>();
    updateInstalledNames(table);
    addManageAction();
    String userSiteText = "Install to user's site packages directory";
    if (!PythonSdkType.isRemote(sdk))
      userSiteText += " (" + PyPackageManagerImpl.getUserSite() + ")";
    myInstallToUser.setText(userSiteText);
  }

  public void setSelected(String pyPackage) {
    myPackages.setSelectedValue(new RepoPackage(pyPackage, PyPIPackageUtil.PYPI_URL), true);
  }

  void updateInstalledNames(JBTable table) {
    for (Object p : ((DefaultTableModel)table.getModel()).getDataVector()) {
      myInstalledPackages.add(((Vector)p).get(0).toString());
    }
  }

  private void addManageAction() {
    myManageButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent event) {
        ManageRepoDialog dialog = new ManageRepoDialog();
        dialog.show();
      }
    });
    myPackages.setCellRenderer(new MyTableRenderer());
  }

  private void addInstallAction(@NotNull final Sdk sdk,
                                @NotNull final JBTable table) {
    myInstallButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent event) {
        final Object pyPackage = myPackages.getSelectedValue();
        if (pyPackage instanceof RepoPackage) {
          final RepoPackage repoPackage = (RepoPackage)pyPackage;
          final String packageName = repoPackage.getName();

          String extraOptions = null;
          if (myOptionsCheckBox.isEnabled() && myOptionsCheckBox.isSelected()) {
            extraOptions = myOptionsField.getText();
          }

          String version = null;
          if (myVersionCheckBox.isEnabled() && myVersionCheckBox.isSelected()) {
            version = (String) myVersionComboBox.getSelectedItem();
          }

          final PackageManagerController.Listener listener = new PackageManagerController.Listener() {
            @Override
            public void installationStarted() {
              setDownloadStatus(true);
              table.setPaintBusy(true);
              currentlyInstalling.add(packageName);
            }

            @Override
            public void installationFinished(@Nullable String errorDescription) {
              table.clearSelection();
              setDownloadStatus(false);
              addNotifications(errorDescription, packageName, myNotificationArea, myPackageListPanel.getNotificationsArea());
              myPackageListPanel.updatePackages(sdk, myInstalledPackages);
              currentlyInstalling.remove(packageName);
            }
          };
          myController.installPackage(packageName, repoPackage.getRepoUrl(), version, myInstallToUser.isSelected(), extraOptions, listener);
          myInstallButton.setEnabled(false);
        }
      }
    });
  }

  private static void addNotifications(final String errorDescription,
                                       final String packageName,
                                       final PyPackagesNotificationPanel... areas) {
    for (PyPackagesNotificationPanel pane : areas) {
      if (StringUtil.isEmpty(errorDescription)) {
        pane.showSuccess("Package successfully installed.");
      }
      else {
        String title = "Install packages failed";
        final String firstLine = title + ": Error occurred when installing package " + packageName + ". ";
        pane.showError(firstLine + "<a href=\"xxx\">Details...</a>",
                       title,
                       firstLine + errorDescription);
      }
    }
  }

  public void initModel() {
    setDownloadStatus(true);
    final Application application = ApplicationManager.getApplication();
    application.executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        try {
          myPackagesModel = new PackagesModel(myController.getAllPackages());

          application.invokeLater(new Runnable() {
            @Override
            public void run() {
              myPackages.setModel(myPackagesModel);
              setDownloadStatus(false);
            }
          }, ModalityState.any());
        }
        catch (final IOException e) {
          application.invokeLater(new Runnable() {
            @Override
            public void run() {
              Messages.showErrorDialog("IO Error occurred. Could not reach url " + e.getMessage() +
                                       ". Please, check your internet connection.", "Packages");
              setDownloadStatus(false);
            }
          }, ModalityState.any());
        }
      }
    });
  }

  protected void setDownloadStatus(boolean status) {
    myPackages.setPaintBusy(status);
  }

  @Override
  protected JComponent createCenterPanel() {
    return myMainPanel;
  }

  private void createUIComponents() {
    myFilter = new MyPackageFilter();
  }

  public class MyPackageFilter extends FilterComponent {

    public MyPackageFilter() {
      super("PACKAGE_FILTER", 5);
    }

    public void filter() {
      if (myPackagesModel != null)
        myPackagesModel.filter(getFilter());
    }
  }

  private class PackagesModel extends CollectionListModel<RepoPackage> {
    protected final List<RepoPackage> myFilteredOut = new ArrayList<RepoPackage>();
    protected List<RepoPackage> myView = new ArrayList<RepoPackage>();

    public PackagesModel(List<RepoPackage> packages) {
      super(packages);
      myView = packages;
    }

    public void add(String urlResource, String element) {
      super.add(new RepoPackage(element, urlResource));
    }

    protected void filter(final String filter) {
      final Collection<RepoPackage> toProcess = toProcess();

      toProcess.addAll(myFilteredOut);
      myFilteredOut.clear();

      final ArrayList<RepoPackage> filtered = new ArrayList<RepoPackage>();

      for (RepoPackage repoPackage : toProcess) {
        if (StringUtil.containsIgnoreCase(repoPackage.getName(), filter)) {
          filtered.add(repoPackage);
        }
        else {
          myFilteredOut.add(repoPackage);
        }
      }
      filter(filtered);
    }

    public void filter(List<RepoPackage> filtered){
      myView.clear();
      myPackages.clearSelection();
      for (RepoPackage repoPackage : filtered) {
        myView.add(repoPackage);
      }
      Collections.sort(myView);
      fireContentsChanged(this, 0, myView.size());
    }

    @Override
    public RepoPackage getElementAt(int index) {
      return myView.get(index);
    }

    protected ArrayList<RepoPackage> toProcess() {
      return new ArrayList<RepoPackage>(myView);
    }

    @Override
    public int getSize() {
      return myView.size();
    }
  }

  @Nullable
  public JComponent getPreferredFocusedComponent() {
    return myFilter;
  }

  public static class MyHyperlinkListener implements HyperlinkListener {
    public void hyperlinkUpdate(HyperlinkEvent e) {
      if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
        JEditorPane pane = (JEditorPane)e.getSource();
        if (e instanceof HTMLFrameHyperlinkEvent) {
          HTMLFrameHyperlinkEvent evt = (HTMLFrameHyperlinkEvent)e;
          HTMLDocument doc = (HTMLDocument)pane.getDocument();
          doc.processHTMLFrameHyperlinkEvent(evt);
        }
        else {
          URL url = e.getURL();
          if (url != null) {
            BrowserUtil.browse(url);
          }
        }
      }
    }
  }

  public class MyPackageSelectionListener implements ListSelectionListener {
    @Override
    public void valueChanged(ListSelectionEvent event) {
      myOptionsCheckBox.setEnabled(myPackages.getSelectedIndex() >= 0);
      myVersionCheckBox.setEnabled(myPackages.getSelectedIndex() >= 0);
      myOptionsCheckBox.setSelected(false);
      myVersionCheckBox.setSelected(false);
      myVersionComboBox.setEnabled(false);
      myOptionsField.setEnabled(false);
      myDescriptionTextArea.setText("");

      setDownloadStatus(true);
      final Object pyPackage = myPackages.getSelectedValue();
      if (pyPackage instanceof RepoPackage) {
        final String packageName = ((RepoPackage)pyPackage).getName();
        myVersionComboBox.removeAllItems();
        if (myVersionCheckBox.isEnabled()) {
          myController.fetchPackageVersions(packageName, new CatchingConsumer<List<String>, Exception>() {
            @Override
            public void consume(final List<String> releases) {
              ApplicationManager.getApplication().invokeLater(new Runnable() {
                @Override
                public void run() {
                  if (myPackages.getSelectedValue() == pyPackage) {
                    myVersionComboBox.removeAllItems();
                    for (String release : releases) {
                      myVersionComboBox.addItem(release);
                    }
                  }
                }
              }, ModalityState.any());
            }

            @Override
            public void consume(Exception e) {
              LOG.info("Error retrieving releases", e);
            }
          });
        }
        myInstallButton.setEnabled(!currentlyInstalling.contains(packageName));

        myController.fetchPackageDetails(packageName, new CatchingConsumer<String, Exception>() {
          @Override
          public void consume(final String details) {
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              @Override
              public void run() {
                if (myPackages.getSelectedValue() == pyPackage) {
                  myDescriptionTextArea.setText(details);
                }
              }
            }, ModalityState.any());
          }

          @Override
          public void consume(Exception exception) {
            LOG.info("Error retrieving package details", exception);
          }
        });
      }
      else {
        myInstallButton.setEnabled(false);
      }
      setDownloadStatus(false);
    }
  }

  @NotNull
  protected Action[] createActions() {
    return new Action[0];
  }

  private class MyTableRenderer extends DefaultListCellRenderer {
    private JLabel myNameLabel = new JLabel();
    private JLabel myRepositoryLabel = new JLabel();
    private JPanel myPanel = new JPanel(new BorderLayout());

    private MyTableRenderer() {
      myPanel.setBorder(BorderFactory.createEmptyBorder(1, 0, 1, 1));
      myRepositoryLabel.setFont(UIUtil.getLabelFont(UIUtil.FontSize.SMALL));
      myPanel.add(myNameLabel, BorderLayout.WEST);
      myPanel.add(myRepositoryLabel, BorderLayout.EAST);
      myNameLabel.setOpaque(true);
      myPanel.setPreferredSize(new Dimension(300, 20));
    }

    @Override
    public Component getListCellRendererComponent(JList list,
                                                  Object value,
                                                  int index,
                                                  boolean isSelected,
                                                  boolean cellHasFocus) {
      if (value instanceof RepoPackage) {
        RepoPackage repoPackage = (RepoPackage) value;
        String name = repoPackage.getName();
        myNameLabel.setText(name);
        myRepositoryLabel.setText(repoPackage.getRepoUrl());
        Component orig = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        final Color fg = orig.getForeground();
        myNameLabel.setForeground(myInstalledPackages.contains(name) ? PlatformColors.BLUE : fg);
      }
      myRepositoryLabel.setForeground(Color.GRAY);

      final Color bg = isSelected ? UIUtil.getListSelectionBackground() : UIUtil.getListBackground();
      myPanel.setBackground(bg);
      myNameLabel.setBackground(bg);
      myRepositoryLabel.setBackground(bg);
      return myPanel;
    }
  }
}
