// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.ide.customize.AbstractCustomizeWizardStep;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileChooser.PathChooserDialog;
import com.intellij.openapi.fileChooser.impl.FileChooserFactoryImpl;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.local.CoreLocalFileSystem;
import com.intellij.openapi.vfs.local.CoreLocalVirtualFile;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.python.actions.InstallCondaActionImpl;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.concurrent.ExecutorService;

import static javax.swing.SwingUtilities.invokeLater;

/**
 * @author Aleksey.Rostovskiy
 */
public class PyCharmCustomizeCondaSetupStep extends AbstractCustomizeWizardStep {
  private final ExecutorService executorService = AppExecutorUtil.createBoundedApplicationPoolExecutor(
    "PyCharmCustomizeCondaSetupStep Pool", 1);
  private static final Logger LOG = Logger.getInstance(PyCharmCustomizeCondaSetupStep.class);
  private final ProgressIndicator myProgressIndicator = new ProgressIndicatorBase();

  private final JButton myInstallButton;
  private final TextFieldWithBrowseButton mySetupCondaFileChooser;
  private final JProgressBar myProgressBar;
  private final JPanel myProgressPanel;
  private final LinkLabel myCancelLink;

  private VirtualFile myLastSelection;

  public PyCharmCustomizeCondaSetupStep() {
    setLayout(new BorderLayout());

    myProgressBar = new JProgressBar(0, 100);
    myProgressBar.setStringPainted(true);
    myProgressBar.setIndeterminate(true);

    myCancelLink = new LinkLabel("Cancel", null);
    myCancelLink.setVisible(false);
    myCancelLink.setListener((aSource, aLinkData) -> {
      myProgressIndicator.cancel();
      showErrorDialog(PyBundle.message("action.SetupMiniconda.installCanceled"), false);
      unlockElementsAfterInstall();
    }, null);

    final JPanel linkWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
    linkWrapper.add(myCancelLink);

    myProgressPanel = new JPanel(new VerticalFlowLayout(true, false));
    myProgressPanel.add(myProgressBar);
    myProgressPanel.add(linkWrapper);
    myProgressPanel.setEnabled(true);
    myProgressPanel.setVisible(false);

    final File installationPath = InstallCondaActionImpl.getDefaultDirectoryFile();
    myLastSelection = new CoreLocalFileSystem().findFileByIoFile(installationPath);

    myInstallButton = new JButton(PyBundle.message("action.SetupMiniconda.actionName"));
    myInstallButton.addActionListener(e -> installButtonActionListener());

    mySetupCondaFileChooser = new TextFieldWithBrowseButton();
    mySetupCondaFileChooser.setText(installationPath.getAbsolutePath());
    mySetupCondaFileChooser.addActionListener(e -> setupFileChooserActionListener());

    JPanel controls = new JPanel(new GridBagLayout());
    controls.setOpaque(false);
    GridBag gbc = new GridBag()
      .setDefaultAnchor(GridBagConstraints.WEST)
      .setDefaultFill(GridBagConstraints.BOTH)
      .setDefaultWeightX(1);

    gbc.insets.top = UIUtil.PANEL_REGULAR_INSETS.top;
    gbc.insets.left = UIUtil.PANEL_REGULAR_INSETS.left;

    controls.add(myInstallButton, gbc.anchor(GridBagConstraints.NORTHWEST));
    gbc.nextLine();
    JLabel specifyPath = new JLabel(wrapInHtml(PyBundle.message("action.SetupMiniconda.specifyPath") + "<br>"));
    controls.add(specifyPath, gbc.nextLine());
    controls.add(mySetupCondaFileChooser, gbc.nextLine());
    JLabel description = new JLabel(wrapInHtml(PyBundle.message("action.SetupMiniconda.description") + "<br>"));
    controls.add(description, gbc.nextLine());
    controls.add(myProgressPanel, gbc.nextLine());

    JPanel content = new JPanel(createSmallBorderLayout());
    content.add(controls, BorderLayout.NORTH);
    add(content, BorderLayout.CENTER);
  }

  // TODO button `Start using PyCharm` can be blocked this way, but it's better not
  //@Override
  //public boolean beforeOkAction() {
  //  return !myProgressPanel.isVisible();
  //}

