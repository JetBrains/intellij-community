package com.intellij.openapi.roots.ui.configuration;

import com.intellij.Patches;
import com.intellij.ide.util.BrowseFilesListener;
import com.intellij.ide.util.JavaUtil;
import com.intellij.ide.util.projectWizard.ToolbarPanel;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.progress.util.SmoothProgressAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.ui.componentsList.components.ScrollablePanel;
import com.intellij.openapi.roots.ui.componentsList.layout.VerticalStackLayout;
import com.intellij.openapi.roots.ui.configuration.actions.IconWithTextAction;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.ex.VirtualFileManagerAdapter;
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx;
import com.intellij.openapi.Disposable;
import com.intellij.ui.FieldPanel;
import com.intellij.ui.InsertPathAction;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.concurrency.SwingWorker;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * @author Eugene Zhuravlev
 *         Date: Oct 4, 2003
 *         Time: 6:54:57 PM
 */
public class ContentEntriesEditor extends ModuleElementsEditor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.ui.configuration.ContentEntriesEditor");
  public static final String NAME = "Paths";
  public static final Icon ICON = IconLoader.getIcon("/modules/paths.png");
  private static final Color BACKGROUND_COLOR = UIManager.getColor("List.background");
  private static final Icon ADD_CONTENT_ENTRY_ICON = IconLoader.getIcon("/modules/addContentEntry.png");

  private ContentEntryTreeEditor myRootTreeEditor;
  private MyContentEntryEditorListener myContentEntryEditorListener;
  private JPanel myEditorsPanel;
  private final Map<ContentEntry, ContentEntryEditor> myEntryToEditorMap = new HashMap<ContentEntry, ContentEntryEditor>();
  private ContentEntry mySelectedEntry;
  private FieldPanel myOutputPathPanel;
  private FieldPanel myTestsOutputPathPanel;
  private VirtualFile myLastSelectedDir = null;
  private JRadioButton myRbRelativePaths;
  private JCheckBox myCbExcludeOutput;
  private final String myModuleName;
  private final ModulesProvider myModulesProvider;

  public ContentEntriesEditor(Project project, String moduleName, ModifiableRootModel model, ModulesProvider modulesProvider) {
    super(project, model);
    myModuleName = moduleName;
    myModulesProvider = modulesProvider;
    final VirtualFileManagerAdapter fileManagerListener = new VirtualFileManagerAdapter() {
      public void afterRefreshFinish(boolean asynchonous) {
        for (Iterator<ContentEntry> it = myEntryToEditorMap.keySet().iterator(); it.hasNext();) {
          final ContentEntryEditor editor = myEntryToEditorMap.get(it.next());
          if (editor != null) {
            editor.update();
          }
        }
      }
    };
    final VirtualFileManagerEx fileManager = ((VirtualFileManagerEx)VirtualFileManager.getInstance());
    fileManager.addVirtualFileManagerListener(fileManagerListener);
    registerDisposable(new Disposable() {
      public void dispose() {
        fileManager.removeVirtualFileManagerListener(fileManagerListener);
      }
    });
  }

  public String getHelpTopic() {
    return "project.paths.paths";
  }

  public String getDisplayName() {
    return NAME;
  }

  public Icon getIcon() {
    return ICON;
  }

  public void disposeUIResources() {
    if (myRootTreeEditor != null) {
      myRootTreeEditor.setContentEntryEditor(null);
    }
    super.disposeUIResources();
  }

  public boolean isModified() {
    if (super.isModified()) {
      return true;
    }
    final Module selfModule = getMyModule();
    return selfModule == null || myRbRelativePaths == null ? false : selfModule.isSavePathsRelative() != myRbRelativePaths.isSelected();
  }

  public JPanel createComponentImpl() {
    final Project project = getMyModule().getProject();

    myContentEntryEditorListener = new MyContentEntryEditorListener();

    final JPanel mainPanel = new JPanel(new BorderLayout());
    mainPanel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

    JComponent outputPathsBlock = createOutputPathsBlock();
    outputPathsBlock.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));
    mainPanel.add(outputPathsBlock, BorderLayout.NORTH);

    final JPanel entriesPanel = new JPanel(new BorderLayout());

    final DefaultActionGroup group = new DefaultActionGroup();
    group.add(new AddContentEntryAction());

    myEditorsPanel = new ScrollablePanel(new VerticalStackLayout());
    myEditorsPanel.setBackground(BACKGROUND_COLOR);
    JScrollPane myScrollPane = ScrollPaneFactory.createScrollPane(myEditorsPanel);
    entriesPanel.add(new ToolbarPanel(myScrollPane, group), BorderLayout.CENTER);

    final Splitter splitter = new Splitter(false);
    splitter.setHonorComponentsMinimumSize(true);
    mainPanel.add(splitter, BorderLayout.CENTER);

    final JPanel editorsPanel = new JPanel(new GridBagLayout());
    splitter.setFirstComponent(editorsPanel);
    editorsPanel.add(entriesPanel,
                     new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));

    myRootTreeEditor = new ContentEntryTreeEditor(project);
    final JComponent treeEditorComponent = myRootTreeEditor.createComponent();
    splitter.setSecondComponent(treeEditorComponent);

    final JPanel rbPanel = new JPanel(new GridBagLayout());
    rbPanel.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 6));
    myRbRelativePaths = new JRadioButton("Use relative path");
    final JRadioButton rbAbsolutePaths = new JRadioButton("Use absolute path");
    ButtonGroup buttonGroup = new ButtonGroup();
    buttonGroup.add(myRbRelativePaths);
    buttonGroup.add(rbAbsolutePaths);
    rbPanel.add(new JLabel("For files outside module file directory:"),
                new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.EAST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0),
                                       0, 0));
    rbPanel.add(rbAbsolutePaths,
                new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0),
                                       0, 0));
    rbPanel.add(myRbRelativePaths,
                new GridBagConstraints(2, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0),
                                       0, 0));
    if (getMyModule().isSavePathsRelative()) {
      myRbRelativePaths.setSelected(true);
    }
    else {
      rbAbsolutePaths.setSelected(true);
    }
    mainPanel.add(rbPanel, BorderLayout.SOUTH);

    final ContentEntry[] contentEntries = myModel.getContentEntries();
    if (contentEntries.length > 0) {
      for (final ContentEntry contentEntry : contentEntries) {
        addContentEntryPanel(contentEntry);
      }
      selectContentEntry(contentEntries[0]);
    }

    return mainPanel;
  }

  private Module getMyModule() {
    return myModulesProvider.getModule(myModuleName);
  }

  private JComponent createOutputPathsBlock() {
    myOutputPathPanel = createOutputPathPanel("Select Output Path", new CommitPathRunnable() {
      public void saveUrl(String url) {
        myModel.setCompilerOutputPath(url);
      }
    });
    myTestsOutputPathPanel = createOutputPathPanel("Select Test Output Path", new CommitPathRunnable() {
      public void saveUrl(String url) {
        myModel.setCompilerOutputPathForTests(url);
      }
    });

    myCbExcludeOutput = new JCheckBox("Exclude output paths", myModel.isExcludeOutput());
    myCbExcludeOutput.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        myModel.setExcludeOutput(e.getStateChange() == ItemEvent.SELECTED);
        if (myRootTreeEditor != null) {
          myRootTreeEditor.update();
        }
      }
    });

    final JPanel outputPathsPanel = new JPanel(new GridBagLayout());

    outputPathsPanel.add(new JLabel("Output path:"),
                         new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.EAST, GridBagConstraints.NONE,
                                                new Insets(6, 0, 0, 4), 0, 0));
    outputPathsPanel.add(myOutputPathPanel,
                         new GridBagConstraints(1, GridBagConstraints.RELATIVE, 2, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL,
                                                new Insets(6, 4, 0, 0), 0, 0));

    outputPathsPanel.add(new JLabel("Test output path:"),
                         new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.EAST, GridBagConstraints.NONE,
                                                new Insets(6, 0, 0, 4), 0, 0));
    outputPathsPanel.add(myTestsOutputPathPanel,
                         new GridBagConstraints(1, GridBagConstraints.RELATIVE, 2, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL,
                                                new Insets(6, 4, 0, 0), 0, 0));

    outputPathsPanel.add(myCbExcludeOutput,
                         new GridBagConstraints(1, GridBagConstraints.RELATIVE, 2, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE,
                                                new Insets(6, 0, 0, 0), 0, 0));

    // fill with data
    final VirtualFile compilerOutputPath = myModel.getCompilerOutputPath();
    if (compilerOutputPath != null) {
      myOutputPathPanel.setText(compilerOutputPath.getPath().replace('/', File.separatorChar));
    }
    else {
      final String compilerOutputUrl = myModel.getCompilerOutputPathUrl();
      if (compilerOutputUrl != null) {
        myOutputPathPanel.setText(VirtualFileManager.extractPath(compilerOutputUrl).replace('/', File.separatorChar));
      }
    }

    final VirtualFile testsOutputPath = myModel.getCompilerOutputPathForTests();
    if (testsOutputPath != null) {
      myTestsOutputPathPanel.setText(testsOutputPath.getPath().replace('/', File.separatorChar));
    }
    else {
      final String testsOutputUrl = myModel.getCompilerOutputPathForTestsUrl();
      if (testsOutputUrl != null) {
        myTestsOutputPathPanel.setText(VirtualFileManager.extractPath(testsOutputUrl).replace('/', File.separatorChar));
      }
    }

    return outputPathsPanel;
  }

  private static interface CommitPathRunnable {
    void saveUrl(String url);
  }

  private FieldPanel createOutputPathPanel(final String title, final CommitPathRunnable commitPathRunnable) {
    final JTextField textField = new JTextField();
    final FileChooserDescriptor outputPathsChooserDescriptor = new FileChooserDescriptor(false, true, false, false, false, false);
    outputPathsChooserDescriptor.setHideIgnored(false);
    InsertPathAction.addTo(textField, outputPathsChooserDescriptor);

    final Runnable commitRunnable = new Runnable() {
      public void run() {
        if (!myModel.isWritable()) {
          return;
        }
        final String path = textField.getText().trim();
        if (path.length() == 0) {
          commitPathRunnable.saveUrl(null);
        }
        else {
          // should set only absolute paths
          String canonicalPath;
          try {
            canonicalPath = new File(path).getCanonicalPath();
          }
          catch (IOException e) {
            canonicalPath = path;
          }
          commitPathRunnable.saveUrl(VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, canonicalPath.replace(File.separatorChar, '/')));
        }
        if (myRootTreeEditor != null) {
          myRootTreeEditor.update(); // need this in order to update appearance of excluded output paths if they are under content root
        }
      }
    };

    textField.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        commitRunnable.run();
      }
    });

    final FieldPanel fieldPanel = new FieldPanel(textField, null, null, new BrowseFilesListener(textField, title, "", outputPathsChooserDescriptor) {
      public void actionPerformed(ActionEvent e) {
        super.actionPerformed(e);
        commitRunnable.run();
      }
    }, null);

    return fieldPanel;
  }

  private void addContentEntryPanel(final ContentEntry contentEntry) {
    final ContentEntryEditor contentEntryEditor = new ContentEntryEditor(contentEntry, myModel);
    contentEntryEditor.addContentEntryEditorListener(myContentEntryEditorListener);
    myEntryToEditorMap.put(contentEntry, contentEntryEditor);
    Border border = BorderFactory.createEmptyBorder(2, 2, 0, 2);
    final JComponent component = contentEntryEditor.getComponent();
    final Border componentBorder = component.getBorder();
    if (componentBorder != null) {
      border = BorderFactory.createCompoundBorder(border, componentBorder);
    }
    component.setBorder(border);
    myEditorsPanel.add(component);
  }

  private void selectContentEntry(ContentEntry contentEntry) {
    if (mySelectedEntry != null && mySelectedEntry.equals(contentEntry)) {
      return;
    }
    try {
      if (mySelectedEntry != null) {
        ContentEntryEditor editor = myEntryToEditorMap.get(mySelectedEntry);
        if (editor != null) {
          editor.setSelected(false);
        }
      }

      if (contentEntry != null) {
        ContentEntryEditor editor = myEntryToEditorMap.get(contentEntry);
        if (editor != null) {
          editor.setSelected(true);
          final JComponent component = editor.getComponent();
          final JComponent scroller = (JComponent)component.getParent();
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              scroller.scrollRectToVisible(component.getBounds());
            }
          });
          myRootTreeEditor.setContentEntryEditor(editor);
          myRootTreeEditor.requestFocus();
        }
      }
    }
    finally {
      mySelectedEntry = contentEntry;
    }
  }

  private ContentEntry getNextContentEntry(ContentEntry contentEntry) {
    return getAdjacentContentEntry(contentEntry, 1);
  }

  /*
  private ContentEntry getPreviousContentEntry(ContentEntry contentEntry) {
    return getAdjacentContentEntry(contentEntry, -1);
  }
  */

  private ContentEntry getAdjacentContentEntry(ContentEntry contentEntry, int delta) {
    final ContentEntry[] contentEntries = myModel.getContentEntries();
    for (int idx = 0; idx < contentEntries.length; idx++) {
      ContentEntry entry = contentEntries[idx];
      if (contentEntry.equals(entry)) {
        int nextEntryIndex = (idx + delta) % contentEntries.length;
        if (nextEntryIndex < 0) {
          nextEntryIndex += contentEntries.length;
        }
        return nextEntryIndex == idx ? null : contentEntries[nextEntryIndex];
      }
    }
    return null;
  }

  private void addContentEntries(final VirtualFile[] files) {
    java.util.List<ContentEntry> contentEntries = new ArrayList<ContentEntry>();
    for (final VirtualFile file : files) {
      if (isAlreadyAdded(file)) {
        continue;
      }
      final ContentEntry contentEntry = myModel.addContentEntry(file);
      contentEntries.add(contentEntry);
    }

    if (contentEntries.size() > 0) {
      final ContentEntry[] contentEntriesArray = contentEntries.toArray(new ContentEntry[contentEntries.size()]);
      addSourceRoots(myProject, contentEntriesArray, new Runnable() {
        public void run() {
          for (int idx = 0; idx < contentEntriesArray.length; idx++) {
            addContentEntryPanel(contentEntriesArray[idx]);
          }
          myEditorsPanel.revalidate();
          myEditorsPanel.repaint();
          selectContentEntry(contentEntriesArray[contentEntriesArray.length - 1]);
        }
      });
    }
  }

  private boolean isAlreadyAdded(VirtualFile file) {
    final VirtualFile[] contentRoots = myModel.getContentRoots();
    for (VirtualFile contentRoot : contentRoots) {
      if (contentRoot.equals(file)) {
        return true;
      }
    }
    return false;
  }

  public void saveData() {
    getMyModule().setSavePathsRelative(myRbRelativePaths.isSelected());
  }

  private static void addSourceRoots(final Project project, final ContentEntry[] contentEntries, final Runnable finishRunnable) {
    final HashMap<ContentEntry, java.util.List<Pair<File, String>>> entryToRootMap = new HashMap<ContentEntry, java.util.List<Pair<File, String>>>();
    final Map<File, ContentEntry> fileToEntryMap = new HashMap<File, ContentEntry>();
    for (final ContentEntry contentEntry : contentEntries) {
      entryToRootMap.put(contentEntry, null);
      fileToEntryMap.put(VfsUtil.virtualToIoFile(contentEntry.getFile()), contentEntry);
    }

    final ProgressWindow progressWindow = new ProgressWindow(true, project);
    final ProgressIndicator progressIndicator = Patches.MAC_HIDE_QUIT_HACK
                                                ? progressWindow
                                                : (ProgressIndicator)new SmoothProgressAdapter(progressWindow, project);

    final Runnable searchRunnable = new Runnable() {
      public void run() {
        final Runnable process = new Runnable() {
          public void run() {
            for (Iterator it = fileToEntryMap.keySet().iterator(); it.hasNext();) {
              final File entryFile = (File)it.next();
              progressIndicator.setText("Searching for source roots in " + entryFile.getPath());
              final java.util.List<Pair<File, String>> roots = JavaUtil.suggestRoots(entryFile);
              entryToRootMap.put(fileToEntryMap.get(entryFile), roots);
            }
          }
        };
        progressWindow.setTitle("Adding Source Roots");
        ProgressManager.getInstance().runProcess(process, progressIndicator);
      }
    };

    final Runnable addSourcesRunnable = new Runnable() {
      public void run() {
        for (int idx = 0; idx < contentEntries.length; idx++) {
          final ContentEntry contentEntry = contentEntries[idx];
          final java.util.List<Pair<File, String>> suggestedRoots = entryToRootMap.get(contentEntry);
          if (suggestedRoots != null) {
            for (int j = 0; j < suggestedRoots.size(); j++) {
              final Pair<File, String> suggestedRoot = suggestedRoots.get(j);
              final VirtualFile sourceRoot = LocalFileSystem.getInstance().findFileByIoFile(suggestedRoot.first);
              if (sourceRoot != null && VfsUtil.isAncestor(contentEntry.getFile(), sourceRoot, false)) {
                contentEntry.addSourceFolder(sourceRoot, false, suggestedRoot.getSecond());
              }
            }
          }
        }
        if (finishRunnable != null) {
          finishRunnable.run();
        }
      }
    };

    new SwingWorker() {
      public Object construct() {
        searchRunnable.run();
        return null;
      }

      public void finished() {
        addSourcesRunnable.run();
      }
    }.start();
  }

  private final class MyContentEntryEditorListener extends ContentEntryEditorListenerAdapter {
    public void editingStarted(ContentEntryEditor editor) {
      selectContentEntry(editor.getContentEntry());
    }

    public void beforeEntryDeleted(ContentEntryEditor editor) {
      final ContentEntry entry = editor.getContentEntry();
      if (mySelectedEntry != null && mySelectedEntry.equals(entry)) {
        myRootTreeEditor.setContentEntryEditor(null);
      }
      final ContentEntry nextContentEntry = getNextContentEntry(entry);
      removeContentEntryPanel(entry);
      selectContentEntry(nextContentEntry);
      editor.removeContentEntryEditorListener(this);
    }

    public void folderIncluded(ContentEntryEditor editor, VirtualFile file) {
      if (editor.isCompilerOutput(file)) {
        myCbExcludeOutput.setSelected(false);
      }
    }

    public void folderExcluded(ContentEntryEditor editor, VirtualFile file) {
      if (editor.isCompilerOutput(file)) {
        myCbExcludeOutput.setSelected(true);
      }
    }

    public void navigationRequested(ContentEntryEditor editor, VirtualFile file) {
      if (mySelectedEntry != null && mySelectedEntry.equals(editor.getContentEntry())) {
        myRootTreeEditor.requestFocus();
        myRootTreeEditor.select(file);
      }
      else {
        selectContentEntry(editor.getContentEntry());
        myRootTreeEditor.requestFocus();
        myRootTreeEditor.select(file);
      }
    }

    private void removeContentEntryPanel(final ContentEntry contentEntry) {
      ContentEntryEditor editor = myEntryToEditorMap.get(contentEntry);
      if (editor != null) {
        myEditorsPanel.remove(editor.getComponent());
        myEntryToEditorMap.remove(contentEntry);
        myEditorsPanel.revalidate();
        myEditorsPanel.repaint();
      }
    }
  }

  private class AddContentEntryAction extends IconWithTextAction {
    private final FileChooserDescriptor myDescriptor;

    public AddContentEntryAction() {
      super("Add Content Root", "Add content root to the module", ADD_CONTENT_ENTRY_ICON);
      myDescriptor = new FileChooserDescriptor(false, true, true, false, true, true) {
        public void validateSelectedFiles(VirtualFile[] files) throws Exception {
          validateContentEntriesCandidates(files);
        }
      };
      myDescriptor.setTitle("Select content root directory");
      myDescriptor.setDescription("Content root is a directory containing all files related to this module");
    }

    public void actionPerformed(AnActionEvent e) {
      VirtualFile[] files = FileChooser.chooseFiles(myProject, myDescriptor, myLastSelectedDir);
      if (files.length > 0) {
        myLastSelectedDir = files[0];
        addContentEntries(files);
      }
    }

    private void validateContentEntriesCandidates(VirtualFile[] files) throws Exception {
      for (final VirtualFile file : files) {
        // check for collisions with already existing entries
        for (final ContentEntry contentEntry : myEntryToEditorMap.keySet()) {
          final VirtualFile contentEntryFile = contentEntry.getFile();
          if (contentEntryFile == null) {
            continue;  // skip invalid entry
          }
          if (contentEntryFile.equals(file)) {
            throw new Exception("Content root \"" + file.getPresentableUrl() + "\" already exists");
          }
          if (VfsUtil.isAncestor(contentEntryFile, file, true)) {
            // intersection not allowed
            throw new Exception(
              "Content root being added \"" + file.getPresentableUrl() + "\"\nis located below existing content root \"" +
              contentEntryFile.getPresentableUrl() +
              "\".\nContent entries should not intersect.");
          }
          if (VfsUtil.isAncestor(file, contentEntryFile, true)) {
            // intersection not allowed
            throw new Exception(
              "Content root being added \"" + file.getPresentableUrl() + "\"\ndominates existing content root \"" +
              contentEntryFile.getPresentableUrl() +
              "\".\nContent entries should not intersect.");
          }
        }
        // check if the same root is configured for another module
        final Module[] modules = myModulesProvider.getModules();
        for (final Module module : modules) {
          if (myModuleName.equals(module.getName())) {
            continue;
          }
          ModuleRootModel rootModel = myModulesProvider.getRootModel(module);
          LOG.assertTrue(rootModel != null);
          final VirtualFile[] moduleContentRoots = rootModel.getContentRoots();
          for (VirtualFile moduleContentRoot : moduleContentRoots) {
            if (file.equals(moduleContentRoot)) {
              throw new Exception(
                "Content root \"" + file.getPresentableUrl() + "\" already defined for module \"" + module.getName() +
                "\".\nTwo modules in a project cannot share the same content root.");
            }
          }
        }
      }
    }

  }

}
