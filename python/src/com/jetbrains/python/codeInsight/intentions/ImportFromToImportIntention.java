package com.jetbrains.python.codeInsight.intentions;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.ASTNode;
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
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.types.PyModuleType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.jetbrains.python.codeInsight.intentions.DeclarationConflictChecker.findDefinitions;
import static com.jetbrains.python.codeInsight.intentions.DeclarationConflictChecker.showConflicts;
import static com.jetbrains.python.psi.PyUtil.sure;

/**
 * Transforms:<ul>
 * <li>{@code from module_name import names} into {@code import module_name};</li>
 * <li>{@code from ...module_name import names} into {@code from ... import module_name};</li>
 * <li>{@code from ...moduleA.moduleB import names} into {@code from ...moduleA import moduleB}.</li>
 * Qualifies any names imported from that module by module name.
 * <br><small>
 * User: dcheryasov
 * Date: Sep 26, 2009 9:12:28 AM
 * </small>
 */
public class ImportFromToImportIntention implements IntentionAction {

  private PyFromImportStatement myFromImportStatement = null;
  private PyReferenceExpression myModuleReference = null;
  private String myModuleName = null;
  private int myRelativeLevel;
  private String myQualifier; // we don't always qualify with module name

  @NotNull
  public String getText() {
    String name = myModuleName != null? myModuleName : "?";
    if (myRelativeLevel > 0) {
      String[] relative_names = getRelativeNames(false);
      return PyBundle.message("INTN.convert.to.from.$0.import.$1", relative_names[0], relative_names[1]);
    }
    else return PyBundle.message("INTN.convert.to.import.$0", name);
  }

  // given module "...foo.bar". returns "...foo" and "bar"; if not strict, undefined names become "?".
  @Nullable
  private String[] getRelativeNames(boolean strict) {
    String remaining_name = "?";
    String separated_name = "?";
    boolean failure = true;
    if (myModuleReference != null) {
      PyExpression remaining_module = myModuleReference.getQualifier();
      if (remaining_module instanceof PyQualifiedExpression) {
        remaining_name = PyResolveUtil.toPath((PyQualifiedExpression)remaining_module, ".");
      }
      else remaining_name = ""; // unqualified name: "...module"
      separated_name = myModuleReference.getReferencedName();
      failure = false;
      if (separated_name == null) {
        separated_name = "?";
        failure = true;
      }
    }
    if (strict && failure) return null;
    else return new String[] {getDots()+remaining_name, separated_name};
  }

  private String getDots() {
    String dots = "";
    for (int i=0; i<myRelativeLevel; i+=1) dots += "."; // this generally runs 1-2 times, so it's cheaper than allocating a StringBuilder
    return dots;
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INTN.Family.convert.import.qualify");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    myModuleReference = null;
    final PsiElement position = file.findElementAt(editor.getCaretModel().getOffset());
    myFromImportStatement = PsiTreeUtil.getParentOfType(position, PyFromImportStatement.class);
    if (myFromImportStatement != null && myFromImportStatement.isValid() && !myFromImportStatement.isFromFuture()) {
      myRelativeLevel = myFromImportStatement.getRelativeLevel();
      myModuleReference = myFromImportStatement.getImportSource();
      if (myRelativeLevel > 0) {
        // make sure we aren't importing a module from the relative path
        for (PyImportElement import_element : myFromImportStatement.getImportElements()) {
          PyReferenceExpression ref = import_element.getImportReference();
          if (ref != null && ref.isValid()) {
            PsiElement target = ref.getReference().resolve();
            if (target instanceof PyExpression && ((PyExpression)target).getType(TypeEvalContext.fast()) instanceof PyModuleType) return false;
          }
        }

      }
    }
    if (myModuleReference != null) {
      myModuleName = PyResolveUtil.toPath(myModuleReference, ".");
    }
    return myModuleReference != null && myModuleName != null && myFromImportStatement != null;
  }

  /**
   * Adds myModuleName as a qualifier to target.
   * @param target_node what to qualify
   * @param project
   */
  private void qualifyTarget(ASTNode target_node, Project project) {
    final PyElementGenerator generator = PyElementGenerator.getInstance(project);
    target_node.addChild(generator.createDot(), target_node.getFirstChildNode());
    target_node.addChild(sure(generator.createFromText(PyReferenceExpression.class, myQualifier, new int[]{0,0}).getNode()), target_node.getFirstChildNode());
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    assert myFromImportStatement != null : "isAvailable() must have returned true, but myFromImportStatement is null";
    try {
      sure(myModuleReference); sure(myModuleName);
      String[] relative_names = null; // [0] is remaining import path, [1] is imported module name
      if (myRelativeLevel > 0) {
        relative_names = getRelativeNames(true);
        if (relative_names == null) throw new IncorrectOperationException("failed to get relative names");
        myQualifier = relative_names[1];
      }
      else myQualifier = myModuleName;
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
              ResolveResult[] resolved = ref.getReference().multiResolve(false);
              for (ResolveResult rr : resolved) {
                if (rr.isValidResult()) {
                  if (rr.getElement() == star_ielt) star_references.add(ref.getReference());
                  for (PyImportElement ielt : ielts) {
                    if (rr.getElement() == ielt) references.put(ref.getReference(), ielt);
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
      PyElementGenerator generator = PyElementGenerator.getInstance(project);
      for (Map.Entry<PsiReference, PyImportElement> entry : references.entrySet()) {
        PsiElement referring_elt = entry.getKey().getElement();
        assert referring_elt.isValid(); // else we won't add it
        ASTNode target_node = referring_elt.getNode();
        assert target_node != null; // else it won't be valid
        PyImportElement ielt = entry.getValue();
        if (ielt.getAsNameElement() != null) {
          // we have an alias, replace it with real name
          PyReferenceExpression refex = ielt.getImportReference();
          assert refex != null; // else we won't resolve to this ielt
          String real_name = refex.getReferencedName();
          ASTNode new_qualifier = generator.createExpressionFromText(real_name).getNode();
          assert new_qualifier != null;
          //ASTNode first_under_target = target_node.getFirstChildNode();
          //if (first_under_target != null) new_qualifier.addChildren(first_under_target, null, null); // save the children if any
          target_node.getTreeParent().replaceChild(target_node, new_qualifier);
          target_node = new_qualifier;
        }
        qualifyTarget(target_node, project);
      }
      for (PsiReference reference : star_references) {
        PsiElement referring_elt = reference.getElement();
        assert referring_elt.isValid(); // else we won't add it
        ASTNode target_node = referring_elt.getNode();
        assert target_node != null; // else it won't be valid
        qualifyTarget(target_node, project);
      }
      // transform the import statement
      PyStatement new_import;
      if (myRelativeLevel == 0) {
        new_import = sure(generator.createFromText(PyImportStatement.class, "import " + myModuleName));
      }
      else {
        new_import = sure(generator.createFromText(PyFromImportStatement.class, "from " + relative_names[0] +  " import " + relative_names[1]));
      }
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

