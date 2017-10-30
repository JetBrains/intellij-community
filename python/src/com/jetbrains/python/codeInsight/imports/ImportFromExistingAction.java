/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.codeInsight.imports;

import com.intellij.codeInsight.hint.QuestionAction;
import com.intellij.ide.DataManager;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.QualifiedName;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBList;
import com.intellij.util.Consumer;
import com.intellij.util.ObjectUtils;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

import static com.jetbrains.python.psi.PyUtil.as;

/**
 * Turns an unqualified unresolved identifier into qualified and resolvable.
 *
 * @author dcheryasov
 */
public class ImportFromExistingAction implements QuestionAction {
  PsiElement myTarget;
  List<ImportCandidateHolder> mySources; // list of <import, imported_item>
  String myName;
  boolean myUseQualifiedImport;
  private Runnable myOnDoneCallback;
  private final boolean myImportLocally;

  /**
   * @param target element to become qualified as imported.
   * @param sources clauses of import to be used.
   * @param name relevant name ot the target element (e.g. of identifier in an expression).
   * @param useQualified if True, use qualified "import modulename" instead of "from modulename import ...".
   */
  public ImportFromExistingAction(@NotNull PsiElement target, @NotNull List<ImportCandidateHolder> sources, @NotNull String name,
                                  boolean useQualified, boolean importLocally) {
    myTarget = target;
    mySources = sources;
    myName = name;
    myUseQualifiedImport = useQualified;
    myImportLocally = importLocally;
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
    PyPsiUtils.assertValid(myTarget);
    if ((myTarget instanceof PyQualifiedExpression) && ((((PyQualifiedExpression)myTarget).isQualified()))) return false; // we cannot be qualified
    for (ImportCandidateHolder item : mySources) {
      PyPsiUtils.assertValid(item.getImportable());
      PyPsiUtils.assertValid(item.getFile());
      final PyImportElement element = item.getImportElement();
      if (element != null) {
        PyPsiUtils.assertValid(element);
      }
    }
    if (mySources.isEmpty()) {
      return false;
    }
    // act
    if (mySources.size() == 1 || ApplicationManager.getApplication().isUnitTestMode()) {
      doWriteAction(mySources.get(0));
    }
    else {
      selectSourceAndDo();
    }
    return true;
  }

  private void selectSourceAndDo() {
    // GUI part
    ImportCandidateHolder[] items = mySources.toArray(new ImportCandidateHolder[mySources.size()]); // silly JList can't handle modern collections
    final JList list = new JBList(items);
    list.setCellRenderer(new CellRenderer(myName));

    final Runnable runnable = () -> {
      final Object selected = list.getSelectedValue();
      if (selected instanceof ImportCandidateHolder) {
        final ImportCandidateHolder item = (ImportCandidateHolder)selected;
        PsiDocumentManager.getInstance(myTarget.getProject()).commitAllDocuments();
        doWriteAction(item);
      }
    };

    DataManager.getInstance().getDataContextFromFocus().doWhenDone((Consumer<DataContext>)dataContext -> new PopupChooserBuilder(list)
      .setTitle(myUseQualifiedImport? PyBundle.message("ACT.qualify.with.module") : PyBundle.message("ACT.from.some.module.import"))
      .setItemChoosenCallback(runnable)
      .setFilteringEnabled(o -> ((ImportCandidateHolder) o).getPresentableText(myName))
      .createPopup()
      .showInBestPositionFor(dataContext));
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

    final PsiFileSystemItem filesystemAnchor = ObjectUtils.chooseNotNull(as(item.getImportable(), PsiFileSystemItem.class), item.getFile());
    AddImportHelper.ImportPriority priority = AddImportHelper.getImportPriority(myTarget, filesystemAnchor);
    PsiFile file = myTarget.getContainingFile();
    InjectedLanguageManager manager = InjectedLanguageManager.getInstance(project);
    if (manager.isInjectedFragment(file)) {
      file = manager.getTopLevelFile(myTarget);
    }
    // We are trying to import top-level module or package which thus cannot be qualified
    if (isRoot(item.getFile())) {
      if (myImportLocally) {
        AddImportHelper.addLocalImportStatement(myTarget, myName);
      } else {
        AddImportHelper.addImportStatement(file, myName, item.getAsName(), priority, null);
      }
    }
    else {
      final QualifiedName path = item.getPath();
      final String qualifiedName = path != null ? path.toString() : "";
      if (myUseQualifiedImport) {
        String nameToImport = qualifiedName;
        if (item.getImportable() instanceof PsiFileSystemItem) {
          nameToImport += "." + myName;
        }
        if (myImportLocally) {
          AddImportHelper.addLocalImportStatement(myTarget, nameToImport);
        }
        else {
          AddImportHelper.addImportStatement(file, nameToImport, item.getAsName(), priority, null);
        }
        myTarget.replace(gen.createExpressionFromText(LanguageLevel.forElement(myTarget), qualifiedName + "." + myName));
      }
      else {
        if (myImportLocally) {
          AddImportHelper.addLocalFromImportStatement(myTarget, qualifiedName, myName);
        }
        else {
          // "Update" scenario takes place inside injected fragments, for normal AST addToExistingImport() will be used instead
          AddImportHelper.addOrUpdateFromImportStatement(file, qualifiedName, myName, item.getAsName(), priority, null);
        }
      }
    }
  }


  private void addToExistingImport(PyImportElement src) {
    final PyElementGenerator gen = PyElementGenerator.getInstance(myTarget.getProject());
    // did user choose 'import' or 'from import'?
    PsiElement parent = src.getParent();
    if (parent instanceof PyFromImportStatement) {
      // add another import element right after the one we got
      PsiElement newImportElement = gen.createImportElement(LanguageLevel.getDefault(), myName, null);
      parent.add(newImportElement);
      CodeStyleManager.getInstance(myTarget.getProject()).reformat(parent);
    }
    else { // just 'import'
      // all we need is to qualify our target
      myTarget.replace(gen.createExpressionFromText(LanguageLevel.forElement(myTarget), src.getVisibleName() + "." + myName));
    }
  }

  private void doWriteAction(final ImportCandidateHolder item) {
    PsiElement src = item.getImportable();
    new WriteCommandAction(src.getProject(), PyBundle.message("ACT.CMD.use.import"), myTarget.getContainingFile()) {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        doIt(item);
      }
    }.execute();
    if (myOnDoneCallback != null) {
      myOnDoneCallback.run();
    }
  }

  public static boolean isRoot(PsiFileSystemItem directory) {
    if (directory == null) return true;
    VirtualFile vFile = directory.getVirtualFile();
    if (vFile == null) return true;
    ProjectFileIndex fileIndex = ProjectFileIndex.SERVICE.getInstance(directory.getProject());
    return Comparing.equal(fileIndex.getClassRootForFile(vFile), vFile) ||
           Comparing.equal(fileIndex.getContentRootForFile(vFile), vFile) ||
           Comparing.equal(fileIndex.getSourceRootForFile(vFile), vFile);
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
