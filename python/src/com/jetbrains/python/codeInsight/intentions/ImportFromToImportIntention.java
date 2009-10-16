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
import com.intellij.psi.ResolveResult;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PythonLanguage;
import static com.jetbrains.python.codeInsight.intentions.DeclarationConflictChecker.findDefinitions;
import static com.jetbrains.python.codeInsight.intentions.DeclarationConflictChecker.showConflicts;
import com.jetbrains.python.psi.*;
import static com.jetbrains.python.psi.PyUtil.sure;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Transforms {@code from module_name import ...} into {@code import module_name}
 * and qualifies any names imported from that module by module name.
 * <br><small>
 * User: dcheryasov
 * Date: Sep 26, 2009 9:12:28 AM
 * </small>
 */
public class ImportFromToImportIntention implements IntentionAction {

  private PyFromImportStatement myFromImportStatement = null;
  private PyReferenceExpression myModuleReference = null;
  private String myModuleName = null;

  @NotNull
  public String getText() {
    String name = myModuleName != null? myModuleName : "...";
    return "Convert to 'import " + name + "'";
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INTN.Family.convert.import.qualify");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    myFromImportStatement = PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyFromImportStatement.class);
    if (myFromImportStatement != null) {
      myModuleReference = myFromImportStatement.getImportSource();
      if (myModuleReference != null) myModuleName = PyResolveUtil.toPath(myModuleReference, ".");
      return true;
    }
    return false;
  }

  /**
   * Adds myModuleName as a qualifier to target.
   * @param target_node what to qualify
   * @param generator 
   * @param project
   */
  private void qualifyTarget(ASTNode target_node, PyElementGenerator generator, Project project) {
    target_node.addChild(generator.createDot(project), target_node.getFirstChildNode());
    target_node.addChild(sure(generator.createFromText(project, PyReferenceExpression.class, myModuleName, new int[]{0,0}).getNode()), target_node.getFirstChildNode());
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    assert myFromImportStatement != null : "isAvailable() must have returned true, but myFromImportStatement is null";
    try {
      sure(myModuleReference); sure(myModuleName);
      // find all unqualified references that lead to one of our import elements
      final PyImportElement[] ielts = myFromImportStatement.getImportElements();
      final PyStarImportElement star_ielt = myFromImportStatement.getStarImportElement();
      final Map<PsiReference, PyImportElement> references = new HashMap<PsiReference, PyImportElement>();
      final List<PsiReference> star_references = new ArrayList<PsiReference>();
      PsiTreeUtil.processElements(file, new PsiElementProcessor() {
        public boolean execute(PsiElement element) {
          if (element instanceof PyReferenceExpression && PsiTreeUtil.getParentOfType(element, PyImportElement.class) == null && element.isValid()) {
            PyReferenceExpression ref = (PyReferenceExpression)element;
            if (ref.getQualifier() == null) {
              ResolveResult[] resolved = ref.multiResolve(false);
              for (ResolveResult rr : resolved) {
                if (rr.isValidResult()) {
                  if (rr.getElement() == star_ielt) star_references.add(ref);
                  for (PyImportElement ielt : ielts) {
                    if (rr.getElement() == ielt) references.put(ref, ielt);
                  }
                }
              }
            }
          }
          return true;
        }
      });

      // check that at every replacement site our topmost qualifier name is visible
      PyQualifiedExpression top_qualifier;
      PyExpression feeler = myModuleReference;
      do {
        sure(feeler instanceof PyQualifiedExpression); // if for some crazy reason module name refers to numbers, etc, no point to continue.
        top_qualifier = (PyQualifiedExpression)feeler;
        feeler = top_qualifier.getQualifier();
      } while (feeler != null);
      String top_name = top_qualifier.getName();
      Collection<PsiReference> possible_targets = references.keySet();
      if (star_references.size() > 0) {
        possible_targets = new ArrayList<PsiReference>(references.keySet().size() + star_references.size());
        possible_targets.addAll(references.keySet());
        possible_targets.addAll(star_references);
      }
      if (
        showConflicts(
          project,
          findDefinitions(top_name, possible_targets, Arrays.asList(myFromImportStatement.getImportElements())), 
          top_name, myModuleName
        )
      ) {
        return; // got conflicts
      }

      // add qualifiers
      Language language = myFromImportStatement.getLanguage();
      assert language instanceof PythonLanguage;
      PythonLanguage pythonLanguage = (PythonLanguage)language;
      PyElementGenerator generator = pythonLanguage.getElementGenerator();
      for (Map.Entry<PsiReference, PyImportElement> entry : references.entrySet()) {
        PsiElement referring_elt = entry.getKey().getElement();
        assert referring_elt.isValid(); // else we won't add it
        ASTNode target_node = referring_elt.getNode();
        assert target_node != null; // else it won't be valid
        PyImportElement ielt = entry.getValue();
        if (ielt.getAsName() != null) {
          // we have an alias, replace it with real name
          PyReferenceExpression refex = ielt.getImportReference();
          assert refex != null; // else we won't resolve to this ielt
          String real_name = refex.getReferencedName();
          ASTNode new_qualifier = generator.createExpressionFromText(project, real_name).getNode();
          assert new_qualifier != null;
          //ASTNode first_under_target = target_node.getFirstChildNode();
          //if (first_under_target != null) new_qualifier.addChildren(first_under_target, null, null); // save the children if any
          target_node.getTreeParent().replaceChild(target_node, new_qualifier);
          target_node = new_qualifier;
        }
        qualifyTarget(target_node, generator, project);
      }
      for (PsiReference reference : star_references) {
        PsiElement referring_elt = reference.getElement();
        assert referring_elt.isValid(); // else we won't add it
        ASTNode target_node = referring_elt.getNode();
        assert target_node != null; // else it won't be valid
        qualifyTarget(target_node, generator, project);
      }
      // transform the import statement
      PyImportStatement new_import = sure(generator.createFromText(project, PyImportStatement.class, "import "+myModuleName));
      ASTNode parent = sure(myFromImportStatement.getParent().getNode());
      ASTNode old_node = sure(myFromImportStatement.getNode());
      parent.replaceChild(old_node, sure(new_import.getNode()));
      //myFromImportStatement.replace(new_import);
    }
    catch (IncorrectOperationException ignored) {
      PyUtil.showBalloon(project, PyBundle.message("QFIX.action.failed"), MessageType.WARNING);
    }
  }

  public boolean startInWriteAction() {
    return true;
  }
}

