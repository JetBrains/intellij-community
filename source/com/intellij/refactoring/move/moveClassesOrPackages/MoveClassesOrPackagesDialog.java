package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.ide.util.DirectoryChooser;
import com.intellij.ide.util.DirectoryChooserModuleTreeView;
import com.intellij.ide.util.PackageChooserDialog;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.refactoring.MoveDestination;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.RefactoringDialog;
import com.intellij.refactoring.RefactoringSettings;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.NonFocusableCheckBox;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MoveClassesOrPackagesDialog extends RefactoringDialog {
  public static interface Callback {
    void run(MoveClassesOrPackagesDialog dialog);
  }

  private static final Logger LOG = Logger.getInstance(
    "#com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesDialog");


  private final JLabel myNameLabel;
  private final JLabel myPromptTo;
  private final TextFieldWithBrowseButton myTextFieldWithBrowseButton;
  private JCheckBox myCbSearchInComments;
  private JCheckBox myCbSearchInNonJavaFiles;
  private JCheckBox myCbPreserveSourceFolders;
  private String myHelpID;
  private Project myProject;
  private final Callback myCallback;
  private boolean mySearchInNonJavaEnabled;
  private PsiDirectory myInitialTargetDirectory;
  private final PsiManager myManager;
  private MoveDestination myMoveDestination;
  private boolean myTargetDirectoryFixed;


  public MoveClassesOrPackagesDialog(Project project, Callback callback, boolean searchInNonJavaEnabled) {
    super(project, true);
    setTitle("Move");
    myProject = project;
    myCallback = callback;
    mySearchInNonJavaEnabled = searchInNonJavaEnabled;

    myNameLabel = new JLabel();
    myPromptTo = new JLabel("To package: ");
    myTextFieldWithBrowseButton = new TextFieldWithBrowseButton();

    init();
    myManager = PsiManager.getInstance(myProject);
  }

  public JComponent getPreferredFocusedComponent() {
    return myTextFieldWithBrowseButton.getTextField();
  }

  protected JComponent createCenterPanel() {
    return null;
  }

  protected JComponent createNorthPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();

    panel.setBorder(IdeBorderFactory.createBorder());

    gbConstraints.insets = new Insets(4, 8, 4, 8);
    gbConstraints.weighty = 1;
    gbConstraints.weightx = 1;
    gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gbConstraints.fill = GridBagConstraints.BOTH;
    panel.add(myNameLabel, gbConstraints);

    gbConstraints.gridx = 0;
    gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gbConstraints.fill = GridBagConstraints.BOTH;
    gbConstraints.weightx = 1;
    gbConstraints.anchor = GridBagConstraints.CENTER;
    myTextFieldWithBrowseButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          PackageChooserDialog chooser = new PackageChooserDialog("Choose Destination Package", myProject);
          chooser.selectPackage(myTextFieldWithBrowseButton.getText());
          chooser.show();
          PsiPackage aPackage = chooser.getSelectedPackage();
          if (aPackage != null) {
            myTextFieldWithBrowseButton.setText(aPackage.getQualifiedName());
            validateOKButton();
          }
        }
      }
    );
    JPanel _panel = new JPanel(new BorderLayout(4, 0));
    _panel.add(myPromptTo, BorderLayout.WEST);
    _panel.add(myTextFieldWithBrowseButton, BorderLayout.CENTER);
    panel.add(_panel, gbConstraints);

    gbConstraints.gridx = 0;
    gbConstraints.weightx = 1;
    gbConstraints.gridwidth = 1;
    gbConstraints.fill = GridBagConstraints.BOTH;
    myCbSearchInComments =
    new NonFocusableCheckBox("Search in comments and strings");
    myCbSearchInComments.setMnemonic('S');
    panel.add(myCbSearchInComments, gbConstraints);

    gbConstraints.gridx = 1;
    gbConstraints.weightx = 1;
    gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gbConstraints.fill = GridBagConstraints.BOTH;
    myCbSearchInNonJavaFiles =
    new NonFocusableCheckBox("Search in non-java files");
    myCbSearchInNonJavaFiles.setMnemonic('e');
    panel.add(myCbSearchInNonJavaFiles, gbConstraints);


    if (!mySearchInNonJavaEnabled) {
      myCbSearchInNonJavaFiles.setEnabled(false);
      myCbSearchInNonJavaFiles.setVisible(false);
      myCbSearchInNonJavaFiles.setSelected(false);
    }

    gbConstraints.gridx = 0;
    //gbConstraints.gridy = 1;
    gbConstraints.gridwidth = 2;
    myCbPreserveSourceFolders =
    new NonFocusableCheckBox("Preserve source folders");
    myCbPreserveSourceFolders.setMnemonic('r');
    panel.add(myCbPreserveSourceFolders, gbConstraints);


    myTextFieldWithBrowseButton.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      public void textChanged(DocumentEvent event) {
        validateOKButton();
      }
    });

    return panel;
  }

  protected String getDimensionServiceKey() {
    return "#com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesDialog";
  }

  public void setData(PsiElement[] psiElements,
                      String targetPackageName,
                      PsiDirectory initialTargetDirectory,
                      boolean isTargetDirectoryFixed,
                      boolean searchInComments,
                      boolean searchInNonJavaFiles,
                      String helpID) {
    myInitialTargetDirectory = initialTargetDirectory;
    myTargetDirectoryFixed = isTargetDirectoryFixed;
    if (psiElements.length == 1) {
      PsiElement firstElement = psiElements[0];
      PsiElement parent = firstElement.getParent();
      LOG.assertTrue(parent != null);
      String parentLongName = UsageViewUtil.getLongName(parent);
      String text = "Move " + UsageViewUtil.getType(firstElement) + " ";
      if (!"".equals(parentLongName)) {
        text += UsageViewUtil.getShortName(firstElement) + " from " + parentLongName;
      }
      else {
        text += UsageViewUtil.getLongName(firstElement);
      }
      myNameLabel.setText(text);
    }
    else if (psiElements.length > 1) {
      myNameLabel.setText((psiElements[0] instanceof PsiClass) ? "Move specified classes" : "Move specified packages");
    }
    myTextFieldWithBrowseButton.setText(targetPackageName);
    //myTextFieldWithBrowseButton.setEnabled(!myTargetDirectoryFixed);

    myCbSearchInComments.setSelected(searchInComments);
    myCbSearchInNonJavaFiles.setSelected(searchInNonJavaFiles);

    if (getSourceRoots().length == 1) {
      myCbPreserveSourceFolders.setSelected(true);
      myCbPreserveSourceFolders.setEnabled(false);
    }
    else {
      myCbPreserveSourceFolders.setSelected(!myTargetDirectoryFixed);
      //myCbPreserveSourceFolders.setEnabled(!myTargetDirectoryFixed);
    }

    validateOKButton();
    myHelpID = helpID;
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(myHelpID);
  }

  public boolean isSearchInComments() {
    return myCbSearchInComments.isSelected();
  }

  private void validateOKButton() {
    String name = myTextFieldWithBrowseButton.getText();
    if (name.length() == 0) {
      setOKActionEnabled(true);
    }
    else {
      PsiManager manager = myManager;
      setOKActionEnabled(manager.getNameHelper().isQualifiedName(name.trim()));
    }
  }

  public MoveDestination getMoveDestination() {
    return myMoveDestination;
  }


  protected void doAction() {
    myMoveDestination = selectDestination();
    if (myMoveDestination == null) return;
    RefactoringSettings.getInstance().MOVE_PREVIEW_USAGES = isPreviewUsages();
    myCallback.run(this);
  }

  public boolean isSearchInNonJavaFiles() {
    return myCbSearchInNonJavaFiles.isSelected();
  }

  private MoveDestination selectDestination() {
    final String packageName = myTextFieldWithBrowseButton.getText();
    PackageWrapper targetPackage = new PackageWrapper(myManager, packageName);
    if (!targetPackage.exists()) {
      final int ret = Messages.showYesNoDialog(myProject, "Package " + packageName + " does not exist.\n" +
                                                "Do you want to create it?", "Move", Messages.getQuestionIcon());
      if (ret != 0) return null;
    }

    if (myCbPreserveSourceFolders.isSelected()) {
      return new MultipleRootsMoveDestination(targetPackage);
    }

    /*
    if (myTargetDirectoryFixed) {
      final VirtualFile sourceRootForFile = ProjectRootManager.getInstance(myProject).getFileIndex().getSourceRootForFile(myInitialTargetDirectory.getVirtualFile());
      if(sourceRootForFile != null) {
        return new AutocreatingSingleSourceRootMoveDestination(targetPackage, sourceRootForFile);
      }
    }
    */

    final VirtualFile[] contentSourceRoots = getSourceRoots();
    if (contentSourceRoots.length == 1) {
      return new AutocreatingSingleSourceRootMoveDestination(targetPackage, contentSourceRoots[0]);
    }
    List<PsiDirectory> targetDirectories = new ArrayList<PsiDirectory>();
    Map<PsiDirectory, String> relativePathsToCreate = new HashMap<PsiDirectory,String>();
    buildDirectoryList(targetPackage, contentSourceRoots, targetDirectories, relativePathsToCreate);
    final DirectoryChooser chooser = new DirectoryChooser(myProject, new DirectoryChooserModuleTreeView(myProject));
    chooser.setTitle("Choose Destination Directory");
    chooser.fillList(
      targetDirectories.toArray(new PsiDirectory[targetDirectories.size()]),
      myInitialTargetDirectory,
      myProject,
      relativePathsToCreate
    );
    chooser.show();
    if (!chooser.isOK()) return null;
    final PsiDirectory selectedDirectory = chooser.getSelectedDirectory();
    final VirtualFile virt = selectedDirectory.getVirtualFile();
    final VirtualFile sourceRootForFile = ProjectRootManager.getInstance(myProject).getFileIndex().getSourceRootForFile(virt);
    LOG.assertTrue(sourceRootForFile != null);
    return new AutocreatingSingleSourceRootMoveDestination(targetPackage, sourceRootForFile);
  }

  private VirtualFile[] getSourceRoots() {
    return ProjectRootManager.getInstance(myProject).getContentSourceRoots();
  }

  private void buildDirectoryList(PackageWrapper aPackage,
                                  VirtualFile[] contentSourceRoots,
                                  List<PsiDirectory> targetDirectories,
                                  Map<PsiDirectory, String> relativePathsToCreate) {

    sourceRoots:
    for (int i = 0; i < contentSourceRoots.length; i++) {
      VirtualFile root = contentSourceRoots[i];

      final PsiDirectory[] directories = aPackage.getDirectories();
      for (int j = 0; j < directories.length; j++) {
        PsiDirectory directory = directories[j];
        if (VfsUtil.isAncestor(root, directory.getVirtualFile(), false)) {
          targetDirectories.add(directory);
          continue sourceRoots;
        }
      }
      String qNameToCreate;
      try {
        qNameToCreate = RefactoringUtil.qNameToCreateInSourceRoot(aPackage, root);
      }
      catch (IncorrectOperationException e) {
        continue sourceRoots;
      }
      PsiDirectory currentDirectory = myManager.findDirectory(root);
      if (currentDirectory == null) continue;
      final String[] shortNames = qNameToCreate.split("\\.");
      for (int j = 0; j < shortNames.length; j++) {
        String shortName = shortNames[j];
        final PsiDirectory subdirectory = currentDirectory.findSubdirectory(shortName);
        if (subdirectory == null) {
          targetDirectories.add(currentDirectory);
          final StringBuffer postfix = new StringBuffer();
          for (int k = j; k < shortNames.length; k++) {
            String name = shortNames[k];
            postfix.append(File.separatorChar);
            postfix.append(name);
          }
          relativePathsToCreate.put(currentDirectory, postfix.toString());
          continue sourceRoots;
        } else {
          currentDirectory = subdirectory;
        }
      }
    }
  }
}
