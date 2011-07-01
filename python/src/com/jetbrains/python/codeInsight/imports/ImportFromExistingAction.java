package com.jetbrains.python.codeInsight.imports;

import com.intellij.codeInsight.hint.QuestionAction;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBList;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Turns an unqualified unresolved identifier into qualified and resolvable.
 *
 * @author dcheryasov
 */
public class ImportFromExistingAction implements QuestionAction {
  PyElement myTarget;
  List<ImportCandidateHolder> mySources; // list of <import, imported_item>
  String myName;
  boolean myUseQualifiedImport;
  private Runnable myOnDoneCallback;

  /**
   * @param target element to become qualified as imported.
   * @param sources clauses of import to be used.
   * @param name relevant name ot the target element (e.g. of identifier in an expression).
   * @param useQualified if True, use qualified "import modulename" instead of "from modulename import ...".
   */
  public ImportFromExistingAction(@NotNull PyElement target, @NotNull List<ImportCandidateHolder> sources, String name,
                                  boolean useQualified) {
    myTarget = target;
    mySources = sources;
    myName = name;
    myUseQualifiedImport = useQualified;
  }

  public void onDone(Runnable callback) {
    assert myOnDoneCallback == null;
    myOnDoneCallback = callback;
  }


  /**
   * Alters either target (by qualifying a name) or source (by explicitly importing the name).
   * @return true if action succeeded
   */
  public boolean execute() {
    // check if the tree is sane
    PsiDocumentManager.getInstance(myTarget.getProject()).commitAllDocuments();
    if (!myTarget.isValid()) return false;
    if ((myTarget instanceof PyQualifiedExpression) && ((((PyQualifiedExpression)myTarget).getQualifier() != null))) return false; // we cannot be qualified
    for (ImportCandidateHolder item : mySources) {
      if (!item.getImportable().isValid()) return false;
      if (!item.getFile().isValid()) return false;
      if (item.getImportElement() != null && !item.getImportElement().isValid()) return false;
    }
    if (mySources.isEmpty()) {
      return false;
    }
    // act
    if (mySources.size() > 1) {
      selectSourceAndDo();
    }
    else doWriteAction(mySources.get(0));
    return true;
  }

  private void selectSourceAndDo() {
    // GUI part
    ImportCandidateHolder[] items = mySources.toArray(new ImportCandidateHolder[mySources.size()]); // silly JList can't handle modern collections
    final JList list = new JBList(items);
    list.setCellRenderer(new CellRenderer(myName));

    final Runnable runnable = new Runnable() {
      public void run() {
        int index = list.getSelectedIndex();
        if (index < 0) return;
        PsiDocumentManager.getInstance(myTarget.getProject()).commitAllDocuments();
        doWriteAction(mySources.get(index));
      }
    };

    DataManager.getInstance().getDataContextFromFocus().doWhenDone(new AsyncResult.Handler<DataContext>() {
      @Override
      public void run(DataContext dataContext) {
        new PopupChooserBuilder(list).
          setTitle(myUseQualifiedImport? PyBundle.message("ACT.qualify.with.module") : PyBundle.message("ACT.from.some.module.import")).
          setItemChoosenCallback(runnable).
          createPopup().
          showInBestPositionFor(dataContext);
      }
    });
  }

  private void doIt(final ImportCandidateHolder item) {
    PyImportElement src = item.getImportElement();
    if (src != null) {
      addToExistingImport(src);
    }
    else { // no existing import, add it then use it
      addImportStatement(item);
    }
  }

  private void addImportStatement(ImportCandidateHolder item) {
    final Project project = myTarget.getProject();
    final PyElementGenerator gen = PyElementGenerator.getInstance(project);
    AddImportHelper.ImportPriority priority = AddImportHelper.getImportPriority(myTarget, item.getFile());
    if (isRoot(project, item.getFile())) {
      AddImportHelper.addImportStatement(myTarget.getContainingFile(), myName, null, priority);
    }
    else {
      String qualifiedName = item.getPath().toString();
      if (myUseQualifiedImport) {
        AddImportHelper.addImportStatement(myTarget.getContainingFile(), qualifiedName, null, priority);
        String qual_name;
        if (item.getAsName() != null) qual_name = item.getAsName();
        else qual_name = qualifiedName;
        myTarget.replace(gen.createExpressionFromText(qual_name + "." + myName));
      }
      else {
        AddImportHelper.addImportFrom(myTarget.getContainingFile(), qualifiedName, myName, null, priority);
      }
    }
  }

  private void addToExistingImport(PyImportElement src) {
    final PyElementGenerator gen = PyElementGenerator.getInstance(myTarget.getProject());
    // did user choose 'import' or 'from import'?
    PsiElement parent = src.getParent();
    if (parent instanceof PyFromImportStatement) {
      // add another import element right after the one we got
      PsiElement new_elt = gen.createImportElement(myName);
      PyUtil.addListNode(parent, new_elt, null, false, true);
    }
    else { // just 'import'
      // all we need is to qualify our target
      myTarget.replace(gen.createExpressionFromText(src.getVisibleName() + "." + myName));
    }
  }

  private void doWriteAction(final ImportCandidateHolder item) {
    PsiElement src = item.getImportable();
    new WriteCommandAction(src.getProject(), PyBundle.message("ACT.CMD.use.import"), myTarget.getContainingFile()) {
      @Override
      protected void run(Result result) throws Throwable {
        doIt(item);
      }
    }.execute();
    if (myOnDoneCallback != null) {
      myOnDoneCallback.run();
    }
  }

  public static boolean isRoot(Project project, PsiFileSystemItem directory) {
    if (directory == null) return true;
    VirtualFile vFile = directory.getVirtualFile();
    if (vFile == null) return true;
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    return fileIndex.getClassRootForFile(vFile) == vFile ||
           fileIndex.getContentRootForFile(vFile) == vFile ||
           fileIndex.getSourceRootForFile(vFile) == vFile;
  }

  public static boolean isResolved(PsiReference reference) {
    if (reference instanceof PsiPolyVariantReference) {
      return ((PsiPolyVariantReference)reference).multiResolve(false).length > 0;
    }
    return reference.resolve() != null;
  }

  // Stolen from FQNameCellRenderer
  private static class CellRenderer extends SimpleColoredComponent implements ListCellRenderer {
    private final Font FONT;
    private final String myName;

    public CellRenderer(String name) {
      myName = name;
      EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
      FONT = new Font(scheme.getEditorFontName(), Font.PLAIN, scheme.getEditorFontSize());
      setOpaque(true);
    }

    // value is a QualifiedHolder
    public Component getListCellRendererComponent(
      JList list,
      Object value, // expected to be
      int index,
      boolean isSelected,
      boolean cellHasFocus
    ){

      clear();

      ImportCandidateHolder item = (ImportCandidateHolder)value;
      setIcon(item.getImportable().getIcon(0));
      String item_name = item.getPresentableText(myName);
      append(item_name, SimpleTextAttributes.REGULAR_ATTRIBUTES);

      String tailText = item.getTailText();
      if (tailText != null) {
        append(" " + tailText, SimpleTextAttributes.GRAY_ATTRIBUTES);
      }

      setFont(FONT);
      if (isSelected) {
        setBackground(list.getSelectionBackground());
        setForeground(list.getSelectionForeground());
      }
      else {
        setBackground(list.getBackground());
        setForeground(list.getForeground());
      }
      return this;
    }
  }
}
