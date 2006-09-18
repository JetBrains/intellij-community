package com.intellij.peer.impl;

import com.intellij.execution.runners.ProcessProxyFactory;
import com.intellij.execution.runners.ProcessProxyFactoryImpl;
import com.intellij.ide.CopyPasteManagerEx;
import com.intellij.ide.DeleteProvider;
import com.intellij.ide.structureView.StructureView;
import com.intellij.ide.structureView.StructureViewFactory;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.newStructureView.StructureViewComponent;
import com.intellij.ide.util.DeleteHandler;
import com.intellij.ide.util.PackageChooserDialog;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.impl.PsiBuilderImpl;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diff.DiffRequestFactory;
import com.intellij.openapi.diff.impl.mergeTool.DiffRequestFactoryImpl;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileChooser.FileSystemTreeFactory;
import com.intellij.openapi.fileChooser.ex.FileSystemTreeFactoryImpl;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapperPeerFactory;
import com.intellij.openapi.ui.PackageChooser;
import com.intellij.openapi.ui.impl.DialogWrapperPeerFactoryImpl;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.FileStatusFactory;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vcs.actions.VcsContextWrapper;
import com.intellij.openapi.vcs.impl.FileStatusFactoryImpl;
import com.intellij.openapi.vcs.ui.impl.CheckinPanelRootNode;
import com.intellij.openapi.vcs.ui.impl.checkinProjectPanel.CheckinPanelTreeTable;
import com.intellij.openapi.vcs.versions.AbstractRevisions;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.peer.PeerFactory;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.SharedImplUtil;
import com.intellij.psi.search.scope.packageSet.PackageSetFactory;
import com.intellij.psi.search.scope.packageSet.PackageSetFactoryImpl;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.ui.*;
import com.intellij.ui.TextComponent;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentFactoryImpl;
import com.intellij.ui.errorView.ErrorViewFactory;
import com.intellij.ui.errorView.impl.ErrorViewFactoryImpl;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.EditSourceOnEnterKeyHandler;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.Table;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.ui.treetable.TreeTable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.TreeCellRenderer;
import java.awt.*;
import java.io.File;

public class PeerFactoryImpl extends PeerFactory implements ApplicationComponent {
  private ProcessProxyFactory myProxyFactory = null;
  private PackageSetFactory myPackageSetFactory;
  private final UIHelper myUIHelper = new MyUIHelper();
  private final ErrorViewFactory myErrorViewFactory = new ErrorViewFactoryImpl();
  private final ContentFactoryImpl myContentFactory = new ContentFactoryImpl();
  private final FileSystemTreeFactoryImpl myFileSystemTreeFactory = new FileSystemTreeFactoryImpl();
  private final DiffRequestFactoryImpl myDiffRequestFactory = new DiffRequestFactoryImpl();
  private final FileStatusFactoryImpl myFileStatusFactory;

  public PeerFactoryImpl() {
    myFileStatusFactory = new FileStatusFactoryImpl();
  }

  @NotNull
  public String getComponentName() {
    return "PeerFactory";
  }

  public void initComponent() {}

  public void disposeComponent() {}

  public FileStatusFactory getFileStatusFactory() {
    return myFileStatusFactory;
  }

  public DialogWrapperPeerFactory getDialogWrapperPeerFactory() {
    return new DialogWrapperPeerFactoryImpl();
  }

  public ProcessProxyFactory getProcessProxyFactory() {
    if (myProxyFactory == null) {
      myProxyFactory = new ProcessProxyFactoryImpl();
    }
    return myProxyFactory;
  }

  public PackageSetFactory getPackageSetFactory() {
    if (myPackageSetFactory == null) {
      myPackageSetFactory = new PackageSetFactoryImpl();
    }
    return myPackageSetFactory;
  }

  public UIHelper getUIHelper() {
    return myUIHelper;
  }

  public ErrorViewFactory getErrorViewFactory() {
    return myErrorViewFactory;
  }

  public ContentFactory getContentFactory() {
    return myContentFactory;
  }

  public FileSystemTreeFactory getFileSystemTreeFactory() {
    return myFileSystemTreeFactory;
  }

  public DiffRequestFactory getDiffRequestFactory() {
    return myDiffRequestFactory;
  }

  private static class MyUIHelper implements UIHelper {
    public void installToolTipHandler(JTree tree) {
      TreeToolTipHandler.install(tree);
    }

    public void installToolTipHandler(JTable table) {
      TableToolTipHandler.install(table);
    }

    public void installEditSourceOnDoubleClick(JTree tree) {
      EditSourceOnDoubleClickHandler.install(tree);
    }

    public void installEditSourceOnDoubleClick(TreeTable tree) {
      EditSourceOnDoubleClickHandler.install(tree);
    }

    public void installEditSourceOnDoubleClick(Table table) {
      EditSourceOnDoubleClickHandler.install(table);
    }

    public void installTreeSpeedSearch(JTree tree) {
      new TreeSpeedSearch(tree);
    }

    public void installListSpeedSearch(JList list) {
      new ListSpeedSearch(list);
    }

    public void installEditSourceOnEnterKeyHandler(JTree tree) {
      EditSourceOnEnterKeyHandler.install(tree);
    }

    public TableCellRenderer createPsiElementRenderer(final PsiElement psiElement, final Project project) {
      return new ColoredTableCellRenderer() {
        protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
          append(getPsiElementText(psiElement), SimpleTextAttributes.REGULAR_ATTRIBUTES);
          setIcon(psiElement.getIcon(0));
        }
      };

    }

