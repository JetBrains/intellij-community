package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.*;
import com.intellij.codeInsight.completion.proc.VariablesProcessor;
import com.intellij.codeInsight.intention.impl.CreateClassDialog;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.codeInsight.template.*;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;

import java.util.*;

/**
 * @author mike
 */
public class CreateFromUsageUtils {
  private static final Logger LOG = Logger.getInstance(
    "#com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageUtils");
  private static final int MAX_GUESSED_MEMBERS_COUNT = 10;

  public static boolean isSimpleReference (PsiReferenceExpression refExpr) {
    return refExpr.getQualifierExpression() == null;
  }

  public static boolean isValidReference(PsiReference reference) {
    if (!(reference instanceof PsiJavaReference)) return false;
    return ((PsiJavaReference) reference).multiResolve(true).length > 0;
  }

  public static boolean isValidMethodReference(PsiReference reference, PsiMethodCallExpression call) {
    if (!(reference instanceof PsiJavaReference)) return false;
    try {
      final ResolveResult candidate = ((PsiJavaReference) reference).advancedResolve(true);
      final PsiElement result = candidate.getElement();
      if (!(result instanceof PsiMethod)) return false;
      return PsiUtil.isApplicable((PsiMethod) result, candidate.getSubstitutor(),
                                  call.getArgumentList());
    }
    catch (ClassCastException cce) {
      // rear case
      return false;
    }
  }

  public static boolean shouldCreateConstructor(PsiClass targetClass, PsiExpressionList argList, PsiMethod candidate) {
    if (argList == null) return false;
    if (candidate == null) {
      if (targetClass == null || targetClass.isInterface() || targetClass instanceof PsiTypeParameter) return false;
      if (argList.getExpressions().length == 0 && targetClass.getConstructors().length == 0) return false;
      return true;
    }
    else {
      return !PsiUtil.isApplicable(candidate, PsiSubstitutor.EMPTY, argList);
    }
  }

