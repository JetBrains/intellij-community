package com.jetbrains.python.packaging.ui;

import com.google.common.collect.Lists;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.table.JBTable;
import com.jetbrains.python.packaging.*;
import com.jetbrains.python.sdk.PythonSdkType;
import org.apache.xmlrpc.AsyncCallback;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URL;
import java.util.*;
import java.util.List;

public class PyPackagesPanel extends JPanel {
  private final JButton myInstallButton;
  private final JButton myUninstallButton;
  private final JButton myUpgradeButton;

  private final JBTable myPackagesTable;
  private DefaultTableModel myPackagesTableModel;
  private Sdk mySelectedSdk;
  private final Project myProject;
  private final PyPackagesNotificationPanel myNotificationArea;
  private boolean myHasPip = true;

  public PyPackagesPanel(Project project, PyPackagesNotificationPanel area) {
    super(new GridBagLayout());
    myProject = project;
    myNotificationArea = area;
    myInstallButton = new JButton("Install");
    myUninstallButton = new JButton("Uninstall");
    myUpgradeButton = new JButton("Upgrade");
    myInstallButton.setMnemonic('I');
    myUninstallButton.setMnemonic('U');
    myUpgradeButton.setMnemonic('p');

    myInstallButton.setEnabled(false);
    myUninstallButton.setEnabled(false);
    myUpgradeButton.setEnabled(false);

    myPackagesTableModel = new DefaultTableModel(new String[]{"Package", "Version", "Latest"}, 0) {
      @Override
      public boolean isCellEditable(int i, int i1) {
        return false;
      }
    };
    final TableCellRenderer tableCellRenderer = new MyTableCellRenderer();
    myPackagesTable = new JBTable(myPackagesTableModel) {
      @Override
      public TableCellRenderer getCellRenderer(int row, int column) {
        return tableCellRenderer;
      }
    };

    Insets anInsets = new Insets(2, 2, 2, 2);
    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myPackagesTable,
                                                                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                                                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scrollPane.setPreferredSize(new Dimension(500, 500));
    add(scrollPane, new GridBagConstraints(0, 0, 1, 8, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                                           anInsets, 0, 0));

    addUninstallAction();
    addUpgradeAction();

    add(myInstallButton,
        new GridBagConstraints(1, 0, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
                               anInsets, 0, 0));
    add(myUninstallButton,
        new GridBagConstraints(1, 1, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
                               anInsets, 0, 0));
    add(myUpgradeButton,
        new GridBagConstraints(1, 2, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, anInsets, 0, 0));

    myPackagesTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent event) {
        updateUninstallUpgrade();
      }
    });

    myInstallButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (mySelectedSdk != null) {
          ManagePackagesDialog dialog = new ManagePackagesDialog(myProject, mySelectedSdk, PyPackagesPanel.this);
          dialog.show();
        }
      }
    });

    myPackagesTable.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2) {
          if (mySelectedSdk != null && myInstallButton.isEnabled()) {
            ManagePackagesDialog dialog = new ManagePackagesDialog(myProject, mySelectedSdk, PyPackagesPanel.this);
            Point p = e.getPoint();
            int row = myPackagesTable.rowAtPoint(p);
            int column = myPackagesTable.columnAtPoint(p);
            if (row >= 0 && column >= 0) {
              Object pyPackage = myPackagesTable.getValueAt(row, 0);
              if (pyPackage instanceof PyPackage) {
                dialog.setSelected(((PyPackage)pyPackage).getName());
              }
            }
            dialog.show();
          }
        }
      }
    });
  }

  public JBTable getPackagesTable() {
    return myPackagesTable;
  }

  public PyPackagesNotificationPanel getNotificationsArea() {
    return myNotificationArea;
  }

  public void setSdkStatus(boolean sdkValid, boolean hasPip) {
    myHasPip = hasPip;
    myInstallButton.setEnabled(sdkValid && hasPip);
  }

  private void addUpgradeAction() {
    myUpgradeButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final int[] rows = myPackagesTable.getSelectedRows();
        final Sdk selectedSdk = mySelectedSdk;
        if (selectedSdk != null) {
          final List<PyRequirement> requirements = new ArrayList<PyRequirement>();
          for (int row : rows) {
            final Object pyPackage = myPackagesTableModel.getValueAt(row, 0);
            if (pyPackage instanceof PyPackage) {
              final String packageName = ((PyPackage)pyPackage).getName();
              final Object currentVersion = myPackagesTableModel.getValueAt(row, 1);

              PyPIPackageUtil.INSTANCE.usePackageReleases(packageName, new AsyncCallback() {
                @Override
                public void handleResult(Object result, URL url, String method) {
                  final List<String> releases = (List<String>)result;
                  PyPIPackageUtil.INSTANCE.addPackageReleases(packageName, releases);
                  if (releases.isEmpty() ||
                      PyRequirement.VERSION_COMPARATOR.compare((String)currentVersion, releases.get(0)) < 0) {
                    requirements.add(new PyRequirement(packageName));
                  }
                  ApplicationManager.getApplication().invokeLater(new Runnable() {
                    @Override
                    public void run() {
                      PyPackageManager.UI ui =
                        new PyPackageManager.UI(myProject, selectedSdk, new PyPackageManager.UI.Listener() {
                          @Override
                          public void started() {
                            myPackagesTable.setPaintBusy(true);
                          }

                          @Override
                          public void finished(final List<PyExternalProcessException> exceptions) {
                            myPackagesTable.clearSelection();
                            updatePackages(selectedSdk);
                            myPackagesTable.setPaintBusy(false);

                            if (exceptions.isEmpty()) {
                              myNotificationArea.showSuccess("Package successfully upgraded");
                            }
                            else {
                              myNotificationArea.showError("Upgrade packages failed. <a href=\"xxx\">Details...</a>",
                                                           "Upgrade Packages Failed",
                                                           PyPackageManager.UI.createDescription(exceptions, "Upgrade packages failed."));
                            }
                          }
                        });
                      ui.install(requirements, Collections.singletonList("-U"));
                    }
                  }, ModalityState.any());
                }

                @Override
                public void handleError(Exception exception, URL url, String method) {
                  ApplicationManager.getApplication().invokeLater(new Runnable() {
                    @Override
                    public void run() {
                      Messages.showErrorDialog("Error occurred. Please, check your internet connection.",
                                               "Upgrade Package Failed.");
                    }
                  }, ModalityState.any());
                }
              });
            }
          }
        }
      }
    });
  }

  private void updateUninstallUpgrade() {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        final int[] selected = myPackagesTable.getSelectedRows();
        boolean isAvailabe = mySelectedSdk != null && selected.length != 0 && myHasPip;
        boolean upgradeAvailable = false;
        boolean isPipOrDistribute = false;
        boolean isInUserSite = true;
        final String userSite = PyPackageManager.getUserSite();
        if (isAvailabe) {
          for (int i = 0; i != selected.length; ++i) {
            final int index = selected[i];
            if (index >= myPackagesTable.getRowCount()) continue;
            final Object value = myPackagesTable.getValueAt(index, 0);
            if (value instanceof PyPackage) {
              final PyPackage pyPackage = (PyPackage)value;
              final String pyPackageName = pyPackage.getName();
              final String availableVersion = (String)myPackagesTable.getValueAt(index, 2);
              upgradeAvailable = PyRequirement.VERSION_COMPARATOR.compare(pyPackage.getVersion(), availableVersion) < 0;
              isPipOrDistribute = "pip".equals(pyPackageName) || "distribute".equals(pyPackageName);
              isAvailabe = !isPipOrDistribute;

              final String location = pyPackage.getLocation();
              if (isInUserSite && location != null) {
                isInUserSite = location.startsWith(userSite);
              }
              if (!isAvailabe) break;
            }
          }
        }
        final boolean isVEnv = PythonSdkType.isVirtualEnv(mySelectedSdk);
        myUninstallButton.setEnabled(isAvailabe && (!isVEnv || !isInUserSite));
        myUpgradeButton.setEnabled(upgradeAvailable && isAvailabe || isPipOrDistribute);
      }
    }, ModalityState.any());
  }

  private void addUninstallAction() {
    myUninstallButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        final List<PyPackage> packages = getSelectedPackages();
        final Sdk sdk = mySelectedSdk;
        if (sdk != null) {
          PyPackageManager.UI ui = new PyPackageManager.UI(myProject, sdk, new PyPackageManager.UI.Listener() {
            @Override
            public void started() {
              myPackagesTable.setPaintBusy(true);
            }

            @Override
            public void finished(final List<PyExternalProcessException> exceptions) {
              myPackagesTable.clearSelection();
              updatePackages(sdk);
              myPackagesTable.setPaintBusy(false);
              if (exceptions.isEmpty()) {
                myNotificationArea.showSuccess("Packages successfully uninstalled");
              }
              else {
                myNotificationArea.showError("Uninstall packages failed. <a href=\"xxx\">Details...</a>",
                                             "Uninstall Packages Failed",
                                             PyPackageManager.UI.createDescription(exceptions, "Uninstall packages failed."));
              }
            }
          });
          ui.uninstall(packages);
        }
      }
    });
  }

  @NotNull
  private List<PyPackage> getSelectedPackages() {
    final List<PyPackage> results = new ArrayList<PyPackage>();
    final int[] rows = myPackagesTable.getSelectedRows();
    for (int row : rows) {
      final Object packageName = myPackagesTableModel.getValueAt(row, 0);
      if (packageName instanceof PyPackage) {
        results.add((PyPackage)packageName);
      }
    }
    return results;
  }

  public void updatePackages(Sdk selectedSdk) {
    mySelectedSdk = selectedSdk;
    myPackagesTable.clearSelection();
    myPackagesTableModel.getDataVector().clear();
    updatePackages(selectedSdk, null);
  }

  public void updatePackages(final Sdk selectedSdk, @Nullable final Set<String> installed) {
    myPackagesTable.setPaintBusy(true);
    final Application application = ApplicationManager.getApplication();
    application.executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        List<PyPackage> packages = Lists.newArrayList();
        if (selectedSdk != null) {
          try {
            packages = PyPackageManager.getInstance(selectedSdk).getPackages();
          }
          catch (PyExternalProcessException e) {
            // do nothing, we already have an empty list
          }
          finally {
            final List<PyPackage> finalPackages = packages;
            application.invokeLater(new Runnable() {
              @Override
              public void run() {
                final Map<String, String> cache = PyPIPackageUtil.getPyPIPackages();
                if (selectedSdk == mySelectedSdk) {
                  myPackagesTableModel.getDataVector().clear();
                  for (PyPackage pyPackage : finalPackages) {
                    if (installed != null) {
                      installed.add(pyPackage.getName());
                    }
                    final String version = cache.get(pyPackage.getName());
                    myPackagesTableModel
                      .addRow(new Object[]{pyPackage, pyPackage.getVersion(), version == null ? "" : version});
                  }
                  myPackagesTable.setPaintBusy(false);
                }
              }
            }, ModalityState.any());
          }
        }
      }
    });
  }

  private static class MyTableCellRenderer extends DefaultTableCellRenderer {
    @Override
    public Component getTableCellRendererComponent(final JTable table, final Object value, final boolean isSelected,
                                                   final boolean hasFocus, final int row, final int column) {
      final JLabel cell = (JLabel)super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      final String version = (String)table.getValueAt(row, 1);
      final String availableVersion = (String)table.getValueAt(row, 2);
      cell.setIcon(PyRequirement.VERSION_COMPARATOR.compare(version, availableVersion) < 0 && column == 2 ?
                   IconLoader.getIcon("/vcs/arrow_right.png") : null);
      final Object pyPackage = table.getValueAt(row, 0);
      if (pyPackage instanceof PyPackage) {
        cell.setToolTipText(PythonSdkType.shortenDirName(((PyPackage)pyPackage).getLocation()));
      }
      return cell;
    }
  }
}