    public TreeCellRenderer createHighlightableTreeCellRenderer() {
      return new HighlightableCellRenderer();
    }

    public void drawDottedRectangle(Graphics g, int x, int y, int i, int i1) {
      UIUtil.drawDottedRectangle(g,x,y,i,i1);
    }

    public void installSmartExpander(JTree tree) {
      SmartExpander.installOn(tree);
    }

    public void installSelectionSaver(JTree tree) {
      SelectionSaver.installOn(tree);
    }

    public TreeTable createDirectoryDiffTree(Project project, AbstractRevisions[] roots) {
      final CheckinPanelTreeTable result = CheckinPanelTreeTable.createOn(project, CheckinPanelRootNode.create(project, roots),
                                                                          new ColumnInfo[0], new ColumnInfo[0],
                                                                          new AnAction[0], new AnAction[0]);
      TreeUtil.expandAll(result.getTree());
      return result;
    }

    public TextComponent createTypedTextField(final String text, PsiType type, PsiElement context, final Project project) {
      final PsiExpressionCodeFragment fragment = PsiManager.getInstance(project).getElementFactory().createExpressionCodeFragment(text, context, type, true);
      final Document document = PsiDocumentManager.getInstance(project).getDocument(fragment);
      return new EditorTextField(document, project, StdFileTypes.JAVA);
    }

    public CopyPasteSupport createPsiBasedCopyPasteSupport(Project project, JComponent keyReceiver, final PsiElementSelector dataSelector) {
      return new CopyPasteManagerEx.CopyPasteDelegator(project, keyReceiver) {
        protected PsiElement[] getSelectedElements() {
          return dataSelector.getSelectedElements();
        }
      };
    }

    public DeleteProvider createPsiBasedDeleteProvider() {
      return new DeleteHandler.DefaultDeleteProvider();
    }

    public PackageChooser createPackageChooser(String title, Project project) {
      return new PackageChooserDialog(title, project);
    }

    private static String getPsiElementText(PsiElement psiElement) {
      if (psiElement instanceof PsiClass) {
        return PsiFormatUtil.formatClass((PsiClass)psiElement, PsiFormatUtil.SHOW_NAME |
                                                               PsiFormatUtil.SHOW_FQ_NAME);
      }
      else if (psiElement instanceof PsiMethod) {
        return PsiFormatUtil.formatMethod((PsiMethod)psiElement,
                                          PsiSubstitutor.EMPTY,
                                          PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS | PsiFormatUtil.SHOW_CONTAINING_CLASS,
                                          0);
      }
      else if (psiElement instanceof PsiField) {
        return PsiFormatUtil.formatVariable((PsiField)psiElement,
                                            PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE | PsiFormatUtil.SHOW_CONTAINING_CLASS,
                                            PsiSubstitutor.EMPTY);
      }
      else {
        return psiElement.toString();
      }

    }

  }

  public VcsContextFactory getVcsContextFactory() {
    return new VcsContextFactory() {
      public VcsContext createCachedContextOn(AnActionEvent event) {
        return VcsContextWrapper.createCachedInstanceOn(event);
      }

      public VcsContext createContextOn(final AnActionEvent event) {
        return new VcsContextWrapper(event.getDataContext(), event.getModifiers(), event.getPlace());
      }

      public FilePath createFilePathOn(@NotNull final VirtualFile virtualFile) {
        return ApplicationManager.getApplication().runReadAction(new Computable<FilePath>() {
          public FilePath compute() {
            return new FilePathImpl(virtualFile);
          }
        });
      }

      public FilePath createFilePathOn(final File file) {
        return ApplicationManager.getApplication().runReadAction(new Computable<FilePath>() {
          public FilePath compute() {
            return FilePathImpl.create(file);
          }
        });
      }

      public FilePath createFilePathOn(final File file, final boolean isDirectory) {
        return ApplicationManager.getApplication().runReadAction(new Computable<FilePath>() {
          public FilePath compute() {
            return FilePathImpl.create(file, isDirectory);
          }
        });
      }

      public FilePath createFilePathOnDeleted(final File file, final boolean isDirectory) {
        return ApplicationManager.getApplication().runReadAction(new Computable<FilePath>() {
          public FilePath compute() {
            return FilePathImpl.createForDeletedFile(file, isDirectory);
          }
        });
      }

      public FilePath createFilePathOn(final VirtualFile parent, final String name) {
        return ApplicationManager.getApplication().runReadAction(new Computable<FilePath>() {
          public FilePath compute() {
            return new FilePathImpl(parent, name, false);
          }
        });
      }
    };
  }

  public StructureViewFactory getStructureViewFactory() {
    return new StructureViewFactory() {

      public StructureView createStructureView(final FileEditor editor, StructureViewModel treeModel, Project project) {
        return new StructureViewComponent(editor, treeModel, project);
      }

      public StructureView createStructureView(FileEditor editor, StructureViewModel treeModel, Project project, boolean showRootNode) {
        return new StructureViewComponent(editor, treeModel, project, showRootNode);
      }
    };
  }

  public PsiBuilder createBuilder(ASTNode tree, Language lang, CharSequence seq, final Project project) {
    return new PsiBuilderImpl(lang, project, SharedImplUtil.findCharTableByTree(tree), seq);
  }
}