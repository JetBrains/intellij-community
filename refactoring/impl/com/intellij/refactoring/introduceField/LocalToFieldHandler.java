package com.intellij.refactoring.introduceField;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.TestUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import static com.intellij.refactoring.introduceField.BaseExpressionToFieldHandler.InitializationPlace.IN_CONSTRUCTOR;
import static com.intellij.refactoring.introduceField.BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;

public class LocalToFieldHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.introduceField.LocalToFieldHandler");

  public static final String REFACTORING_NAME = RefactoringBundle.message("convert.local.to.field.title");
  private final Project myProject;
  private final boolean myIsConstant;
  private final PsiManager myManager;

  public LocalToFieldHandler(Project project, boolean isConstant) {
    myProject = project;
    myManager = PsiManager.getInstance(myProject);
    myIsConstant = isConstant;
  }

  public boolean convertLocalToField(final PsiLocalVariable local, Editor editor) {
    PsiClass aClass;
    boolean tempIsStatic = myIsConstant;
    PsiElement parent = local.getParent();

    while (true) {
      if (parent instanceof PsiClass && !(parent instanceof PsiAnonymousClass)) {
        aClass = (PsiClass)parent;
        break;
      }
      if (parent instanceof PsiFile && PsiUtil.isInJspFile(parent)) {
        String message = RefactoringBundle.message("error.not.supported.for.jsp", REFACTORING_NAME);
        CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.LOCAL_TO_FIELD, myProject);
        return false;
      }
      if (parent instanceof PsiModifierListOwner &&((PsiModifierListOwner)parent).hasModifierProperty(PsiModifier.STATIC)) {
        tempIsStatic = true;
      }
      parent = parent.getParent();
    }

    final boolean isStatic = tempIsStatic;

    PsiExpression[] occurences = CodeInsightUtil.findReferenceExpressions(RefactoringUtil.getVariableScope(local),
                                                                          local
    );
    if (editor != null) {
      RefactoringUtil.highlightAllOccurences(myProject, occurences, editor);
    }

    //LocalToFieldDialog dialog = new LocalToFieldDialog(project, aClass, local, isStatic);
    final String variableName;
    final String fieldName;
    final BaseExpressionToFieldHandler.InitializationPlace initializerPlace;
    final boolean declareFinal;
    final String fieldVisibility;
    final TypeSelectorManagerImpl typeSelectorManager = new TypeSelectorManagerImpl(myProject, local.getType(),
                                                                                    occurences
    );

    boolean rebindNeeded = false;
    if (!myIsConstant) {
      PsiMethod method = PsiTreeUtil.getParentOfType(local, PsiMethod.class);
      IntroduceFieldDialog dialog = new IntroduceFieldDialog(myProject, aClass,
                                                             local.getInitializer(), local,
                                                             method != null && method.isConstructor(),
                                                             true, isStatic,
                                                             occurences.length, method != null, method != null,
                                                             typeSelectorManager
      );
      dialog.show();
      if (!dialog.isOK()) return false;
      variableName = local.getName();
      fieldName = dialog.getEnteredName();
      initializerPlace = dialog.getInitializerPlace();
      declareFinal = dialog.isDeclareFinal();
      fieldVisibility = dialog.getFieldVisibility();
    }
    else {
      IntroduceConstantDialog dialog = new IntroduceConstantDialog(myProject, aClass,
                                                                   local.getInitializer(), local, true, occurences, aClass, typeSelectorManager
      );
      dialog.show();
      if (!dialog.isOK()) return false;
      variableName = local.getName();
      fieldName = dialog.getEnteredName();
      declareFinal = true;
      initializerPlace = IN_FIELD_DECLARATION;
      fieldVisibility = dialog.getFieldVisibility();
      final PsiClass destinationClass = dialog.getDestinationClass();
      if (destinationClass != null) {
        aClass = destinationClass;
        rebindNeeded = true;
      }
    }

    final PsiClass aaClass = aClass;
    final boolean rebindNeeded1 = rebindNeeded;
    final Runnable runnable = new Runnable() {
      public void run() {
        try {
          final boolean rebindNeeded2 = !variableName.equals(fieldName) || rebindNeeded1;
          final PsiReference[] refs;
          if (rebindNeeded2) {
            PsiManager manager = local.getManager();
            PsiSearchHelper helper = manager.getSearchHelper();
            refs = helper.findReferences(local, GlobalSearchScope.projectScope(myProject), false);
          }
          else {
            refs = null;
          }

          final PsiMethod enclosingConstructor = BaseExpressionToFieldHandler.getEnclosingConstructor(aaClass, local);
          PsiField field = createField(local, fieldName,
                                       initializerPlace == IN_FIELD_DECLARATION
          );
          if (isStatic) {
            field.getModifierList().setModifierProperty(PsiModifier.STATIC, true);
          }
          if (declareFinal) {
            field.getModifierList().setModifierProperty(PsiModifier.FINAL, true);
          }
          field.getModifierList().setModifierProperty(fieldVisibility, true);

          field = (PsiField)aaClass.add(field);
          local.normalizeDeclaration();
          PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)local.getParent();
          final BaseExpressionToFieldHandler.InitializationPlace finalInitializerPlace;
          if (local.getInitializer() == null) {
            finalInitializerPlace = IN_FIELD_DECLARATION;
          }
          else {
            finalInitializerPlace = initializerPlace;
          }
          final PsiElementFactory factory = myManager.getElementFactory();

          switch (finalInitializerPlace) {
            case IN_FIELD_DECLARATION:
              declarationStatement.delete();
              break;

            case IN_CURRENT_METHOD:
              PsiStatement statement = createAssignment(local, fieldName, factory);
              declarationStatement.replace(statement);
              break;

            case IN_CONSTRUCTOR:
              addInitializationToConstructors(local, field, enclosingConstructor, factory);
              break;
            case IN_SETUP_METHOD:
            addInitializationToSetUp(local, field, factory);
          }

          if (enclosingConstructor != null && initializerPlace == IN_CONSTRUCTOR) {
            PsiStatement statement = createAssignment(local, fieldName, factory);
            declarationStatement.replace(statement);
          }

          if (rebindNeeded2) {
            for (final PsiReference reference : refs) {
              if (reference != null) {
                //expr = RefactoringUtil.outermostParenthesizedExpression(expr);
                RefactoringUtil.replaceOccurenceWithFieldRef((PsiExpression)reference, field, aaClass);
                //replaceOccurenceWithFieldRef((PsiExpression)reference, field, aaClass);
              }
            }
            //RefactoringUtil.renameVariableReferences(local, pPrefix + fieldName, GlobalSearchScope.projectScope(myProject));
          }
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    };
    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(runnable);
      }
    }, REFACTORING_NAME, null);
    return true;
  }

  private PsiField createField(PsiLocalVariable local, String fieldName, boolean includeInitializer) {
    @NonNls StringBuffer pattern = new StringBuffer();
    pattern.append("private int ");
    pattern.append(fieldName);
    if (local.getInitializer() == null) {
      includeInitializer = false;
    }
    if (includeInitializer) {
      pattern.append("=0");
    }
    pattern.append(";");
    PsiElementFactory factory = myManager.getElementFactory();
    try {
      PsiField field = factory.createFieldFromText(pattern.toString(), null);
      field = (PsiField)CodeStyleManager.getInstance(myProject).reformat(field);

      field.getTypeElement().replace(factory.createTypeElement(local.getType()));
      if (includeInitializer) {
        PsiExpression initializer =
          RefactoringUtil.convertInitializerToNormalExpression(local.getInitializer(), local.getType());
        field.getInitializer().replace(initializer);
      }

      return field;
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return null;
    }
  }

  private PsiStatement createAssignment(PsiLocalVariable local, String fieldname, PsiElementFactory factory) {
    try {
      String pattern = fieldname + "=0;";
      PsiExpressionStatement statement = (PsiExpressionStatement)factory.createStatementFromText(pattern, null);
      statement = (PsiExpressionStatement)CodeStyleManager.getInstance(myProject).reformat(statement);

      PsiAssignmentExpression expr = (PsiAssignmentExpression)statement.getExpression();
      final PsiExpression initializer = RefactoringUtil.convertInitializerToNormalExpression(local.getInitializer(), local.getType());
      expr.getRExpression().replace(initializer);

      return statement;
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return null;
    }
  }

  private void addInitializationToSetUp(final PsiLocalVariable local, final PsiField field, final PsiElementFactory factory)
                                                                                                                             throws IncorrectOperationException {
    PsiMethod inClass = TestUtil.findSetUpMethod(field.getContainingClass());
    assert inClass != null;
    PsiStatement assignment = createAssignment(local, field.getName(), factory);
    final PsiCodeBlock body = inClass.getBody();
    assert body != null;
    body.add(assignment);
    if (!PsiTreeUtil.isAncestor(inClass, local, true)) local.delete();
  }

  private void addInitializationToConstructors(PsiLocalVariable local, PsiField field, PsiMethod enclosingConstructor,
                                               PsiElementFactory factory) throws IncorrectOperationException {
    PsiClass aClass = field.getContainingClass();
    PsiMethod[] constructors = aClass.getConstructors();
    PsiStatement assignment = createAssignment(local, field.getName(), factory);
    boolean added = false;
    for (PsiMethod constructor : constructors) {
      if (constructor == enclosingConstructor) continue;
      PsiCodeBlock body = constructor.getBody();
      if (body == null) continue;
      PsiStatement[] statements = body.getStatements();
      if (statements.length > 0) {
        PsiStatement first = statements[0];
        if (first instanceof PsiExpressionStatement) {
          PsiExpression expression = ((PsiExpressionStatement)first).getExpression();
          if (expression instanceof PsiMethodCallExpression) {
            @NonNls String text = ((PsiMethodCallExpression)expression).getMethodExpression().getText();
            if ("this".equals(text)) {
              continue;
            }
          }
        }
      }
      body.add(assignment);
      added = true;
    }
    if (!added && enclosingConstructor == null) {
      PsiMethod constructor = factory.createConstructor();
      constructor.getBody().add(assignment);
      aClass.add(constructor);
    }

    if (enclosingConstructor == null) local.delete();
  }
}