  private static void showErrorDialog(@NotNull String message, boolean log) {
    if (log) LOG.error(message);
    invokeLater(() -> Messages.showErrorDialog(message, PyBundle.message("action.SetupMiniconda.installFailed")));
  }

  private void lockElementsOnInstall() {
    myInstallButton.setEnabled(false);
    mySetupCondaFileChooser.setEnabled(false);
    myProgressBar.setString("Installing...");
    myProgressPanel.setVisible(true);
    myCancelLink.setVisible(true);
  }

  private void unlockElementsAfterInstall() {
    mySetupCondaFileChooser.setEnabled(true);
    myInstallButton.setEnabled(true);
    myProgressPanel.setVisible(false);
    myCancelLink.setVisible(false);
  }

  private void installButtonActionListener() {
    String path = InstallCondaActionImpl.beatifyPath(mySetupCondaFileChooser.getText());
    String errorMessage = InstallCondaActionImpl.checkPath(path);

    if (errorMessage != null) {
      showErrorDialog(errorMessage, false);
      return;
    }
    lockElementsOnInstall();

    final ProcessOutput[] processOutput = new ProcessOutput[1];

    executorService.submit(() -> {
      try {
        CapturingProcessHandler handler = InstallCondaActionImpl.installationHandler(path, (line) -> {
          myProgressBar.setString(line);
          return Unit.INSTANCE;
        });

        processOutput[0] = handler.runProcessWithProgressIndicator(myProgressIndicator);

        LOG.info(processOutput[0].getStdout());
        int exitCode = processOutput[0].getExitCode();

        if (exitCode == 0) {
          myInstallButton.setText("Installed");
        }
        else {
          showErrorDialog(processOutput[0].getStderr(), true);
          unlockElementsAfterInstall();
        }
      }
      catch (Exception e) {
        showErrorDialog(processOutput[0].getStderr(), true);
        unlockElementsAfterInstall();
      }
      finally {
        myProgressPanel.setVisible(false);
      }
    });
  }

  private void setupFileChooserActionListener() {
    FileChooserDescriptor chooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    chooserDescriptor.setHideIgnored(false);
    chooserDescriptor.withFileFilter(file -> file.isDirectory());
    Ref<VirtualFile> fileRef = Ref.create();
    PathChooserDialog chooser = FileChooserFactoryImpl.createNativePathChooserIfEnabled(chooserDescriptor, null, this);

    if (chooser == null) {
      File lastSelectedFile = myLastSelection == null ? null : VfsUtilCore.virtualToIoFile(myLastSelection);
      JFileChooser fc = new JFileChooser(lastSelectedFile == null ? null : lastSelectedFile.getParentFile());
      fc.setSelectedFile(lastSelectedFile);
      fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
      fc.setFileHidingEnabled(SystemInfo.isWindows || SystemInfo.isMac);

      int returnVal = fc.showOpenDialog(this);
      if (returnVal == JFileChooser.APPROVE_OPTION) {
        File file = fc.getSelectedFile();
        if (file != null) {
          fileRef.set(new CoreLocalVirtualFile(new CoreLocalFileSystem(), file));
          mySetupCondaFileChooser.setText(file.getAbsolutePath());
        }
      }
    }
    else {
      chooser.choose(myLastSelection, files -> fileRef.set(files.get(0)));
    }

    if (!fileRef.isNull()) {
      File file = VfsUtilCore.virtualToIoFile(fileRef.get());
      myLastSelection = fileRef.get();
      mySetupCondaFileChooser.setText(file.getAbsolutePath());
    }
  }

  @Override
  public String getTitle() {
    return "Featured tools";
  }

  @Override
  protected String getHTMLHeader() {
    return wrapInHtml("<h2>Miniconda</h2>");
  }

  @Override
  protected String getHTMLFooter() {
    return "Miniconda can be installed later via Tools | " + PyBundle.message("action.SetupMiniconda.actionNameWithDots");
  }

  private static String wrapInHtml(@NotNull String text) {
    return "<html><body>" + text + "</body></html>";
  }
}