  public static void setupMethodBody(PsiClass targetClass, PsiMethod method) throws IncorrectOperationException {
    final PsiType returnType = method.getReturnType() != null ? method.getReturnType() : PsiType.VOID;

    final PsiElementFactory factory = method.getManager().getElementFactory();

    LOG.assertTrue(!targetClass.isInterface(), "Interface bodies should be already set up");

    FileTemplate template = FileTemplateManager.getInstance().getCodeTemplate(
      FileTemplateManager.TEMPLATE_FROM_USAGE_METHOD_BODY);
    FileType fileType = FileTypeManager.getInstance().getFileTypeByExtension(template.getExtension());
    Properties properties = new Properties();
    properties.setProperty(FileTemplate.ATTRIBUTE_RETURN_TYPE, returnType.getPresentableText());
    properties.setProperty(FileTemplate.ATTRIBUTE_DEFAULT_RETURN_VALUE,
                           CodeInsightUtil.getDefaultValueOfType(returnType));

    FileTemplateUtil.setClassAndMethodNameProperties(properties, targetClass, method);

    String methodText;
    CodeStyleManager csManager = CodeStyleManager.getInstance(method.getProject());
    try {
      String bodyText = template.getText(properties);
      if (!"".equals(bodyText)) bodyText += "\n";
      methodText = returnType.getPresentableText() + " foo () {\n" + bodyText + "}";
      methodText = FileTemplateUtil.indent(methodText, method.getProject(), fileType);
    }
    catch (Exception e) {
      throw new IncorrectOperationException("Failed to parse file template");
    }

    if (methodText != null) {
      PsiMethod m;
      try {
        m = factory.createMethodFromText(methodText, targetClass);
      }
      catch (IncorrectOperationException e) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
              public void run() {
                Messages.showErrorDialog("Please Correct \"New Method Body\" Template", "File Template Error");
              }
            });
        return;
      }
      method.getBody().replace(m.getBody());
      csManager.reformat(method);
    }
  }

  public static void setupEditor(PsiMethod method, final Editor newEditor) {
    PsiCodeBlock body = method.getBody();
    if (body != null) {
      PsiElement l = PsiTreeUtil.skipSiblingsForward(body.getLBrace(), new Class[] {PsiWhiteSpace.class});
      PsiElement r = PsiTreeUtil.skipSiblingsBackward(body.getRBrace(), new Class[] {PsiWhiteSpace.class});
      if (l != null && r != null) {
        int start = l.getTextRange().getStartOffset(),
            end   = r.getTextRange().getEndOffset();
        newEditor.getCaretModel().moveToOffset(Math.max(start, end));
        newEditor.getSelectionModel().setSelection(Math.min(start, end), Math.max(start, end));
        newEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      }
    }
  }

  public static void setupMethodParameters(
    PsiMethod method,
    TemplateBuilder builder,
    PsiExpressionList argumentList,
    PsiSubstitutor substitutor) throws IncorrectOperationException {
    if (argumentList == null) return;
    PsiManager psiManager = method.getManager();
    final PsiElementFactory factory = psiManager.getElementFactory();

    final PsiParameterList parameterList = method.getParameterList();

    PsiExpression[] args = argumentList.getExpressions();
    GlobalSearchScope resolveScope = method.getResolveScope();

    GuessTypeParameters guesser = new GuessTypeParameters(argumentList.getManager().getElementFactory());

    for (int i = 0; i < args.length; i++) {
      PsiExpression arg = args[i];

      SuggestedNameInfo suggestedInfo = CodeStyleManager.getInstance(psiManager.getProject()).suggestVariableName(
        VariableKind.PARAMETER, null, arg, null);
      String[] names = suggestedInfo.names; //TODO: callback about used name

      if (names.length == 0) {
        names = new String[]{"p" + i};
      }

      PsiType argType = arg.getType();
      if (argType == null || argType == PsiType.NULL) {
        argType = PsiType.getJavaLangObject(psiManager, resolveScope);
      }

      PsiParameter parameter = factory.createParameter(names[0], argType);
      parameter = (PsiParameter) parameterList.add(parameter);

      ExpectedTypeInfo info = ExpectedTypesProvider.getInstance(psiManager.getProject()).createInfo(argType,
                                                                                                    ExpectedTypeInfo.TYPE_OR_SUPERTYPE, argType, TailType.NONE);

      PsiElement context = PsiTreeUtil.getParentOfType(argumentList, new Class[] {PsiClass.class, PsiMethod.class});
      guesser.setupTypeElement(parameter.getTypeElement(), new ExpectedTypeInfo[]{info},
                               substitutor, builder, context, method.getContainingClass());

      Expression expression = new ParameterNameExpression(names);
      builder.replaceElement(parameter.getNameIdentifier(), expression);
    }
  }

  public static PsiClass createClass(
    final PsiJavaCodeReferenceElement referenceElement,
    final boolean createInterface,
    final String superClassName) {
    final String name = referenceElement.getReferenceName();

    if (referenceElement.getQualifier() instanceof PsiJavaCodeReferenceElement) {
      PsiJavaCodeReferenceElement qualifier = (PsiJavaCodeReferenceElement) referenceElement.getQualifier();
      final PsiElement psiElement = qualifier.resolve();
      if (psiElement instanceof PsiClass) {
        return ApplicationManager.getApplication().runWriteAction(
          new Computable<PsiClass>() {
            public PsiClass compute() {
              try {
                PsiClass psiClass = (PsiClass) psiElement;
                if (!CodeInsightUtil.prepareClassForWrite(psiClass)) return null;

                PsiManager manager = psiClass.getManager();
                PsiElementFactory elementFactory = manager.getElementFactory();
                PsiClass result = createInterface ? elementFactory.createInterface(name) : elementFactory.createClass(name);
                result = (PsiClass)manager.getCodeStyleManager().reformat(result);
                return (PsiClass) psiClass.add(result);
              }
              catch (IncorrectOperationException e) {
                LOG.error(e);
                return null;
              }
            }
          });
      }
    }

    final PsiFile sourceFile = referenceElement.getContainingFile();
    PsiDirectory sourceDir = sourceFile.getContainingDirectory();

    final PsiManager manager = referenceElement.getManager();
    final PsiElementFactory factory = manager.getElementFactory();

    PsiDirectory targetDirectory = null;
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      Project project = manager.getProject();
      String title = createInterface ? "Create Interface" : "Create Class";
      final PsiPackage aPackage = sourceDir.getPackage();
      CreateClassDialog dialog = new CreateClassDialog(project, title, name,
                                                       aPackage != null ? aPackage.getQualifiedName() : "",
                                                       createInterface, false);
      dialog.show();
      if (dialog.getExitCode() != CreateClassDialog.OK_EXIT_CODE) return null;

      targetDirectory = dialog.getTargetDirectory();
      if (targetDirectory == null) return null;
    }

    final PsiDirectory directory = targetDirectory;
    return ApplicationManager.getApplication().runWriteAction(
      new Computable<PsiClass>() {
        public PsiClass compute() {
          try {
            PsiClass targetClass;
            if (!ApplicationManager.getApplication().isUnitTestMode()) {
              try {
                if (createInterface) {
                  targetClass = directory.createInterface(name);
                }
                else {
                  targetClass = directory.createClass(name);
                }
              }
              catch (final IncorrectOperationException e) {
                ApplicationManager.getApplication().invokeLater(new Runnable() {
                              public void run() {
                                Messages.showErrorDialog("Cannot create " + name +  ".java in " + directory.getVirtualFile().getName() + ": " + e.getMessage(), "File Creation Failed");
                              }
                            });
                return null;
              }
              if (!manager.getResolveHelper().isAccessible(targetClass, referenceElement, null)) {
                targetClass.getModifierList().setModifierProperty("public", true);
              }
            }
            else {
              final PsiClass aClass;
              if (createInterface) {
                aClass = factory.createInterface(name);
              }
              else {
                aClass = factory.createClass(name);
              }
              targetClass = (PsiClass) sourceFile.add(aClass);
            }

            if (superClassName != null) {
              PsiJavaCodeReferenceElement superClass = factory.createReferenceElementByFQClassName(superClassName, targetClass.getResolveScope());
              targetClass.getExtendsList().add(superClass);
            }

            return targetClass;
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
            return null;
          }
        }
      });
  }

  public static PsiReferenceExpression[] collectExpressions(final PsiExpression expression, Class[] scopes) {
    PsiElement parent = PsiTreeUtil.getParentOfType(expression, scopes);

    final List<PsiReferenceExpression> result = new ArrayList<PsiReferenceExpression>();
    PsiRecursiveElementVisitor visitor = new PsiRecursiveElementVisitor() {
      public List getResult() {
        return result;
      }

      public void visitReferenceExpression(PsiReferenceExpression expr) {
        if (expression instanceof PsiReferenceExpression) {
          if (!isValidReference(expr) && expr.textMatches(expression)) {
            result.add(expr);
          }
        }
        visitElement(expr);
      }

      public void visitMethodCallExpression(PsiMethodCallExpression expr) {
        if (expression instanceof PsiMethodCallExpression) {
          PsiReferenceExpression methodExpression = expr.getMethodExpression();
          if (!isValidMethodReference(methodExpression, expr) &&
              methodExpression.textMatches(((PsiMethodCallExpression) expression).getMethodExpression())) {
            result.add(expr.getMethodExpression());
          }
        }
        visitElement(expr);
      }
    };

    parent.accept(visitor);
    return result.toArray(new PsiReferenceExpression[result.size()]);
  }

  public static PsiVariable[] guessMatchingVariables (final PsiExpression expression) {
    List<ExpectedTypeInfo[]> typesList = new ArrayList<ExpectedTypeInfo[]>();
    final List<String> expectedMethodNames = new ArrayList<String>();
    final List<String> expectedFieldNames  = new ArrayList<String>();

    getExpectedInformation(expression, typesList, expectedMethodNames, expectedFieldNames);

    final List<PsiVariable> list = new ArrayList<PsiVariable>();
    VariablesProcessor varproc = new VariablesProcessor("", true, list){
      public boolean execute(PsiElement element, PsiSubstitutor substitutor) {
        if(!(element instanceof PsiField) ||
           element.getManager().getResolveHelper().isAccessible(((PsiField)element), expression, null)) {
          return super.execute(element, substitutor);
        }
        return true;
      }
    };
    PsiScopesUtil.treeWalkUp(varproc, expression, null);
    PsiVariable[] allVars = varproc.getResultsAsArray();

    ExpectedTypeInfo[] infos = ExpectedTypeUtil.intersect(typesList);

    final List<PsiVariable> result = new ArrayList<PsiVariable>();
    nextVar:
    for (int i = 0; i < allVars.length; i++) {
      PsiVariable variable = allVars[i];
      PsiType varType = variable.getType();
      boolean matched = infos.length == 0;
      for (int j = 0; j < infos.length; j++) {
        if (ExpectedTypeUtil.matches(varType, infos[j])) {
          matched = true;
          break;
        };
      }

      if (matched) {
        for (Iterator<String> iterator = expectedFieldNames.iterator(); iterator.hasNext();) {
          String name = iterator.next();
          if (!(varType instanceof PsiClassType)) continue nextVar;
          PsiClass aClass = PsiUtil.resolveClassInType(varType);
          if (aClass.findFieldByName(name, true) == null) continue nextVar;
        }

        for (Iterator<String> iterator = expectedMethodNames.iterator(); iterator.hasNext();) {
          String name = iterator.next();
          if (!(varType instanceof PsiClassType)) continue nextVar;
          PsiClass aClass = PsiUtil.resolveClassInType(varType);
          PsiMethod[] methods = aClass.findMethodsByName(name, true);
          if (methods == null || methods.length == 0) continue nextVar;
        }

        result.add(variable);
      }
    }

    return result.toArray(new PsiVariable[result.size()]);
  }

  private static void getExpectedInformation (PsiExpression expression, List<ExpectedTypeInfo[]> types,
                                              List<String> expectedMethodNames,
                                              List<String> expectedFieldNames) {
    Class[] scopes = new Class[]{PsiMethod.class, PsiClassInitializer.class, PsiClass.class, PsiField.class,
                                 PsiFile.class};
    PsiExpression[] expressions = collectExpressions(expression, scopes);

    for (int i = 0; i < expressions.length; i++) {
      PsiExpression expr = expressions[i];
      PsiElement parent = expr.getParent();
      if (parent instanceof PsiReferenceExpression) {
        PsiElement pparent = parent.getParent();
        if (pparent instanceof PsiMethodCallExpression) {
          String refName = ((PsiReferenceExpression) parent).getReferenceName();
          if (refName != null) {
            expectedMethodNames.add(refName);
          }
        }
        else if (pparent instanceof PsiReferenceExpression || pparent instanceof PsiVariable ||
                 pparent instanceof PsiExpression) {
          String refName = ((PsiReferenceExpression) parent).getReferenceName();
          if (refName != null) {
            expectedFieldNames.add(refName);
          }
        }
      }
      else {
        ExpectedTypeInfo[] someExpectedTypes = ExpectedTypesProvider.getInstance(expression.getProject()).getExpectedTypes(expr, false);
        if (someExpectedTypes.length > 0) {
          types.add(someExpectedTypes);
        }
      }
    }
  }

  public static ExpectedTypeInfo[] guessExpectedTypes (PsiExpression expression, boolean allowVoidType) {
        final PsiManager manager = expression.getManager();
    final GlobalSearchScope resolveScope = expression.getResolveScope();

    ExpectedTypesProvider provider = ExpectedTypesProvider.getInstance(manager.getProject());
    List<ExpectedTypeInfo[]> typesList = new ArrayList<ExpectedTypeInfo[]>();
    final List<String> expectedMethodNames = new ArrayList<String>();
    final List<String> expectedFieldNames  = new ArrayList<String>();

    getExpectedInformation(expression, typesList, expectedMethodNames, expectedFieldNames);

    if (typesList.size() == 1 && (expectedFieldNames.size() > 0 || expectedMethodNames.size() > 0)) {
      ExpectedTypeInfo[] infos = typesList.get(0);
      if (infos.length == 1 && infos[0].getKind() == ExpectedTypeInfo.TYPE_OR_SUBTYPE &&
          infos[0].getType().equals(PsiType.getJavaLangObject(manager, resolveScope))) {
        typesList.clear();
      }
    }

    if (typesList.size() == 0) {
      PsiElementFactory factory = manager.getElementFactory();
      for (Iterator<String> iterator = expectedFieldNames.iterator(); iterator.hasNext();) {
        String fieldName = iterator.next();
        PsiField[] fields = manager.getShortNamesCache().getFieldsByName(fieldName, resolveScope);
        addMemberInfo(fields, expression, typesList, factory);
      }

      for (Iterator<String> iterator = expectedMethodNames.iterator(); iterator.hasNext();) {
        String methodName = iterator.next();
        PsiMethod[] methods = manager.getShortNamesCache().getMethodsByName(methodName, resolveScope);
        addMemberInfo(methods, expression, typesList, factory);
      }
    }

    ExpectedTypeInfo[] expectedTypes = ExpectedTypeUtil.intersect(typesList);
    if (expectedTypes.length == 0 && !typesList.isEmpty()) {
      List<ExpectedTypeInfo> union = new ArrayList<ExpectedTypeInfo>();
      for (Iterator<ExpectedTypeInfo[]> i = typesList.iterator(); i.hasNext();) {
        union.addAll(Arrays.asList((ExpectedTypeInfo[]) i.next()));
      }
      expectedTypes = union.toArray(new ExpectedTypeInfo[union.size()]);
    }

    if (expectedTypes == null || expectedTypes.length == 0) {
      PsiType t = allowVoidType ? PsiType.VOID : PsiType.getJavaLangObject(manager, resolveScope);
      expectedTypes = new ExpectedTypeInfo[] {provider.createInfo(t, ExpectedTypeInfo.TYPE_STRICTLY, t, TailType.NONE)};
    }

    return expectedTypes;
  }


  public static PsiType[] guessType(PsiExpression expression, final boolean allowVoidType) {
    final PsiManager manager = expression.getManager();
    final GlobalSearchScope resolveScope = expression.getResolveScope();

    List<ExpectedTypeInfo[]> typesList = new ArrayList<ExpectedTypeInfo[]>();
    final List<String> expectedMethodNames = new ArrayList<String>();
    final List<String> expectedFieldNames  = new ArrayList<String>();

    getExpectedInformation(expression, typesList, expectedMethodNames, expectedFieldNames);

    if (typesList.size() == 1 && (expectedFieldNames.size() > 0 || expectedMethodNames.size() > 0)) {
      ExpectedTypeInfo[] infos = typesList.get(0);
      if (infos.length == 1 && infos[0].getKind() == ExpectedTypeInfo.TYPE_OR_SUBTYPE &&
          infos[0].getType().equals(PsiType.getJavaLangObject(manager, resolveScope))) {
        typesList.clear();
      }
    }

    if (typesList.size() == 0) {
      PsiElementFactory factory = manager.getElementFactory();
      for (Iterator<String> iterator = expectedFieldNames.iterator(); iterator.hasNext();) {
        String fieldName = iterator.next();
        PsiField[] fields = manager.getShortNamesCache().getFieldsByName(fieldName, resolveScope);
        addMemberInfo(fields, expression, typesList, factory);
      }

      for (Iterator<String> iterator = expectedMethodNames.iterator(); iterator.hasNext();) {
        String methodName = iterator.next();
        PsiMethod[] methods = manager.getShortNamesCache().getMethodsByName(methodName, resolveScope);
        addMemberInfo(methods, expression, typesList, factory);
      }
    }

    ExpectedTypeInfo[] expectedTypes = ExpectedTypeUtil.intersect(typesList);
    if (expectedTypes.length == 0 && !typesList.isEmpty()) {
      List<ExpectedTypeInfo> union = new ArrayList<ExpectedTypeInfo>();
      for (Iterator<ExpectedTypeInfo[]> i = typesList.iterator(); i.hasNext();) {
        union.addAll(Arrays.asList((ExpectedTypeInfo[]) i.next()));
      }
      expectedTypes = union.toArray(new ExpectedTypeInfo[union.size()]);
    }

    if (expectedTypes == null || expectedTypes.length == 0) {
      return allowVoidType ? new PsiType[]{PsiType.VOID} : new PsiType[]{PsiType.getJavaLangObject(manager, resolveScope)};
    }
    else {
      //Double check to avoid expensive operations on PsiClassTypes
      final Set<PsiType> typesSet = new HashSet<PsiType>();

      final PsiTypeVisitor<PsiType> visitor = new PsiTypeVisitor<PsiType>() {
        public PsiType visitType(PsiType type) {
          if (PsiType.NULL.equals(type)) {
            type = PsiType.getJavaLangObject(manager, resolveScope);
          }
          else if (PsiType.VOID.equals(type) && !allowVoidType) {
            type = PsiType.getJavaLangObject(manager, resolveScope);
          }

          if (!typesSet.contains(type)) {
            if (type instanceof PsiClassType && (expectedFieldNames.size() > 0 || expectedMethodNames.size() > 0)) {
              PsiClass aClass = ((PsiClassType) type).resolve();
              if (aClass != null) {
                for (Iterator<String> i = expectedFieldNames.iterator(); i.hasNext();) {
                  String fieldName = i.next();
                  if (aClass.findFieldByName(fieldName, true) == null) return null;
                }

                for (Iterator<String> i = expectedMethodNames.iterator(); i.hasNext();) {
                  String methodName = i.next();
                  PsiMethod[] methods = aClass.findMethodsByName(methodName, true);
                  if (methods == null || methods.length == 0) return null;
                }
              }
            }

            typesSet.add(type);
            return type;
          }

          return null;
        }

        public PsiType visitCapturedWildcardType(PsiCapturedWildcardType capturedWildcardType) {
          return capturedWildcardType.getUpperBound().accept(this);
        }
      };

      ExpectedTypesProvider provider = ExpectedTypesProvider.getInstance(manager.getProject());
      PsiType[] types = provider.processExpectedTypes(expectedTypes, visitor, manager.getProject());
      if (types.length == 0) {
        return allowVoidType
               ? new PsiType[]{PsiType.VOID}
               : new PsiType[]{PsiType.getJavaLangObject(manager, resolveScope)};
      }

      return types;
    }
  }

  private static void addMemberInfo(PsiMember[] members,
                                    PsiElement place,
                                    List<ExpectedTypeInfo[]> types,
                                    PsiElementFactory factory) {
    if (members.length > MAX_GUESSED_MEMBERS_COUNT) return;

    List<ExpectedTypeInfo> l = new ArrayList<ExpectedTypeInfo>();
    PsiManager manager = place.getManager();
    ExpectedTypesProvider provider = ExpectedTypesProvider.getInstance(manager.getProject());
    for (int i = 0; i < members.length; i++) {
      PsiMember member = members[i];
      PsiClass aClass = member.getContainingClass();
      if (manager.getResolveHelper().isAccessible(aClass, place, null)) {
        if (!(aClass instanceof PsiAnonymousClass)) {
          PsiClassType type = factory.createType(aClass);
          l.add(provider.createInfo(type, ExpectedTypeInfo.TYPE_OR_SUBTYPE, type, TailType.NONE));
        }
      }
    }

    if (l.size() > 0) {
      types.add(l.toArray(new ExpectedTypeInfo[l.size()]));
    }
  }

  private static class ParameterNameExpression implements Expression {
    private final String[] myNames;

    public ParameterNameExpression(String[] names) {
      myNames = names;
    }

    public Result calculateResult(ExpressionContext context) {
      LookupItem[] lookupItems = calculateLookupItems(context);
      if (lookupItems.length == 0) return new TextResult("");

      return new TextResult(lookupItems[0].getLookupString());
    }

    public Result calculateQuickResult(ExpressionContext context) {
      return null;
    }

    public LookupItem[] calculateLookupItems(ExpressionContext context) {
      final Project project = context.getProject();
      final int offset = context.getStartOffset();
      PsiDocumentManager.getInstance(project).commitAllDocuments();
      PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(context.getEditor().getDocument());
      final PsiElement elementAt = file.findElementAt(offset);
      PsiParameterList parameterList = PsiTreeUtil.getParentOfType(elementAt, PsiParameterList.class);
      if (parameterList == null) return new LookupItem[0];

      PsiParameter parameter = PsiTreeUtil.getParentOfType(elementAt, PsiParameter.class);

      Set<String> parameterNames = new HashSet<String>();
      final PsiParameter[] parameters = parameterList.getParameters();
      for (int i = 0; i < parameters.length; i++) {
        PsiParameter psiParameter = parameters[i];
        if (psiParameter == parameter) continue;
        parameterNames.add(psiParameter.getName());
      }

      HashSet<String> names = new HashSet<String>();
      LinkedHashSet<LookupItem[]> set = new LinkedHashSet<LookupItem[]>();

      for (int i = 0; i < myNames.length; i++) {
        String name = myNames[i];

        if (parameterNames.contains(name)) {
          int j = 1;
          while (parameterNames.contains(name + j)) j++;
          name = name + j;
        }

        names.add(name);
        LookupItemUtil.addLookupItem(set, name, "");
      }

      final String[] suggestedNames = ExpressionUtil.getNames(context);
      if (suggestedNames != null) {
        for (int i = 0; i < suggestedNames.length; i++) {
          String name = suggestedNames[i];

          if (parameterNames.contains(name)) {
            int j = 1;
            while (parameterNames.contains(name + j)) j++;
            name = name + j;
          }

          if (!names.contains(name)) {
            LookupItemUtil.addLookupItem(set, name, "");
          }
        }
      }

      return set.toArray(new LookupItem[set.size()]);
    }
  }
}
