package com.intellij.peer.impl;

import com.intellij.execution.runners.ProcessProxyFactory;
import com.intellij.execution.runners.ProcessProxyFactoryImpl;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diff.DiffRequestFactory;
import com.intellij.openapi.diff.impl.mergeTool.DiffRequestFactoryImpl;
import com.intellij.openapi.fileChooser.FileSystemTreeFactory;
import com.intellij.openapi.fileChooser.ex.FileSystemTreeFactoryImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapperPeerFactory;
import com.intellij.openapi.ui.impl.DialogWrapperPeerFactoryImpl;
import com.intellij.openapi.vcs.FileStatusFactory;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vcs.actions.VcsContextWrapper;
import com.intellij.openapi.vcs.impl.FileStatusFactoryImpl;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.packageDependencies.packageSet.PackageSetFactoryImpl;
import com.intellij.peer.PeerFactory;
import com.intellij.psi.*;
import com.intellij.psi.search.scope.packageSet.PackageSetFactory;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.ui.*;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentFactoryImpl;
import com.intellij.ui.errorView.ErrorViewFactory;
import com.intellij.ui.errorView.impl.ErrorViewFactoryImpl;
import com.intellij.ui.plaf.beg.BegTreeHandleUtil;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.EditSourceOnEnterKeyHandler;
import com.intellij.util.ui.Table;
import com.intellij.util.ui.Tree;
import com.intellij.util.ui.treetable.TreeTable;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.TreeCellRenderer;
import java.awt.*;

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

    public void installTreeSpeedSearch(Tree tree) {
      new TreeSpeedSearch(tree);
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
      BegTreeHandleUtil.drawDottedRectangle(g,x,y,i,i1);
    }

    public void showListPopup(String title, JList list, Runnable finishAction, Project project, int x, int y) {
      new ListPopup(title, list, finishAction, project).show(x, y);

    }

    public void installSmartExpander(JTree tree) {
      SmartExpander.installOn(tree);
    }

    public void installSelectionSaver(JTree tree) {
      SelectionSaver.installOn(tree);
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
      public VcsContext createOn(AnActionEvent event) {
        return VcsContextWrapper.on(event);
      }
    };
  }
}