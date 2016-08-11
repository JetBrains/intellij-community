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
package com.jetbrains.python.codeInsight.intentions;

import com.google.common.collect.Sets;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
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
  private String myText;

  /**
   * This class exists to extract bunches of info we can't store in our stateless instance.
   * Instead, we store it per thread.
   */
  private static class InfoHolder {
    PyFromImportStatement myFromImportStatement = null;
    PyReferenceExpression myModuleReference = null;
    String myModuleName = null;
    int myRelativeLevel = 0;

    public String getText() {
      String name = myModuleName != null ? myModuleName : "...";
      if (myRelativeLevel > 0) {
        String[] relative_names = getRelativeNames(false, this);
        return PyBundle.message("INTN.convert.to.from.$0.import.$1", relative_names[0], relative_names[1]);
      }
      else {
        return PyBundle.message("INTN.convert.to.import.$0", name);
      }
    }

    public static InfoHolder collect(final PsiElement position) {
      InfoHolder ret = new InfoHolder();
      ret.myModuleReference = null;
      ret.myFromImportStatement = PsiTreeUtil.getParentOfType(position, PyFromImportStatement.class);
      PyPsiUtils.assertValid(ret.myFromImportStatement);
      if (ret.myFromImportStatement != null && !ret.myFromImportStatement.isFromFuture()) {
        ret.myRelativeLevel = ret.myFromImportStatement.getRelativeLevel();
        ret.myModuleReference = ret.myFromImportStatement.getImportSource();
      }
      if (ret.myModuleReference != null) {
        ret.myModuleName = PyPsiUtils.toPath(ret.myModuleReference);
      }
      return ret;
    }
  }

  @NotNull
  public String getText() {
    return myText;
  }

  @Nullable
  private static PsiElement getElementFromEditor(Editor editor, PsiFile file) {
    PsiElement element = null;
    Document doc = editor.getDocument();
    PsiFile a_file = file;
    if (a_file == null) {
      Project project = editor.getProject();
      if (project != null) a_file = PsiDocumentManager.getInstance(project).getPsiFile(doc);
    }
    if (a_file != null) element = a_file.findElementAt(editor.getCaretModel().getOffset());
    return element;
  }

  // given module "...foo.bar". returns "...foo" and "bar"; if not strict, undefined names become "?".
  @Nullable
  private static String[] getRelativeNames(boolean strict, InfoHolder info) {
    String remaining_name = "?";
    String separated_name = "?";
    boolean failure = true;
    if (info.myModuleReference != null) {
      PyExpression remaining_module = info.myModuleReference.getQualifier();
      if (remaining_module instanceof PyQualifiedExpression) {
        remaining_name = PyPsiUtils.toPath((PyQualifiedExpression)remaining_module);
      }
      else remaining_name = ""; // unqualified name: "...module"
      separated_name = info.myModuleReference.getReferencedName();
      failure = false;
      if (separated_name == null) {
        separated_name = "?";
        failure = true;
      }
    }
    if (strict && failure) return null;
    else return new String[] {getDots(info.myRelativeLevel)+remaining_name, separated_name};
  }

  private static String getDots(int level) {
    String dots = "";
    for (int i=0; i<level; i+=1) dots += "."; // this generally runs 1-2 times, so it's cheaper than allocating a StringBuilder
    return dots;
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INTN.Family.convert.import.qualify");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!(file instanceof PyFile)) {
      return false;
    }

    InfoHolder info = InfoHolder.collect(getElementFromEditor(editor, file));
    info.myModuleReference = null;
    final PsiElement position = file.findElementAt(editor.getCaretModel().getOffset());
    info.myFromImportStatement = PsiTreeUtil.getParentOfType(position, PyFromImportStatement.class);
    PyPsiUtils.assertValid(info.myFromImportStatement);
    if (info.myFromImportStatement != null && !info.myFromImportStatement.isFromFuture()) {
      info.myRelativeLevel = info.myFromImportStatement.getRelativeLevel();
      info.myModuleReference = info.myFromImportStatement.getImportSource();
      if (info.myRelativeLevel > 0) {
        // make sure we aren't importing a module from the relative path
        for (PyImportElement import_element : info.myFromImportStatement.getImportElements()) {
          PyReferenceExpression ref = import_element.getImportReferenceExpression();
          PyPsiUtils.assertValid(ref);
          if (ref != null) {
            PsiElement target = ref.getReference().resolve();
            final TypeEvalContext context = TypeEvalContext.codeAnalysis(file.getProject(), file);
            if (target instanceof PyExpression && context.getType((PyExpression)target) instanceof PyModuleType) {
              return false;
            }
          }
        }
      }
    }
    if (info.myModuleReference != null) {
      info.myModuleName = PyPsiUtils.toPath(info.myModuleReference);
    }
    if (info.myModuleReference != null && info.myModuleName != null && info.myFromImportStatement != null) {
      myText = info.getText();
      return true;
    }
    return false;
  }

  /**
   * Adds myModuleName as a qualifier to target.
   * @param target_node what to qualify
   * @param project
   * @param qualifier
   */
  private static void qualifyTarget(ASTNode target_node, Project project, String qualifier) {
    final PyElementGenerator generator = PyElementGenerator.getInstance(project);
    target_node.addChild(generator.createDot(), target_node.getFirstChildNode());
    target_node.addChild(sure(generator.createFromText(LanguageLevel.getDefault(), PyReferenceExpression.class, qualifier, new int[]{0,0}).getNode()), target_node.getFirstChildNode());
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    InfoHolder info = InfoHolder.collect(getElementFromEditor(editor, file));
    try {
      String qualifier; // we don't always qualify with module name
      sure(info.myModuleReference); sure(info.myModuleName);
      String[] relative_names = null; // [0] is remaining import path, [1] is imported module name
      if (info.myRelativeLevel > 0) {
        relative_names = getRelativeNames(true, info);
        if (relative_names == null) throw new IncorrectOperationException("failed to get relative names");
        qualifier = relative_names[1];
      }
      else qualifier = info.myModuleName;
      // find all unqualified references that lead to one of our import elements
      final PyImportElement[] ielts = info.myFromImportStatement.getImportElements();
      final PyStarImportElement star_ielt = info.myFromImportStatement.getStarImportElement();
      final Map<PsiReference, PyImportElement> references = new HashMap<>();
      final List<PsiReference> star_references = new ArrayList<>();
      PsiTreeUtil.processElements(file, new PsiElementProcessor() {
        public boolean execute(@NotNull PsiElement element) {
          PyPsiUtils.assertValid(element);
          if (element instanceof PyReferenceExpression && PsiTreeUtil.getParentOfType(element, PyImportElement.class) == null) {
            PyReferenceExpression ref = (PyReferenceExpression)element;
            if (!ref.isQualified()) {
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
      PyExpression feeler = info.myModuleReference;
      do {
        sure(feeler instanceof PyQualifiedExpression); // if for some crazy reason module name refers to numbers, etc, no point to continue.
        top_qualifier = (PyQualifiedExpression)feeler;
        feeler = top_qualifier.getQualifier();
      } while (feeler != null);
      String top_name = top_qualifier.getName();
      Collection<PsiReference> possible_targets = references.keySet();
      if (star_references.size() > 0) {
        possible_targets = new ArrayList<>(references.keySet().size() + star_references.size());
        possible_targets.addAll(references.keySet());
        possible_targets.addAll(star_references);
      }
      final Set<PsiElement> ignored = Sets.<PsiElement>newHashSet(Arrays.asList(info.myFromImportStatement.getImportElements()));
      if (top_name != null && showConflicts(project, findDefinitions(top_name, possible_targets, ignored),
                                            top_name, info.myModuleName)) {
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
          PyReferenceExpression refex = ielt.getImportReferenceExpression();
          assert refex != null; // else we won't resolve to this ielt
          String real_name = refex.getReferencedName();
          ASTNode new_qualifier = generator.createExpressionFromText(real_name).getNode();
          assert new_qualifier != null;
          //ASTNode first_under_target = target_node.getFirstChildNode();
          //if (first_under_target != null) new_qualifier.addChildren(first_under_target, null, null); // save the children if any
          target_node.getTreeParent().replaceChild(target_node, new_qualifier);
          target_node = new_qualifier;
        }
        qualifyTarget(target_node, project, qualifier);
      }
      for (PsiReference reference : star_references) {
        PsiElement referring_elt = reference.getElement();
        assert referring_elt.isValid(); // else we won't add it
        ASTNode target_node = referring_elt.getNode();
        assert target_node != null; // else it won't be valid
        qualifyTarget(target_node, project, qualifier);
      }
      // transform the import statement
      PyStatement new_import;
      if (info.myRelativeLevel == 0) {
        new_import = sure(generator.createFromText(LanguageLevel.getDefault(), PyImportStatement.class, "import " + info.myModuleName));
      }
      else {
        new_import = sure(generator.createFromText(LanguageLevel.getDefault(), PyFromImportStatement.class, "from " + relative_names[0] +  " import " + relative_names[1]));
      }
      ASTNode parent = sure(info.myFromImportStatement.getParent().getNode());
      ASTNode old_node = sure(info.myFromImportStatement.getNode());
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

