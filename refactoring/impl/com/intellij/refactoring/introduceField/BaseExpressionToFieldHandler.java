/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 29.05.2002
 * Time: 13:05:34
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.introduceField;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.TestUtil;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.IntroduceHandlerBase;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import static com.intellij.refactoring.introduceField.BaseExpressionToFieldHandler.InitializationPlace.*;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.occurences.OccurenceManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.List;

public abstract class BaseExpressionToFieldHandler extends IntroduceHandlerBase implements RefactoringActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.introduceField.BaseExpressionToFieldHandler");

  public enum InitializationPlace {
    IN_CURRENT_METHOD,
    IN_FIELD_DECLARATION,
    IN_CONSTRUCTOR,
    IN_SETUP_METHOD;
  }

  private PsiClass myParentClass;

  protected boolean invokeImpl(final Project project, final PsiExpression selectedExpr, final Editor editor) {
    LOG.assertTrue(selectedExpr != null);
    final PsiFile file = selectedExpr.getContainingFile();
    LOG.assertTrue(file != null, "expr.getContainingFile() == null");

    if (LOG.isDebugEnabled()) {
      LOG.debug("expression:" + selectedExpr);
    }

    myParentClass = getParentClass(selectedExpr);
    if (myParentClass == null) {
      if (PsiUtil.isInJspFile(file)) {
        CommonRefactoringUtil.showErrorMessage(getRefactoringName(),
                                               RefactoringBundle.message("error.not.supported.for.jsp", getRefactoringName()), getHelpID(),
                                               project);
        return false;
      }
      else {
        LOG.assertTrue(false);
        return false;
      }
    }

    if (!validClass(myParentClass)) {
      return false;
    }

    PsiType tempType = getTypeByExpression(selectedExpr);
    if (tempType == null) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("unknown.expression.type"));
      CommonRefactoringUtil.showErrorMessage(getRefactoringName(), message, getHelpID(), project);
      return false;
    }

    if (tempType == PsiType.VOID) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("selected.expression.has.void.type"));
      CommonRefactoringUtil.showErrorMessage(getRefactoringName(), message, getHelpID(), project);
      return false;
    }

    PsiElement tempAnchorElement = RefactoringUtil.getParentExpressionAnchorElement(selectedExpr);
    if (tempAnchorElement == null) {
      //TODO : work outside code block (e.g. field initializer)
      String message = RefactoringBundle.message("refactoring.is.not.supported.in.the.current.context", getRefactoringName());
      CommonRefactoringUtil.showErrorMessage(getRefactoringName(), message, getHelpID(), project);
      return false;
    }

    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, file)) return false;

    final PsiClass parentClass = myParentClass;
    final OccurenceManager occurenceManager = createOccurenceManager(selectedExpr, parentClass);
    final PsiExpression[] occurrences = occurenceManager.getOccurences();
    final PsiElement anchorStatementIfAll = occurenceManager.getAnchorStatementForAll();

    ArrayList<RangeHighlighter> highlighters = null;
    if (editor != null) {
      final HighlightManager highlightManager;
      highlighters = new ArrayList<RangeHighlighter>();
      highlightManager = HighlightManager.getInstance(project);
      if (occurrences.length > 1) {
        highlightManager.addOccurrenceHighlights(editor, occurrences, highlightAttributes(), true, highlighters);
      }
    }


    final Settings settings =
      showRefactoringDialog(project, myParentClass, selectedExpr, tempType,
                            occurrences, tempAnchorElement, anchorStatementIfAll
      );

    if (settings == null) return false;

    if (settings.getForcedType() != null) {
      tempType = settings.getForcedType();
    }
    final PsiType type = tempType;

    final String fieldName = settings.getFieldName();
    final PsiElement anchorElementIfOne = tempAnchorElement;
    final boolean replaceAll = settings.isReplaceAll();
    if (replaceAll) {
      tempAnchorElement = anchorStatementIfAll;
    }
    final PsiElement anchorElement = tempAnchorElement;


    if (editor != null) {
      HighlightManager highlightManager = HighlightManager.getInstance(project);
      for (RangeHighlighter highlighter : highlighters) {
        highlightManager.removeSegmentHighlighter(editor, highlighter);
      }
    }

    PsiElement anchor = getNormalizedAnchor(anchorElement);

    boolean tempDeleteSelf = false;
    if (selectedExpr.getParent() instanceof PsiExpressionStatement && anchor.equals(anchorElement)) {
      PsiStatement statement = (PsiStatement)selectedExpr.getParent();
      if (statement.getParent() instanceof PsiCodeBlock) {
        tempDeleteSelf = true;
      }
    }
    final boolean deleteSelf = tempDeleteSelf;

    final Runnable runnable = new Runnable() {
      public void run() {
        try {
          PsiExpression expr = selectedExpr;
          InitializationPlace initializerPlace = settings.getInitializerPlace();
          final PsiLocalVariable localVariable = settings.getLocalVariable();
          final boolean deleteLocalVariable = settings.isDeleteLocalVariable();
          PsiExpression initializer;
          if (localVariable != null) {
            initializer = localVariable.getInitializer();
          }
          else {
            initializer = expr;
          }

          final PsiMethod enclosingConstructor = getEnclosingConstructor(myParentClass, anchorElement);
          final PsiClass destClass = settings.getDestinationClass() == null ? myParentClass : settings.getDestinationClass();

          if (!CommonRefactoringUtil.checkReadOnlyStatus(project, destClass.getContainingFile())) return;

          PsiField field = createField(fieldName, type, initializer,
                                       initializerPlace == IN_FIELD_DECLARATION && initializer != null
          );
          field.getModifierList().setModifierProperty(settings.getFieldVisibility(), true);
          if (settings.isDeclareFinal()) {
            field.getModifierList().setModifierProperty(PsiModifier.FINAL, true);
          }
          if (settings.isDeclareStatic()) {
            field.getModifierList().setModifierProperty(PsiModifier.STATIC, true);
          }
          if (settings.isAnnotateAsNonNls()) {
            PsiAnnotation annotation = myParentClass.getManager().getElementFactory().createAnnotationFromText("@" + AnnotationUtil.NON_NLS, myParentClass);
            field.getModifierList().addAfter(annotation, null);
            CodeStyleManager.getInstance(myParentClass.getProject()).shortenClassReferences(field.getModifierList());
          }
          PsiElement finalAnchorElement = null;
          if (destClass == myParentClass) {
            for (finalAnchorElement = anchorElement;
                 finalAnchorElement != null && finalAnchorElement.getParent() != destClass;
                 finalAnchorElement = finalAnchorElement.getParent()) {
              ;
            }
          }
          PsiMember anchorMember = finalAnchorElement instanceof PsiMember ? ((PsiMember)finalAnchorElement) : null;

          if ((anchorMember instanceof PsiField || anchorMember instanceof PsiClassInitializer) &&
              anchorMember.hasModifierProperty(PsiModifier.STATIC) == field.hasModifierProperty(PsiModifier.STATIC)) {
            field = (PsiField)destClass.addBefore(field, anchorMember);
          }
          else {
            field = (PsiField)destClass.add(field);
          }
          PsiStatement assignStatement;
          if ((initializerPlace == IN_CURRENT_METHOD && initializer != null)
              || (initializerPlace == IN_CONSTRUCTOR && enclosingConstructor != null && initializer != null)) {
            final PsiElement anchorElementHere;
            if (replaceAll) {
              if (enclosingConstructor != null) {
                final PsiElement anchorInConstructor = occurenceManager.getAnchorStatementForAllInScope(enclosingConstructor);
                anchorElementHere = anchorInConstructor != null ? anchorInConstructor : anchorStatementIfAll;
              } else {
                anchorElementHere = anchorStatementIfAll;
              }
            }
            else {
              anchorElementHere = anchorElementIfOne;
            }
            assignStatement = createAssignment(field, initializer, anchorElementHere);
            anchorElementHere.getParent().addBefore(assignStatement, getNormalizedAnchor(anchorElementHere));
          }
          if (initializerPlace == IN_CONSTRUCTOR && initializer != null) {
            addInitializationToConstructors(initializer, field, enclosingConstructor);
          }
          if (initializerPlace == IN_SETUP_METHOD && initializer != null) {
            addInitializationToSetUp(initializer, field, occurenceManager, replaceAll);
          }
          if (expr.getParent() instanceof PsiParenthesizedExpression) {
            expr = (PsiExpression)expr.getParent();
          }
          if (deleteSelf) {
            PsiStatement statement = (PsiStatement)expr.getParent();
            statement.delete();
          }

          if (replaceAll) {
            List<PsiElement> array = new ArrayList<PsiElement>();
            for (PsiExpression occurrence : occurrences) {
              if (occurrence instanceof PsiExpression) {
                occurrence = RefactoringUtil.outermostParenthesizedExpression(occurrence);
              }
              if (deleteSelf && occurrence.equals(expr)) continue;
              array.add(RefactoringUtil.replaceOccurenceWithFieldRef(occurrence, field, destClass));
            }

            if (editor != null) {
              if (!ApplicationManager.getApplication().isUnitTestMode()) {
                PsiElement[] exprsToHighlight = array.toArray(new PsiElement[array.size()]);
                HighlightManager highlightManager = HighlightManager.getInstance(project);
                highlightManager.addOccurrenceHighlights(editor, exprsToHighlight, highlightAttributes(), true, null);
                WindowManager.getInstance().getStatusBar(project).setInfo(RefactoringBundle.message("press.escape.to.remove.the.highlighting"));
              }
            }
          }
          else {
            if (!deleteSelf) {
              expr = RefactoringUtil.outermostParenthesizedExpression(expr);
              RefactoringUtil.replaceOccurenceWithFieldRef(expr, field, destClass);
            }
          }


          if (localVariable != null) {
            if (deleteLocalVariable) {
              localVariable.normalizeDeclaration();
              localVariable.getParent().delete();
            }
          }
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    };

    CommandProcessor.getInstance().executeCommand(
      project,
      new Runnable() {
        public void run() {
          ApplicationManager.getApplication().runWriteAction(runnable);
        }
      },
      getRefactoringName(), null
      );

    return true;
  }

  private TextAttributes highlightAttributes() {
    return EditorColorsManager.getInstance().getGlobalScheme().getAttributes(
                EditorColors.SEARCH_RESULT_ATTRIBUTES
              );
  }

  protected abstract OccurenceManager createOccurenceManager(PsiExpression selectedExpr, PsiClass parentClass);

  protected final PsiClass getParentClass() {
    return myParentClass;
  }

  abstract protected boolean validClass(PsiClass parentClass);

  protected boolean isStaticField() {
    return false;
  }

  private PsiElement getNormalizedAnchor(PsiElement anchorElement) {
    PsiElement child = anchorElement;
    while (child != null) {
      PsiElement prev = child.getPrevSibling();
      if (RefactoringUtil.isExpressionAnchorElement(prev)) break;
      if (prev instanceof PsiJavaToken && ((PsiJavaToken)prev).getTokenType() == JavaTokenType.LBRACE) break;
      child = prev;
    }

    child = PsiTreeUtil.skipSiblingsForward(child, new Class[] {PsiWhiteSpace.class, PsiComment.class});
    PsiElement anchor;
    if (child != null) {
      anchor = child;
    }
    else {
      anchor = anchorElement;
    }
    return anchor;
  }

  protected abstract String getHelpID();

  protected abstract Settings showRefactoringDialog(Project project, PsiClass parentClass, PsiExpression expr,
                                                    PsiType type, PsiExpression[] occurences, PsiElement anchorElement,
                                                    PsiElement anchorElementIfAll);


  private PsiType getTypeByExpression(PsiExpression expr) {
    return RefactoringUtil.getTypeByExpressionWithExpectedType(expr);
  }

  public PsiClass getParentClass(PsiExpression initializerExpression) {
    PsiElement parent = initializerExpression.getParent();
    while (parent != null) {
      if (parent instanceof PsiClass && !(parent instanceof PsiAnonymousClass)) {
        return (PsiClass)parent;
      }
      parent = parent.getParent();
    }
    return null;
  }

  public static PsiMethod getEnclosingConstructor(PsiClass parentClass, PsiElement element) {
    if (element == null) return null;
    final PsiMethod[] constructors = parentClass.getConstructors();
    for (PsiMethod constructor : constructors) {
      if (PsiTreeUtil.isAncestor(constructor, element, false)) return constructor;
    }
    return null;
  }

  private void addInitializationToSetUp(final PsiExpression initializer,
                                        final PsiField field,
                                        final OccurenceManager occurenceManager, final boolean replaceAll) throws IncorrectOperationException {
    final PsiMethod setupMethod = TestUtil.findSetUpMethod(myParentClass);

    assert setupMethod != null;

    PsiElement anchor = null;
    if (PsiTreeUtil.isAncestor(setupMethod, initializer, true)) {
      anchor = replaceAll
               ? occurenceManager.getAnchorStatementForAllInScope(setupMethod)
               : PsiTreeUtil.getParentOfType(initializer, PsiStatement.class);
    }

    final PsiExpressionStatement expressionStatement =
      (PsiExpressionStatement)myParentClass.getManager().getElementFactory().createStatementFromText(field.getName() + "= expr;", null);
    PsiAssignmentExpression expr = (PsiAssignmentExpression)expressionStatement.getExpression();
    final PsiExpression rExpression = expr.getRExpression();
    LOG.assertTrue(rExpression != null);
    rExpression.replace(initializer);

    final PsiCodeBlock body = setupMethod.getBody();
    assert body != null;
    body.addBefore(expressionStatement, anchor);
  }

  private void addInitializationToConstructors(PsiExpression initializerExpression, PsiField field, PsiMethod enclosingConstructor) {
    try {
      PsiClass aClass = field.getContainingClass();
      PsiMethod[] constructors = aClass.getConstructors();

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
        PsiStatement assignment = createAssignment(field, initializerExpression, body.getLastChild());
        body.add(assignment);
        added = true;
      }
      if (!added && enclosingConstructor == null) {
        PsiElementFactory factory = field.getManager().getElementFactory();
        PsiMethod constructor = (PsiMethod)aClass.add(factory.createConstructor());
        final PsiCodeBlock body = constructor.getBody();
        PsiStatement assignment = createAssignment(field, initializerExpression, body.getLastChild());
        body.add(assignment);
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private PsiField createField(String fieldName, PsiType type, PsiExpression initializerExpr, boolean includeInitializer) {
    @NonNls StringBuffer pattern = new StringBuffer();
    pattern.append("private int ");
    pattern.append(fieldName);
    if (includeInitializer) {
      pattern.append("=0");
    }
    pattern.append(";");
    PsiManager psiManager = myParentClass.getManager();
    PsiElementFactory factory = psiManager.getElementFactory();
    try {
      PsiField field = factory.createFieldFromText(pattern.toString(), null);
      field = (PsiField)CodeStyleManager.getInstance(psiManager.getProject()).reformat(field);
      field.getTypeElement().replace(factory.createTypeElement(type));
      if (includeInitializer) {
        field.getInitializer().replace(initializerExpr);
      }
      return field;
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return null;
    }
  }

  private PsiStatement createAssignment(PsiField field, PsiExpression initializerExpr, PsiElement context) {
    try {
      @NonNls String pattern = "x=0;";
      PsiManager psiManager = myParentClass.getManager();
      PsiElementFactory factory = psiManager.getElementFactory();
      PsiExpressionStatement statement = (PsiExpressionStatement)factory.createStatementFromText(pattern, null);
      statement = (PsiExpressionStatement)CodeStyleManager.getInstance(psiManager.getProject()).reformat(statement);

      PsiAssignmentExpression expr = (PsiAssignmentExpression)statement.getExpression();
      final PsiExpression rExpression = expr.getRExpression();
      LOG.assertTrue(rExpression != null);
      rExpression.replace(initializerExpr);
      final PsiReferenceExpression fieldReference = RenameUtil.createFieldReference(field, context);
      expr.getLExpression().replace(fieldReference);

      return statement;
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return null;
    }
  }

  protected abstract String getRefactoringName();

  public boolean startInWriteAction() {
    return false;
  }

  public static class Settings {
    private final String myFieldName;
    private final PsiType myForcedType;

    private boolean myReplaceAll;
    private final boolean myDeclareStatic;
    private final boolean myDeclareFinal;
    private final InitializationPlace myInitializerPlace;
    private final String myVisibility;
    private final boolean myDeleteLocalVariable;
    private final PsiClass myTargetClass;
    private final boolean myAnnotateAsNonNls;

    public PsiLocalVariable getLocalVariable() {
      return myLocalVariable;
    }

    public boolean isDeleteLocalVariable() {
      return myDeleteLocalVariable;
    }

    private final PsiLocalVariable myLocalVariable;

    public String getFieldName() {
      return myFieldName;
    }

    public boolean isDeclareStatic() {
      return myDeclareStatic;
    }

    public boolean isDeclareFinal() {
      return myDeclareFinal;
    }

    public InitializationPlace getInitializerPlace() {
      return myInitializerPlace;
    }

    public String getFieldVisibility() {
      return myVisibility;
    }

    public PsiClass getDestinationClass() { return myTargetClass; }

    public PsiType getForcedType() {
      return myForcedType;
    }

    public boolean isReplaceAll() {
      return myReplaceAll;
    }

    public boolean isAnnotateAsNonNls() {
      return myAnnotateAsNonNls;
    }

    public Settings(String fieldName, boolean replaceAll,
                    boolean declareStatic, boolean declareFinal,
                    InitializationPlace initializerPlace, String visibility, PsiLocalVariable localVariableToRemove, PsiType forcedType,
                    boolean deleteLocalVariable, PsiClass targetClass, final boolean annotateAsNonNls) {

      myFieldName = fieldName;
      myReplaceAll = replaceAll;
      myDeclareStatic = declareStatic;
      myDeclareFinal = declareFinal;
      myInitializerPlace = initializerPlace;
      myVisibility = visibility;
      myLocalVariable = localVariableToRemove;
      myDeleteLocalVariable = deleteLocalVariable;
      myForcedType = forcedType;
      myTargetClass = targetClass;
      myAnnotateAsNonNls = annotateAsNonNls;
    }


  }
}
