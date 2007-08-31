package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.CommonBundle;
import com.intellij.ide.util.BrowseFilesListener;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.libraries.LibraryImpl;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.ui.configuration.LibraryTableModifiableModelProvider;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryTableEditor;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;

public abstract class BaseLibrariesConfigurable extends BaseStructureConfigurable  {
  protected String myLevel;

  protected BaseLibrariesConfigurable(final Project project) {
    super(project);
  }


  protected void processRemovedItems() {
  }

  protected boolean wasObjectStored(final Object editableObject) {
    return false;
  }

  public boolean clearSearch() {
    return false;
  }

  @Nullable
  public Runnable enableSearch(final String option) {
    return null;
  }

  @Nullable
  @NonNls
  public String getHelpTopic() {
    return "reference.settingsdialog.project.structure.library";
  }

  public boolean isModified() {
    boolean isModified = false;
    for (final LibrariesModifiableModel provider : myContext.myLevel2Providers.values()) {
      isModified |= provider.isChanged();
    }

    return isModified;
  }

  public void reset() {
    super.reset();
    myTree.setRootVisible(false);
  }

  protected void loadTree() {
    final LibraryTablesRegistrar registrar = LibraryTablesRegistrar.getInstance();

    final LibrariesModifiableModel provider = myContext.myLevel2Providers.get(myLevel);
    createLibrariesNode(registrar.getLibraryTableByLevel(myLevel, myProject), provider,
                                     myContext.createModifiableModelProvider(myLevel, false));
  }


  private void createLibrariesNode(final LibraryTable table,
                                     LibrariesModifiableModel provider,
                                     final LibraryTableModifiableModelProvider modelProvider) {
    provider = new LibrariesModifiableModel(table);
    final Library[] libraries = provider.getLibraries();
    for (Library library : libraries) {
      addNode(new MyNode(new LibraryConfigurable(modelProvider, library, myProject, TREE_UPDATER)), myRoot);
    }
  }

  public void apply() throws ConfigurationException {
    super.apply();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        for (final LibrariesModifiableModel provider : myContext.myLevel2Providers.values()) {
          provider.deferredCommit();
        }
      }
    });
  }

  public MyNode createLibraryNode(Library library) {
    final LibraryTable table = library.getTable();
    if (table != null) {
      final String level = table.getTableLevel();
      final LibraryConfigurable configurable =
        new LibraryConfigurable(myContext.createModifiableModelProvider(level, false), library, myProject, TREE_UPDATER);
      final MyNode node = new MyNode(configurable);
      addNode(node, myRoot);
      return node;
    }

    return null;
  }

  public void dispose() {
    myContext.myLevel2Providers.clear();
  }

  protected AnAction createCopyAction() {
    return new MyCopyAction();
  }

  protected AbstractAddGroup createAddAction() {
    return new AbstractAddGroup(getAddText()) {
      {
        setPopup(false);
      }

      public AnAction[] getChildren(@Nullable final AnActionEvent e) {
        return new AnAction[]{new AnAction(getAddText()) {
          public void actionPerformed(AnActionEvent e) {
            final LibraryTableEditor editor = LibraryTableEditor.editLibraryTable(getModelProvider(false), myProject);
            editor.createAddLibraryAction(true).actionPerformed(null);
            Disposer.dispose(editor);
          }
        }};
      }
    };
  }

  protected abstract String getAddText();

  public abstract LibraryTableModifiableModelProvider getModelProvider(final boolean editable);

  public abstract BaseLibrariesConfigurable getOppositeGroup();

  protected void removeLibrary(final Library library) {
    final LibraryTable table = library.getTable();
    if (table != null) {
      getModelProvider(true).getModifiableModel().removeLibrary(library);
      myContext.invalidateModules(myContext.myLibraryDependencyCache.get(library));
      myContext.myLibraryDependencyCache.remove(library);
    }
  }

  protected
  @Nullable
  String getEmptySelectionString() {
    return "Select a library to view or edit its details here";
  }

  private class MyCopyAction extends AnAction {
    private JCheckBox mySaveAsCb;
    private JTextField myNameTf;
    private TextFieldWithBrowseButton myPathTf;
    private JPanel myWholePanel;

    private MyCopyAction() {
      super(CommonBundle.message("button.copy"), CommonBundle.message("button.copy"), COPY_ICON);
    }

    public void actionPerformed(final AnActionEvent e) {
      final Object o = getSelectedObject();
      if (o instanceof LibraryImpl) {
        myPathTf.addBrowseFolderListener("Choose directory",
                                         ProjectBundle.message("directory.roots.copy.label"),
                                         myProject, BrowseFilesListener.SINGLE_DIRECTORY_DESCRIPTOR);
        mySaveAsCb.addActionListener(new ActionListener() {
          public void actionPerformed(final ActionEvent e) {
            myPathTf.setEnabled(mySaveAsCb.isSelected());
          }
        });
        mySaveAsCb.setText(ProjectBundle.message("save.as.library.checkbox", getOppositeGroup().myLevel));
        mySaveAsCb.setSelected(false);
        myPathTf.setEnabled(false);

        final DialogWrapper dlg = new DialogWrapper(myTree, false) {
          {
            setTitle("Copy");
            init();
          }

          @Nullable
          protected JComponent createCenterPanel() {
            return myWholePanel;
          }

          public JComponent getPreferredFocusedComponent() {
            return myNameTf;
          }

          protected void doOKAction() {
            if (myNameTf.getText().length() == 0) {
              Messages.showErrorDialog("Enter library copy name", CommonBundle.message("title.error"));
              return;
            }
            super.doOKAction();
          }
        };
        dlg.show();
        if (!dlg.isOK()) return;

        BaseLibrariesConfigurable configurable = mySaveAsCb.isSelected() ? getOppositeGroup() : BaseLibrariesConfigurable.this;

        final LibraryImpl library = (LibraryImpl)myContext.getLibrary(((LibraryImpl)o).getName(), myLevel);

        LOG.assertTrue(library != null);

        final Library lib = configurable.getModelProvider(true).getModifiableModel().createLibrary(myNameTf.getText());

        final LibraryImpl model = (LibraryImpl)lib.getModifiableModel();

        for (OrderRootType type : OrderRootType.ALL_TYPES) {
          final VirtualFile[] files = library.getFiles(type);
          for (VirtualFile file : files) {
            if (mySaveAsCb.isSelected()) {
              final File copy = new File(new File(myPathTf.getText()), file.getName());
              if (copy.mkdirs()) {
                try {
                  final File fromFile = VfsUtil.virtualToIoFile(file);
                  if (fromFile.isFile()) {
                    FileUtil.copy(fromFile, copy);
                  } else {
                    FileUtil.copyDir(fromFile, copy);
                  }
                  model.addRoot(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(copy), type);
                }
                catch (IOException e1) {
                  //skip
                }
                continue;
              }
            }

            model.addRoot(file, type);
          }
        }
        configurable.createLibraryNode(model);
      }
    }

    public void update(final AnActionEvent e) {
      if (myTree.getSelectionPaths() == null || myTree.getSelectionPaths().length != 1) {
        e.getPresentation().setEnabled(false);
      } else {
        e.getPresentation().setEnabled(getSelectedObject() instanceof LibraryImpl);
      }
    }
  }
}
