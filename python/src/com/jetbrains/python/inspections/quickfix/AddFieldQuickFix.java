/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.codeInsight.template.TemplateBuilderFactory;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.psi.PsiElement;
import com.intellij.util.Function;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.types.PyClassType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.jetbrains.python.PyNames.FAKE_OLD_BASE;

/**
 * Available on self.my_something when my_something is unresolved.
 * User: dcheryasov
 * Date: Apr 4, 2009 1:53:46 PM
 */
public class AddFieldQuickFix implements LocalQuickFix {

  private PyClassType myQualifierType;
  private final String myInitializer;
  private String myIdentifier;

  public AddFieldQuickFix(String identifier, PyClassType qualifierType, String initializer) {
    myIdentifier = identifier;
    myQualifierType = qualifierType;
    myInitializer = initializer;
  }

  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.NAME.add.field.$0.to.class.$1", myIdentifier, myQualifierType.getName());
  }

  @NotNull
  public String getFamilyName() {
    return "Add field to class";
  }

  @Nullable
  public static PsiElement appendToMethod(PyFunction init, Function<String, PyStatement> callback) {
    // add this field as the last stmt of the constructor
    final PyStatementList stmt_list = init.getStatementList();
    PyStatement[] stmts = stmt_list.getStatements(); // NOTE: rather wasteful, consider iterable stmt list
    PyStatement last_stmt = null;
    if (stmts.length > 0) last_stmt = stmts[stmts.length-1];
    // name of 'self' may be different for fancier styles
    PyParameter[] params = init.getParameterList().getParameters();
    String self_name = PyNames.CANONICAL_SELF;
    if (params.length > 0) {
      self_name = params[0].getName();
    }
    PyStatement new_stmt = callback.fun(self_name);
    if (!FileModificationService.getInstance().preparePsiElementForWrite(stmt_list)) return null;
    final PsiElement result = stmt_list.addAfter(new_stmt, last_stmt);
    PyPsiUtils.removeRedundantPass(stmt_list);
    return result;
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    // expect the descriptor to point to the unresolved identifier.
    PyClass cls = myQualifierType.getPyClass();
    PsiElement initStatement;
    if (!myQualifierType.isDefinition()) {
      initStatement = addFieldToInit(project, cls, myIdentifier, new CreateFieldCallback(project, myIdentifier, myInitializer));
    }
    else {
      PyStatement field = PyElementGenerator.getInstance(project)
        .createFromText(LanguageLevel.getDefault(), PyStatement.class, myIdentifier + " = " + myInitializer);
      initStatement = PyUtil.addElementToStatementList(field, cls.getStatementList(), true);
    }
    if (initStatement != null) {
      showTemplateBuilder(initStatement);
      return;
    }
    // somehow we failed. tell about this
    PyUtil.showBalloon(project, PyBundle.message("QFIX.failed.to.add.field"), MessageType.ERROR);
  }

  private void showTemplateBuilder(PsiElement initStatement) {
    initStatement = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(initStatement);
    if (initStatement instanceof PyAssignmentStatement) {
      final TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(initStatement);
      final PyExpression assignedValue = ((PyAssignmentStatement)initStatement).getAssignedValue();
      if (assignedValue != null) {
        builder.replaceElement(assignedValue, myInitializer);
        builder.run();
      }
    }
  }

  @Nullable
  public static PsiElement addFieldToInit(Project project, PyClass cls, String itemName, Function<String, PyStatement> callback) {
    if (cls != null && itemName != null) {
      PyFunction init = cls.findMethodByName(PyNames.INIT, false);
      if (init != null) {
        return appendToMethod(init, callback);
      }
      else { // no init! boldly copy ancestor's.
        for (PyClass ancestor : cls.getAncestorClasses()) {
          init = ancestor.findMethodByName(PyNames.INIT, false);
          if (init != null) break;
        }
        PyFunction newInit = createInitMethod(project, cls, init);
        if (newInit == null) {
          return null;
        }

        appendToMethod(newInit, callback);

        PsiElement addAnchor = null;
        PyFunction[] meths = cls.getMethods();
        if (meths.length > 0) addAnchor = meths[0].getPrevSibling();
        PyStatementList clsContent = cls.getStatementList();
        newInit = (PyFunction) clsContent.addAfter(newInit, addAnchor);

        PyUtil.showBalloon(project, PyBundle.message("QFIX.added.constructor.$0.for.field.$1", cls.getName(), itemName), MessageType.INFO);
        final PyStatementList statementList = newInit.getStatementList();
        assert statementList != null;
        return statementList.getStatements()[0];
        //else  // well, that can't be
      }
    }
    return null;
  }

  @Nullable
  private static PyFunction createInitMethod(Project project, PyClass cls, @Nullable PyFunction ancestorInit) {
    // found it; copy its param list and make a call to it.
    if (!FileModificationService.getInstance().preparePsiElementForWrite(cls)) {
      return null;
    }
    String paramList = ancestorInit != null ? ancestorInit.getParameterList().getText() : "(self)";

    String functionText = "def " + PyNames.INIT + paramList + ":\n";
    if (ancestorInit == null) functionText += "    pass";
    else {
      final PyClass ancestorClass = ancestorInit.getContainingClass();
      if (ancestorClass != null && ancestorClass != PyBuiltinCache.getInstance(ancestorInit).getClass("object") && !FAKE_OLD_BASE.equals(ancestorClass.getName())) {
        StringBuilder sb = new StringBuilder();
        PyParameter[] params = ancestorInit.getParameterList().getParameters();

        boolean seen = false;
        if (cls.isNewStyleClass()) {
          // form the super() call
          sb.append("super(");
          sb.append(cls.getName());

          // NOTE: assume that we have at least the first param
          String self_name = params[0].getName();
          sb.append(", ").append(self_name).append(").").append(PyNames.INIT).append("(");
        }
        else {
          sb.append(ancestorClass.getName());
          sb.append(".__init__(self");
          seen = true;
        }
        for (int i = 1; i < params.length; i += 1) {
          if (seen) sb.append(", ");
          else seen = true;
          sb.append(params[i].getText());
        }
        sb.append(")");
        functionText += "    " + sb.toString();
      }
      else {
        functionText += "    pass";
      }
    }
    return PyElementGenerator.getInstance(project).createFromText(
      LanguageLevel.getDefault(), PyFunction.class, functionText,
      new int[]{0}
    );
  }

  private static class CreateFieldCallback implements Function<String, PyStatement> {
    private Project myProject;
    private String myItemName;
    private String myInitializer;

    private CreateFieldCallback(Project project, String itemName, String initializer) {
      myProject = project;
      myItemName = itemName;
      myInitializer = initializer;
    }

    public PyStatement fun(String self_name) {
      return PyElementGenerator.getInstance(myProject).createFromText(LanguageLevel.getDefault(), PyStatement.class, self_name + "." + myItemName + " = " + myInitializer);
    }
  }
}
