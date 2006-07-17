package com.intellij.refactoring.turnRefsToSuper;

import com.intellij.internal.diGraph.analyzer.GlobalAnalyzer;
import com.intellij.internal.diGraph.analyzer.Mark;
import com.intellij.internal.diGraph.analyzer.MarkedNode;
import com.intellij.internal.diGraph.analyzer.OneEndFunctor;
import com.intellij.internal.diGraph.impl.EdgeImpl;
import com.intellij.internal.diGraph.impl.NodeImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.*;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.rename.AutomaticRenamingDialog;
import com.intellij.refactoring.rename.naming.AutomaticVariableRenamer;
import com.intellij.refactoring.util.MoveRenameUsageInfo;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.Queue;
import org.jetbrains.annotations.NonNls;

import java.util.*;

/**
 * @author dsl
 */
public abstract class TurnRefsToSuperProcessorBase extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.turnRefsToSuper.TurnRefsToSuperProcessorBase");
  protected PsiClass myClass;
  protected final boolean myReplaceInstanceOf;
  protected PsiManager myManager;
  protected PsiSearchHelper mySearchHelper;
  protected HashSet<PsiElement> myMarkedNodes = new HashSet<PsiElement>();
  private Queue<PsiExpression> myExpressionsQueue;
  protected HashMap<PsiElement, Node> myElementToNode = new HashMap<PsiElement, Node>();
  protected Map<SmartPsiElementPointer, String> myVariablesRenames = new HashMap<SmartPsiElementPointer, String>();
  private final String mySuperClassName;
  private List<UsageInfo> myVariablesUsages = new ArrayList<UsageInfo>();

  protected boolean preprocessUsages(Ref<UsageInfo[]> refUsages) {
    UsageInfo[] usages = refUsages.get();
    List<UsageInfo> filtered = new ArrayList<UsageInfo>();
    for (UsageInfo usage : usages) {
      if (usage instanceof TurnToSuperReferenceUsageInfo) {
        filtered.add(usage);
      }
    }

    myVariableRenamer = new AutomaticVariableRenamer(myClass, mySuperClassName, filtered);
    if (!ApplicationManager.getApplication().isUnitTestMode() &&
        myVariableRenamer.hasAnythingToRename()) {
      final AutomaticRenamingDialog dialog = new AutomaticRenamingDialog(myProject, myVariableRenamer);
      dialog.show();
      if (!dialog.isOK()) return false;

      final List<PsiNamedElement> variables = myVariableRenamer.getElements();
      for (final PsiNamedElement namedElement : variables) {
        final PsiVariable variable = (PsiVariable)namedElement;
        final SmartPsiElementPointer pointer = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(variable);
        myVariablesRenames.put(pointer, myVariableRenamer.getNewName(variable));
      }

      Runnable runnable = new Runnable() {
        public void run() {
          myVariableRenamer.findUsages(myVariablesUsages, false, false);
        }
      };

      if (!ProgressManager.getInstance()
        .runProcessWithProgressSynchronously(runnable, RefactoringBundle.message("searching.for.variables"), true, myProject)) {
        return false;
      }
    }

    prepareSuccessful();
    return true;
  }

  private AutomaticVariableRenamer myVariableRenamer;

  protected void performVariablesRenaming() {
    try {
      //forget about smart pointers
      Map<PsiElement, String> variableRenames = new HashMap<PsiElement, String>();
      for (Map.Entry<SmartPsiElementPointer, String> entry : myVariablesRenames.entrySet()) {
        variableRenames.put(entry.getKey().getElement(), entry.getValue());
      }

      for (UsageInfo usage : myVariablesUsages) {
        if (usage instanceof MoveRenameUsageInfo) {
          final MoveRenameUsageInfo renameUsageInfo = ((MoveRenameUsageInfo)usage);
          final String newName = variableRenames.get(renameUsageInfo.getUpToDateReferencedElement());
          final PsiReference reference = renameUsageInfo.getReference();
          if (reference != null) {
            reference.handleElementRename(newName);
          }
        }
      }

      for (Map.Entry<SmartPsiElementPointer,String> entry : myVariablesRenames.entrySet()) {
        final String newName = entry.getValue();
        if (newName != null) {
          final PsiVariable variable = (PsiVariable)entry.getKey().getElement();
          variable.setName(newName);
        }
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  public TurnRefsToSuperProcessorBase(Project project, boolean replaceInstanceOf, String superClassName) {
    super(project);
    mySuperClassName = superClassName;
    myManager = PsiManager.getInstance(project);
    mySearchHelper = myManager.getSearchHelper();
    myManager = PsiManager.getInstance(myProject);
    myReplaceInstanceOf = replaceInstanceOf;
  }

  protected ArrayList<UsageInfo> detectTurnToSuperRefs(PsiReference[] refs, final ArrayList<UsageInfo> result) {
    buildGraph(refs);

    for (PsiReference ref : refs) {
      final PsiElement element = ref.getElement();
      if (canTurnToSuper(element)) {
        result.add(new TurnToSuperReferenceUsageInfo(element));
      }
    }
    return result;
  }

  protected boolean canTurnToSuper(PsiElement ref) {
    return !myMarkedNodes.contains(ref);
  }

  protected static void processTurnToSuperRefs(UsageInfo[] usages, final PsiClass aSuper) throws IncorrectOperationException {
    for (UsageInfo usage : usages) {
      if (usage instanceof TurnToSuperReferenceUsageInfo) {
        final PsiElement element = usage.getElement();
        if (element != null) {
          final PsiReference ref = element.getReference();
          assert ref != null;
          PsiElement newElement = ref.bindToElement(aSuper);

          if (newElement.getParent() instanceof PsiTypeElement) {
            if (newElement.getParent().getParent() instanceof PsiTypeCastExpression) {
              fixPossiblyRedundantCast((PsiTypeCastExpression)newElement.getParent().getParent());
            }
          }
        }
      }
    }
  }

  private static void fixPossiblyRedundantCast(PsiTypeCastExpression cast) throws IncorrectOperationException {
    PsiTypeElement castTypeElement = cast.getCastType();
    if (castTypeElement == null) return;
    PsiClass castClass = PsiUtil.resolveClassInType(castTypeElement.getType());
    if (castClass == null) return;

    PsiExpression operand = cast.getOperand();
    if (operand == null) return;
    PsiClass operandClass = PsiUtil.resolveClassInType(RefactoringUtil.getTypeByExpression(operand));
    if (operandClass == null) return;

    if (!castClass.getManager().areElementsEquivalent(castClass, operandClass) &&
        !operandClass.isInheritor(castClass, true)) {
      return;
    }
    // OK, cast is redundant
    PsiExpression exprToReplace = cast;
    while (exprToReplace.getParent() instanceof PsiParenthesizedExpression) {
      exprToReplace = (PsiExpression)exprToReplace.getParent();
    }
    exprToReplace.replace(operand);
  }

  private void buildGraph(PsiReference[] refs) {
    myMarkedNodes.clear();
    myExpressionsQueue = new Queue<PsiExpression>(refs.length);
    myElementToNode.clear();
    for (PsiReference ref : refs) {
      processUsage(ref.getElement());
    }

    processQueue();

    markNodes();

    spreadMarks();
  }

  private void processUsage(PsiElement ref) {
    if (ref instanceof PsiReferenceExpression) {
      final PsiElement parent = ref.getParent();
      if (parent instanceof PsiReferenceExpression) {
        final PsiReferenceExpression refExpr = (PsiReferenceExpression)parent;
        final PsiElement refMember = refExpr.resolve();
        if (!isInSuper(refMember)) {
          markNode(ref);
        }
      }
      return;
    }

    PsiElement parent = ref.getParent();
    if (parent instanceof PsiTypeElement) {
      PsiElement pparent = parent.getParent();
      while (pparent instanceof PsiTypeElement) {
        addLink(pparent, parent);
        addLink(parent, pparent);
        parent = pparent;
        pparent = parent.getParent();
      }
      final PsiTypeElement typeElement = (PsiTypeElement)parent;

      addLink(typeElement, ref);
      addLink(ref, typeElement);

      if (pparent instanceof PsiVariable) {
        processVariableType((PsiVariable)pparent);
      }
      else if (pparent instanceof PsiMethod) {
        processMethodReturnType((PsiMethod)pparent);
      }
      else if (pparent instanceof PsiTypeCastExpression) {
        addLink(pparent, typeElement);
        addLink(typeElement, pparent);
      }
      else if (pparent instanceof PsiReferenceParameterList) {
        final PsiReferenceParameterList refParameterList = ((PsiReferenceParameterList)pparent);
        final PsiElement ppparent = pparent.getParent();
        if (ppparent instanceof PsiJavaCodeReferenceElement) {
          final PsiJavaCodeReferenceElement classReference = (PsiJavaCodeReferenceElement)ppparent;
          if (classReference.getParent() instanceof PsiReferenceList) {
            final PsiReferenceList referenceList = ((PsiReferenceList)ppparent.getParent());
            final PsiClass parentClass = PsiTreeUtil.getParentOfType(ref, PsiClass.class);
            if (parentClass != null) {
              if (referenceList.equals(parentClass.getExtendsList()) || referenceList.equals(parentClass.getImplementsList())) {
                final PsiTypeElement[] typeParameterElements = refParameterList.getTypeParameterElements();
                for (int i = 0; i < typeParameterElements.length; i++) {
                  if (typeParameterElements[i] == typeElement) {
                    final PsiElement resolved = classReference.resolve();
                    if (resolved instanceof PsiClass) {
                      final PsiTypeParameter[] typeParameters = ((PsiClass)resolved).getTypeParameters();
                      if (typeParameters.length > i) {
                        linkTypeParameterInstantiations(typeParameters[i], typeElement, parentClass);
                        return;
                      }
                    }
                  }
                }
              }
            }
          }
        }
        markNode(ref); //???
      }
    }
    else if (parent instanceof PsiNewExpression) {
      PsiNewExpression newExpression = (PsiNewExpression)parent;
      if (newExpression.getType() instanceof PsiArrayType) {
        addLink(newExpression, ref);
        addLink(ref, newExpression);
        PsiArrayInitializerExpression initializer = newExpression.getArrayInitializer();
        if (initializer != null) {
          addLink(ref, initializer);
        }
        checkToArray(ref, newExpression);
      }
      else {
        markNode(ref);
      }
    }
    else if (parent instanceof PsiJavaCodeReferenceElement && ref.equals(((PsiJavaCodeReferenceElement)parent).getQualifier())) {
      final PsiElement resolved = ((PsiJavaCodeReferenceElement)parent).resolve();
      if (resolved == null || !isInSuper(resolved)) {
        markNode(ref);
      }
    }
    else {
      markNode(ref);
    }
  }

  private void linkTypeParameterInstantiations (PsiTypeParameter typeParameter, final PsiTypeElement instantiation, final PsiClass inheritingClass) {
    final PsiTypeParameterListOwner owner = typeParameter.getOwner();
    if (owner instanceof PsiClass) {
      final PsiClass ownerClass = ((PsiClass)owner);
      final LocalSearchScope derivedScope = new LocalSearchScope(inheritingClass);
      final PsiSubstitutor substitutor = TypeConversionUtil.getClassSubstitutor(ownerClass, inheritingClass, PsiSubstitutor.EMPTY);
      if (substitutor == null) return;
      final LocalSearchScope baseScope = new LocalSearchScope(ownerClass);
      ReferencesSearch.search(typeParameter, baseScope).forEach(new Processor<PsiReference>() {
        public boolean process(final PsiReference ref) {
          final PsiElement element = ref.getElement();
          final PsiElement parent = element.getParent();
          if (parent instanceof PsiTypeElement) {
            final PsiElement pparent = parent.getParent();
            if (pparent instanceof PsiMethod && parent.equals(((PsiMethod)pparent).getReturnTypeElement())) {
              final PsiMethod method = (PsiMethod)pparent;
              final MethodSignature signature = method.getSignature(substitutor);
              if (PsiUtil.isAccessible(method, inheritingClass, null)) {
                final PsiMethod inInheritor = MethodSignatureUtil.findMethodBySignature(inheritingClass, signature, false);
                if (inInheritor != null && inInheritor.getReturnTypeElement() != null) {
                  addLink(instantiation, method.getReturnTypeElement());
                  addLink(method.getReturnTypeElement(), instantiation);
                }
              }
            } else if (pparent instanceof PsiParameter) {
              final PsiParameter parameter = (PsiParameter)pparent;
              if (parameter.getDeclarationScope() instanceof PsiMethod) {
                PsiMethod method = (PsiMethod)parameter.getDeclarationScope();
                final int index = ((PsiParameterList)parameter.getParent()).getParameterIndex(parameter);
                final MethodSignature signature = method.getSignature(substitutor);
                if (PsiUtil.isAccessible(method, inheritingClass, null)) {
                  final PsiMethod inInheritor = MethodSignatureUtil.findMethodBySignature(inheritingClass, signature, false);
                  if (inInheritor != null) {
                    final PsiParameter[] inheritorParams = inInheritor.getParameterList().getParameters();
                    LOG.assertTrue(inheritorParams.length > index);
                    final PsiTypeElement hisTypeElement = inheritorParams[index].getTypeElement();
                    addLink(instantiation, hisTypeElement);
                    addLink(hisTypeElement, instantiation);
                  }
                }
              }
            }
          }

          return true;
        }
      });
    }
  }

  private void addArgumentParameterLink(PsiElement arg, PsiExpressionList actualArgsList, PsiMethod method) {
    PsiParameter[] params = method.getParameterList().getParameters();
    PsiExpression[] actualArgs = actualArgsList.getExpressions();
    int argIndex = -1;
    for (int i = 0; i < actualArgs.length; i++) {
      PsiExpression actualArg = actualArgs[i];
      if (actualArg.equals(arg)) {
        argIndex = i;
        break;
      }
    }

    if (argIndex >= 0 && argIndex < params.length) {
      addLink(params[argIndex], arg);
    }
    else if (method.isVarArgs() && argIndex >= params.length) {
      addLink(params[params.length - 1], arg);
    }
  }

  private void checkToArray(PsiElement ref, PsiNewExpression newExpression) {
    PsiElement tmp;

    final PsiClass javaUtilCollectionClass = myManager.findClass("java.util.Collection", ref.getResolveScope());
    if (javaUtilCollectionClass == null) return;
    tmp = newExpression.getParent();
    if (!(tmp instanceof PsiExpressionList)) return;
    tmp = tmp.getParent();
    if (!(tmp instanceof PsiMethodCallExpression)) return;
    PsiMethodCallExpression methodCall = (PsiMethodCallExpression)tmp;
    tmp = tmp.getParent();
    if (!(tmp instanceof PsiTypeCastExpression)) return;
    PsiTypeCastExpression typeCast = (PsiTypeCastExpression)tmp;

    PsiReferenceExpression methodRef = methodCall.getMethodExpression();
    tmp = methodRef.resolve();
    if (!(tmp instanceof PsiMethod)) return;
    PsiMethod method = (PsiMethod)tmp;
    @NonNls final String name = method.getName();
    if (!name.equals("toArray")) return;

    PsiClass methodClass = method.getContainingClass();
    if (!methodClass.isInheritor(javaUtilCollectionClass, true)) return;

    // ok, this is an implementation of java.util.Collection.toArray
    addLink(typeCast, ref);

  }

  private void processVariableType(PsiVariable variable) {
    final PsiTypeElement type = variable.getTypeElement();
    final PsiExpression initializer = variable.getInitializer();
    if (initializer != null) {
      addLink(type, initializer);
    }

    for (PsiReference ref : ReferencesSearch.search(variable).findAll()) {
      final PsiElement element = ref.getElement();
      addLink(element, type);
      addLink(type, element);
      analyzeVarUsage(element);
    }

    if (variable instanceof PsiParameter) {
      final PsiElement declScope = ((PsiParameter)variable).getDeclarationScope();
      if (declScope instanceof PsiCatchSection) {
        markNode(type);
      }
      else if (declScope instanceof PsiForeachStatement) {
        final PsiExpression iteratedValue = ((PsiForeachStatement)declScope).getIteratedValue();
        addLink(type, iteratedValue);
        addLink(iteratedValue, type);
      }
      else if (declScope instanceof PsiMethod) {
        final PsiMethod method = (PsiMethod)declScope;
        final int index = method.getParameterList().getParameterIndex((PsiParameter)variable);

        {
          for (PsiReference call : ReferencesSearch.search(method).findAll()) {
            PsiElement ref = call.getElement();
            PsiExpressionList argumentList;
            if (ref.getParent() instanceof PsiCall) {
              argumentList = ((PsiCall)ref.getParent()).getArgumentList();
            }
            else if (ref.getParent() instanceof PsiAnonymousClass) {
              argumentList = ((PsiConstructorCall)ref.getParent().getParent()).getArgumentList();
            }
            else {
              continue;
            }
            if (argumentList == null) continue;
            PsiExpression[] args = argumentList.getExpressions();
            if (index >= args.length) continue;
            addLink(type, args[index]);
          }
        }

        final class Inner {
          void linkInheritors(final PsiMethod[] methods) {
            for (final PsiMethod superMethod : methods) {
              final PsiParameter[] parameters = superMethod.getParameterList().getParameters();
              if (index >= parameters.length) continue;
              final PsiTypeElement superType = parameters[index].getTypeElement();
              addLink(superType, type);
              addLink(type, superType);
            }
          }
        }

        final PsiMethod[] superMethods = method.findSuperMethods();
        new Inner().linkInheritors(superMethods);
        final PsiClass[] subClasses =
          mySearchHelper.findInheritors(method.getContainingClass(), GlobalSearchScope.projectScope(myProject), false);
        // ??? In the theory this is non-efficient way: too many inheritors can be processed.
        // ??? But in real use it seems reasonably fast. If poor performance problems emerged,
        // ??? should be optimized
        for (int i1 = 0; i1 != subClasses.length; ++i1) {
          final PsiMethod[] mBSs = subClasses[i1].findMethodsBySignature(method, true);
          new Inner().linkInheritors(mBSs);
        }
      }
      else {
        LOG.assertTrue(false);
      }
    }
  }

  private void analyzeVarUsage(final PsiElement element) {
    PsiType constrainingType = null;
    final PsiElement parent = element.getParent();
    if (parent instanceof PsiReturnStatement) {
      final PsiMethod method = PsiTreeUtil.getParentOfType(parent, PsiMethod.class);
      assert method != null;
      constrainingType = method.getReturnType();
    } else if (parent instanceof PsiAssignmentExpression) {
      constrainingType = ((PsiAssignmentExpression)parent).getLExpression().getType();
    } else if (parent instanceof PsiLocalVariable) {
      constrainingType = ((PsiLocalVariable)parent).getType();
    }
    //TODO: I expect more cases here

    if (constrainingType instanceof PsiClassType) {
      final PsiClass resolved = ((PsiClassType)constrainingType).resolve();
      if (!myClass.equals(resolved)) {
          if (resolved == null || !isSuperInheritor(resolved)) {
            markNode(element);
          }
        }
    }
  }

  private void processMethodReturnType(final PsiMethod method) {
    final PsiTypeElement returnType = method.getReturnTypeElement();
    for (PsiReference call : ReferencesSearch.search(method).findAll()) {
      final PsiElement ref = call.getElement();
      if (PsiTreeUtil.getParentOfType(ref, PsiDocComment.class) != null) continue;
      final PsiMethodCallExpression methodCall = (PsiMethodCallExpression)ref.getParent();
      addLink(methodCall, returnType);
    }

    final PsiReturnStatement[] returnStatements = RefactoringUtil.findReturnStatements(method);
    for (final PsiReturnStatement returnStatement : returnStatements) {
      final PsiExpression returnValue = returnStatement.getReturnValue();
      if (returnValue != null) {
        addLink(returnType, returnValue);
      }
    }

    final PsiMethod[] superMethods = method.findSuperMethods();
    final class Inner {
      public void linkInheritors(final PsiMethod[] methods) {
        for (final PsiMethod superMethod : methods) {
          final PsiTypeElement superType = superMethod.getReturnTypeElement();
          addLink(superType, returnType);
          addLink(returnType, superType);
        }
      }
    }

    new Inner().linkInheritors(superMethods);
    // ??? In the theory this is non-efficient way: too many inheritors can be processed (and multiple times).
    // ??? But in real use it seems reasonably fast. If poor performance problems emerged,
    // ??? should be optimized
    final PsiClass[] subClasses =
      mySearchHelper.findInheritors(method.getContainingClass(), GlobalSearchScope.projectScope(myProject), false);
    for (int i1 = 0; i1 != subClasses.length; ++i1) {
      final PsiMethod[] mBSs = subClasses[i1].findMethodsBySignature(method, true);
      new Inner().linkInheritors(mBSs);
    }
  }

  private void processQueue() {
    while (!myExpressionsQueue.isEmpty()) {
      PsiExpression expr = myExpressionsQueue.pullFirst();
      PsiElement parent = expr.getParent();
      if (parent instanceof PsiAssignmentExpression) {
        PsiAssignmentExpression assignment = (PsiAssignmentExpression)parent;
        if (assignment.getRExpression() != null) {
          addLink(assignment.getLExpression(), assignment.getRExpression());
        }
        addLink(assignment, assignment.getLExpression());
        addLink(assignment.getLExpression(), assignment);
      }
      else if (parent instanceof PsiArrayAccessExpression) {
        PsiArrayAccessExpression arrayAccess = (PsiArrayAccessExpression)parent;
        if (expr.equals(arrayAccess.getArrayExpression())) {
          addLink(arrayAccess, expr);
          addLink(expr, arrayAccess);
        }
      }
      else if (parent instanceof PsiParenthesizedExpression) {
        addLink(parent, expr);
        addLink(expr, parent);
      }
      else if (parent instanceof PsiArrayInitializerExpression) {
        PsiArrayInitializerExpression arrayInitializerExpr = (PsiArrayInitializerExpression)parent;
        PsiExpression[] initializers = arrayInitializerExpr.getInitializers();
        for (PsiExpression initializer : initializers) {
          addLink(arrayInitializerExpr, initializer);
        }
      }
      else if (parent instanceof PsiExpressionList) {
        PsiElement pparent = parent.getParent();
        if (pparent instanceof PsiCallExpression) {
          PsiMethod method = ((PsiCallExpression)pparent).resolveMethod();
          if (method != null) {
            addArgumentParameterLink(expr, (PsiExpressionList)parent, method);
          }
        }
      }
    }
  }

  protected void markNodes() {
    //for (Iterator iterator = myDependencyMap.keySet().iterator(); iterator.hasNext();) {
    for (final PsiElement element : myElementToNode.keySet()) {
      if (element instanceof PsiExpression) {
        final PsiElement parent = element.getParent();
        if (parent instanceof PsiReferenceExpression) {
          final PsiReferenceExpression refExpr = (PsiReferenceExpression)parent;
          if (element.equals(refExpr.getQualifierExpression())) {
            final PsiElement refElement = refExpr.resolve();
            if (refElement != null && !isInSuper(refElement)) {
              markNode(element);
            }
          }
        }
      }
      else if (!myReplaceInstanceOf && element.getParent() != null
               && element.getParent().getParent() instanceof PsiInstanceOfExpression) {
        markNode(element);
      }
      else if (element.getParent() instanceof PsiClassObjectAccessExpression) {
        markNode(element);
      }
      else if (element instanceof PsiParameter) {
        final PsiType type = ((PsiParameter)element).getType();
        final PsiClass aClass = PsiUtil.resolveClassInType(type);
        if (aClass != null) {
          if (!myManager.isInProject(element) || !myManager.areElementsEquivalent(aClass, myClass)) {
            if (!isSuperInheritor(aClass)) {
              markNode(element);
            }
          }
        }
        else { // unresolvable class
          markNode(element);
        }
      }
    }
  }

  protected abstract boolean isSuperInheritor(PsiClass aClass);

  protected abstract boolean isInSuper(PsiElement member);

  protected void addLink(PsiElement source, PsiElement target) {
    Node from = myElementToNode.get(source);
    Node to = myElementToNode.get(target);

    if (from == null) {
      from = new Node(source);
      if (source instanceof PsiExpression) myExpressionsQueue.addLast((PsiExpression)source);
      myElementToNode.put(source, from);
    }

    if (to == null) {
      to = new Node(target);
      if (target instanceof PsiExpression) myExpressionsQueue.addLast((PsiExpression)target);
      myElementToNode.put(target, to);
    }

    Edge.connect(from, to);
  }

  private void spreadMarks() {
    final LinkedList<MarkedNode> markedNodes = new LinkedList<MarkedNode>();

    for (final PsiElement markedNode : myMarkedNodes) {
      final Node node = myElementToNode.get(markedNode);
      if (node != null) markedNodes.addFirst(node);
    }

    GlobalAnalyzer.doOneEnd(markedNodes, new Colorer());
  }

  private void markNode(final PsiElement node) {
    myMarkedNodes.add(node);
  }

  class Colorer implements OneEndFunctor {
    public Mark compute(Mark from, Mark edge, Mark to) {
      VisitMark mark = new VisitMark((VisitMark)to);

      myMarkedNodes.add(mark.getElement());
      mark.switchOn();

      return mark;
    }
  }

  private static class Edge extends EdgeImpl {
    private Edge(Node from, Node to) {
      super(from, to);
    }

    public static boolean connect(Node from, Node to) {
      if (from.mySuccessors.add(to)) {
        new Edge(from, to);
        return true;
      }

      return false;
    }
  }

  private static class VisitMark implements Mark {
    private boolean myVisited;
    private PsiElement myElement;

    public boolean coincidesWith(Mark x) {
      return ((VisitMark)x).myVisited == myVisited;
    }

    public VisitMark(VisitMark m) {
      myVisited = false;
      myElement = m.myElement;
    }

    public VisitMark(PsiElement e) {
      myVisited = false;
      myElement = e;
    }

    public void switchOn() {
      myVisited = true;
    }

    public void switchOff() {
      myVisited = false;
    }

    public PsiElement getElement() {
      return myElement;
    }
  }

  private static class Node extends NodeImpl {
    private HashSet<Node> mySuccessors = new HashSet<Node>();
    private VisitMark myMark;

    public Node(PsiElement x) {
      super();
      myMark = new VisitMark(x);
    }

    public Mark getMark() {
      return myMark;
    }

    public void setMark(Mark x) {
      myMark = (VisitMark)x;
    }
  }
}
