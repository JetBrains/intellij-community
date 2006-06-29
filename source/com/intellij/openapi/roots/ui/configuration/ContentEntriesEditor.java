package com.intellij.openapi.roots.ui.configuration;

import com.intellij.Patches;
import com.intellij.ide.util.JavaUtil;
import com.intellij.ide.util.projectWizard.ToolbarPanel;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.progress.util.SmoothProgressAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
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
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.concurrency.SwingWorker;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Eugene Zhuravlev
 *         Date: Oct 4, 2003
 *         Time: 6:54:57 PM
 */
public class ContentEntriesEditor extends ModuleElementsEditor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.ui.configuration.ContentEntriesEditor");
  public static final String NAME = ProjectBundle.message("module.paths.title");
  public static final Icon ICON = IconLoader.getIcon("/modules/paths.png");
  private static final Color BACKGROUND_COLOR = UIUtil.getListBackground();
  private static final Icon ADD_CONTENT_ENTRY_ICON = IconLoader.getIcon("/modules/addContentEntry.png");

  private ContentEntryTreeEditor myRootTreeEditor;
  private MyContentEntryEditorListener myContentEntryEditorListener;
  private JPanel myEditorsPanel;
  private final Map<ContentEntry, ContentEntryEditor> myEntryToEditorMap = new HashMap<ContentEntry, ContentEntryEditor>();
  private ContentEntry mySelectedEntry;

  private LanguageLevelConfigurable myLanguageLevelConfigurable;

  private VirtualFile myLastSelectedDir = null;
  private JRadioButton myRbRelativePaths;
  private final String myModuleName;
  private final ModulesProvider myModulesProvider;

  public ContentEntriesEditor(Project project, String moduleName, ModifiableRootModel model, ModulesProvider modulesProvider) {
    super(project, model);
    myModuleName = moduleName;
    myModulesProvider = modulesProvider;
    final VirtualFileManagerAdapter fileManagerListener = new VirtualFileManagerAdapter() {
      public void afterRefreshFinish(boolean asynchonous) {
        for (final ContentEntry contentEntry : myEntryToEditorMap.keySet()) {
          final ContentEntryEditor editor = myEntryToEditorMap.get(contentEntry);
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
    myEntryToEditorMap.clear();
    myLanguageLevelConfigurable.disposeUIResources();
    super.disposeUIResources();
  }

  public boolean isModified() {
    if (super.isModified()) {
      return true;
    }
    if (myLanguageLevelConfigurable != null && myLanguageLevelConfigurable.isModified()) return true;
    final Module selfModule = getModule();
    return selfModule != null && myRbRelativePaths != null && selfModule.isSavePathsRelative() != myRbRelativePaths.isSelected();
  }

  public JPanel createComponentImpl() {
    final Module module = getModule();
    final Project project = module.getProject();

    myContentEntryEditorListener = new MyContentEntryEditorListener();

    final JPanel mainPanel = new JPanel(new BorderLayout());
    mainPanel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

    myLanguageLevelConfigurable = new LanguageLevelConfigurable(getModule());
    mainPanel.add(myLanguageLevelConfigurable.createComponent(), BorderLayout.NORTH);
    myLanguageLevelConfigurable.reset();

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

    final JPanel innerPanel = new JPanel(new GridBagLayout());
    innerPanel.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 6));
    myRbRelativePaths = new JRadioButton(ProjectBundle.message("module.paths.outside.module.dir.relative.radio"));
    final JRadioButton rbAbsolutePaths = new JRadioButton(ProjectBundle.message("module.paths.outside.module.dir.absolute.radio"));
    ButtonGroup buttonGroup = new ButtonGroup();
    buttonGroup.add(myRbRelativePaths);
    buttonGroup.add(rbAbsolutePaths);
    innerPanel.add(new JLabel(ProjectBundle.message("module.paths.outside.module.dir.label")),
                   new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0),
                                          0, 0));
    innerPanel.add(rbAbsolutePaths,
                   new GridBagConstraints(1, 0, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0),
                                          0, 0));
    innerPanel.add(myRbRelativePaths,
                   new GridBagConstraints(2, 0, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0),
                                          0, 0));
    if (module.isSavePathsRelative()) {
      myRbRelativePaths.setSelected(true);
    }
    else {
      rbAbsolutePaths.setSelected(true);
    }

    mainPanel.add(innerPanel, BorderLayout.SOUTH);

    final ContentEntry[] contentEntries = myModel.getContentEntries();
    if (contentEntries.length > 0) {
      for (final ContentEntry contentEntry : contentEntries) {
        addContentEntryPanel(contentEntry);
      }
      selectContentEntry(contentEntries[0]);
    }

    return mainPanel;
  }

  private Module getModule() {
    return myModulesProvider.getModule(myModuleName);
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

  public void moduleStateChanged() {
    if (myRootTreeEditor != null) { //in order to update exclude output root if it is under content root
      myRootTreeEditor.update();
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
    List<ContentEntry> contentEntries = new ArrayList<ContentEntry>();
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
          for (ContentEntry contentEntry : contentEntriesArray) {
            addContentEntryPanel(contentEntry);
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
    final Module module = getModule();
    module.setSavePathsRelative(myRbRelativePaths.isSelected());
    try {
      myLanguageLevelConfigurable.apply();
    }
    catch (ConfigurationException e) {
      //can't be
    }
  }


  private static void addSourceRoots(final Project project, final ContentEntry[] contentEntries, final Runnable finishRunnable) {
    final HashMap<ContentEntry, List<Pair<File, String>>> entryToRootMap = new HashMap<ContentEntry, List<Pair<File, String>>>();
    final Map<File, ContentEntry> fileToEntryMap = new HashMap<File, ContentEntry>();
    for (final ContentEntry contentEntry : contentEntries) {
      entryToRootMap.put(contentEntry, null);
      fileToEntryMap.put(VfsUtil.virtualToIoFile(contentEntry.getFile()), contentEntry);
    }

    final ProgressWindow progressWindow = new ProgressWindow(true, project);
    final ProgressIndicator progressIndicator = Patches.MAC_HIDE_QUIT_HACK
                                                ? progressWindow
                                                : new SmoothProgressAdapter(progressWindow, project);

    final Runnable searchRunnable = new Runnable() {
      public void run() {
        final Runnable process = new Runnable() {
          public void run() {
            for (final File file : fileToEntryMap.keySet()) {
              progressIndicator.setText(ProjectBundle.message("module.paths.searching.source.roots.progress", file.getPath()));
              final List<Pair<File, String>> roots = JavaUtil.suggestRoots(file);
              entryToRootMap.put(fileToEntryMap.get(file), roots);
            }
          }
        };
        progressWindow.setTitle(ProjectBundle.message("module.paths.searching.source.roots.title"));
        ProgressManager.getInstance().runProcess(process, progressIndicator);
      }
    };

    final Runnable addSourcesRunnable = new Runnable() {
      public void run() {
        for (final ContentEntry contentEntry : contentEntries) {
          final List<Pair<File, String>> suggestedRoots = entryToRootMap.get(contentEntry);
          if (suggestedRoots != null) {
            for (final Pair<File, String> suggestedRoot : suggestedRoots) {
              final VirtualFile sourceRoot = LocalFileSystem.getInstance().findFileByIoFile(suggestedRoot.first);
              final VirtualFile fileContent = contentEntry.getFile();
              if (sourceRoot != null && fileContent != null && VfsUtil.isAncestor(fileContent, sourceRoot, false)) {
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
      super(ProjectBundle.message("module.paths.add.content.action"),
            ProjectBundle.message("module.paths.add.content.action.description"), ADD_CONTENT_ENTRY_ICON);
      myDescriptor = new FileChooserDescriptor(false, true, true, false, true, true) {
        public void validateSelectedFiles(VirtualFile[] files) throws Exception {
          validateContentEntriesCandidates(files);
        }
      };
      myDescriptor.setTitle(ProjectBundle.message("module.paths.add.content.title"));
      myDescriptor.setDescription(ProjectBundle.message("module.paths.add.content.prompt"));
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
            throw new Exception(ProjectBundle.message("module.paths.add.content.already.exists.error", file.getPresentableUrl()));
          }
          if (VfsUtil.isAncestor(contentEntryFile, file, true)) {
            // intersection not allowed
            throw new Exception(
              ProjectBundle.message("module.paths.add.content.intersect.error", file.getPresentableUrl(),
                                    contentEntryFile.getPresentableUrl()));
          }
          if (VfsUtil.isAncestor(file, contentEntryFile, true)) {
            // intersection not allowed
            throw new Exception(
              ProjectBundle.message("module.paths.add.content.dominate.error", file.getPresentableUrl(),
                                    contentEntryFile.getPresentableUrl()));
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
                ProjectBundle.message("module.paths.add.content.duplicate.error", file.getPresentableUrl(), module.getName()));
            }
          }
        }
      }
    }

  }

}
