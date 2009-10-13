package com.jetbrains.python.codeInsight.intentions;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PythonLanguage;
import static com.jetbrains.python.codeInsight.intentions.DeclarationConflictChecker.findDefinitions;
import static com.jetbrains.python.codeInsight.intentions.DeclarationConflictChecker.showConflicts;
import com.jetbrains.python.psi.*;
import static com.jetbrains.python.psi.PyUtil.sure;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Adds an alias to "import foo" or "from foo import bar" import elements, or removes it if it's already present. 
 * User: dcheryasov
 * Date: Oct 9, 2009 6:07:19 PM
 */
public class ImportToggleAliasIntention implements IntentionAction {

  private PyImportElement myImportElement;
  private PyFromImportStatement myFromImportStatement;
  private PyImportStatement myImportStatement;
  private String myAlias;


  @NotNull
  public String getText() {
    String add_name = "Add alias";
    if (myImportElement != null) {
      PyReferenceExpression refex = myImportElement.getImportReference();
      if (refex != null) {
        String name = refex.getReferencedName();
        if (name != null && !"".equals(name)) {
          add_name = "Add alias to '" + name + "'";
        }
      }
    }
    return myAlias == null? add_name : "Remove alias '" + myAlias + "'";
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INTN.Family.toggle.import.alias");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    myImportElement  = PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyImportElement.class);
    if (myImportElement != null && myImportElement .isValid()) {
      PyTargetExpression target = myImportElement.getAsName();
      if (target != null && target.isValid()) myAlias = target.getName();
      else myAlias = null;
      myFromImportStatement = PsiTreeUtil.getParentOfType(myImportElement, PyFromImportStatement.class);
      if (myFromImportStatement != null && myFromImportStatement.isValid()) {
        return true;
      }
      else {
        myImportStatement = PsiTreeUtil.getParentOfType(myImportElement, PyImportStatement.class);
        return myImportStatement != null && myImportStatement.isValid();
      }
    }
    return false;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    // sanity check: isAvailable must have set it.
    assert myImportElement != null;
    assert myImportStatement != null || myFromImportStatement != null;
    //
    final String target_name; // we set in in the source
    final String remove_name; // we replace it in the source
    PyReferenceExpression reference = sure(myImportElement.getImportReference());
    // search for references to us with the right name
    try {
      String imported_name = PyResolveUtil.toPath(reference, ".");
      if (myAlias != null) {
        // have to remove alias, rename everything to original
        target_name = imported_name;
        remove_name = myAlias;
      }
      else {
        // ask for and add alias
        AskNameDialog dialog = new AskNameDialog(project);
        dialog.setTitle("Alias for '" + imported_name + "'?");
        dialog.show();
        if (!dialog.isOK()) return; // 'Cancel' button cancels everything
        target_name = dialog.getAlias();
        remove_name = imported_name;
      }
      final PsiElement referee = reference.resolve();
      if (referee != null && imported_name != null) {
        final Collection<PsiReference> references = new ArrayList<PsiReference>();
        PsiTreeUtil.processElements(file, new PsiElementProcessor() {
          public boolean execute(PsiElement element) {
            if (element instanceof PyReferenceExpression && PsiTreeUtil.getParentOfType(element, PyImportElement.class) == null) {
              PyReferenceExpression ref = (PyReferenceExpression)element;
              if (remove_name.equals(PyResolveUtil.toPath(ref, "."))) {  // filter out other names that might resolve to our target
                PsiElement resolved = ref.resolve();
                if (resolved == referee) references.add(ref);
              }
            }
            return true;
          }
        });
        // no references here is OK by us.
        if (showConflicts(project, findDefinitions(target_name, references, null), target_name, null)) {
          return; // got conflicts
        }

        // alter the import element
        Language language = myImportElement.getLanguage();
        assert language instanceof PythonLanguage;
        PythonLanguage pythonLanguage = (PythonLanguage)language;
        PyElementGenerator generator = pythonLanguage.getElementGenerator();
        if (myAlias != null) {
          // remove alias
          ASTNode node = sure(myImportElement.getNode());
          ASTNode parent = sure(node.getTreeParent());
          node = sure(node.getFirstChildNode()); // this is the reference
          node = sure(node.getTreeNext()); // things past the reference: space, 'as', and alias
          parent.removeRange(node, null);
        }
        else {
          // add alias
          ASTNode my_ielt_node = sure(myImportElement.getNode());
          PyImportElement fountain = generator.createFromText(project, PyImportElement.class, "import foo as "+target_name, new int[]{0,2});
          ASTNode graft_node = sure(fountain.getNode()); // at import elt
          graft_node = sure(graft_node.getFirstChildNode()); // at ref
          graft_node = sure(graft_node.getTreeNext()); // space
          my_ielt_node.addChild((ASTNode)graft_node.clone());
          graft_node = sure(graft_node.getTreeNext()); // 'as'
          my_ielt_node.addChild((ASTNode)graft_node.clone());
          graft_node = sure(graft_node.getTreeNext()); // space
          my_ielt_node.addChild((ASTNode)graft_node.clone());
          graft_node = sure(graft_node.getTreeNext()); // alias
          my_ielt_node.addChild((ASTNode)graft_node.clone());
        }
        // alter references
        for (PsiReference ref : references) {
          ASTNode ref_name_node = sure(sure(ref.getElement()).getNode());
          ASTNode parent = sure(ref_name_node.getTreeParent());
          ASTNode new_name_node = generator.createExpressionFromText(project, target_name).getNode();
          assert new_name_node != null;
          parent.replaceChild(ref_name_node, new_name_node);
        }
      }
    }
    catch (IncorrectOperationException ignored) {
      PyUtil.showBalloon(project, PyBundle.message("QFIX.action.failed"), MessageType.WARNING);
    }
  }

  public boolean startInWriteAction() {
    return true;
  }
}
