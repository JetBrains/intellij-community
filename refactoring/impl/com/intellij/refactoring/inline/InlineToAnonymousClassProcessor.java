package com.intellij.refactoring.inline;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.patterns.impl.Pattern;
import static com.intellij.patterns.impl.StandardPatterns.psiElement;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.rename.NonCodeUsageInfoFactory;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author yole
 */
public class InlineToAnonymousClassProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.inline.InlineToAnonymousClassProcessor");

  private PsiClass myClass;
  private final PsiCall myCallToInline;
  private final boolean myInlineThisOnly;
  private final boolean mySearchInComments;
  private final boolean mySearchInNonJavaFiles;

  private Pattern ourCatchClausePattern = psiElement().type(PsiTypeElement.class).withParent(psiElement().type(PsiParameter.class).withParent(psiElement().type(PsiCatchSection.class)));
  private Pattern ourThrowsClausePattern = psiElement().withParent(psiElement().type(PsiReferenceList.class).withFirstChild(psiElement().withText(PsiKeyword.THROWS)));

  protected InlineToAnonymousClassProcessor(Project project,
                                            PsiClass psiClass,
                                            @Nullable final PsiCall callToInline,
                                            boolean inlineThisOnly,
                                            final boolean searchInComments,
                                            final boolean searchInNonJavaFiles) {
    super(project);
    myClass = psiClass;
    myCallToInline = callToInline;
    myInlineThisOnly = inlineThisOnly;
    if (myInlineThisOnly) assert myCallToInline != null;
    mySearchInComments = searchInComments;
    mySearchInNonJavaFiles = searchInNonJavaFiles;
  }

  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages) {
    return new InlineViewDescriptor(myClass);
  }

  @NotNull
  protected UsageInfo[] findUsages() {
    if (myInlineThisOnly) {
      return new UsageInfo[] { new UsageInfo(myCallToInline) };
    }
    final Collection<PsiReference> refCollection = ReferencesSearch.search(myClass).findAll();
    Set<UsageInfo> usages = new HashSet<UsageInfo>();
    for (PsiReference reference : refCollection) {
      usages.add(new UsageInfo(reference.getElement()));
    }

    List<UsageInfo> nonCodeUsages = new ArrayList<UsageInfo>();
    if (mySearchInComments) {
      RefactoringUtil.addUsagesInStringsAndComments(myClass, myClass.getQualifiedName(), nonCodeUsages,
                                                    new NonCodeUsageInfoFactory(myClass, myClass.getQualifiedName()));
    }

    if (mySearchInNonJavaFiles) {
      GlobalSearchScope projectScope = GlobalSearchScope.projectScope(myClass.getProject());
      RefactoringUtil.addTextOccurences(myClass, myClass.getQualifiedName(), projectScope, nonCodeUsages,
                                        new NonCodeUsageInfoFactory(myClass, myClass.getQualifiedName()));
    }
    usages.addAll(nonCodeUsages);

    return usages.toArray(new UsageInfo[usages.size()]);
  }

  protected void refreshElements(PsiElement[] elements) {
    assert elements.length == 1;
    myClass = (PsiClass) elements [0];
  }

  protected boolean isPreviewUsages(UsageInfo[] usages) {
    if (super.isPreviewUsages(usages)) return true;
    for(UsageInfo usage: usages) {
      if (isForcePreview(usage)) {
        WindowManager.getInstance().getStatusBar(myProject).setInfo(RefactoringBundle.message("occurrences.found.in.comments.strings.and.non.java.files"));
        return true;
      }
    }
    return false;
  }

  private static boolean isForcePreview(final UsageInfo usage) {
    if (usage.isNonCodeUsage) return true;
    PsiElement element = usage.getElement();
    if (element != null) {
      PsiFile file = element.getContainingFile();
      if (!(file instanceof PsiJavaFile)) {
        return true;
      }
    }
    return false;
  }

  protected boolean preprocessUsages(final Ref<UsageInfo[]> refUsages) {
    UsageInfo[] usages = refUsages.get();
    String s = getPreprocessUsagesMessage(usages);
    if (s != null) {
      CommonRefactoringUtil.showErrorMessage(RefactoringBundle.message("inline.to.anonymous.refactoring"), s, null, myClass.getProject());
      return false;
    }
    ArrayList<String> conflicts = getConflicts(usages);
    if (!conflicts.isEmpty()) {
      return showConflicts(conflicts);
    }
    return super.preprocessUsages(refUsages);
  }

  public ArrayList<String> getConflicts(final UsageInfo[] usages) {
    ArrayList<String> result = new ArrayList<String>();
    ReferencedElementsCollector collector = new ReferencedElementsCollector() {
      protected void checkAddMember(@NotNull final PsiMember member) {
        if (PsiTreeUtil.isAncestor(myClass, member, false)) {
          return;
        }
        final PsiModifierList modifierList = member.getModifierList();
        if (member.getContainingClass() == myClass.getSuperClass() && modifierList != null &&
            modifierList.hasModifierProperty(PsiModifier.PROTECTED)) {
          // ignore access to protected members of superclass - they'll be accessible anyway
          return;
        }
        super.checkAddMember(member);
      }
    };
    InlineMethodProcessor.addInaccessibleMemberConflicts(myClass, usages, collector, result);
    return result;
  }

  protected void performRefactoring(UsageInfo[] usages) {
    PsiClassType superType = getSuperType();

    List<PsiElement> elementsToDelete = new ArrayList<PsiElement>();
    List<PsiNewExpression> newExpressions = new ArrayList<PsiNewExpression>();
    for(UsageInfo info: usages) {
      final PsiElement element = info.getElement();
      if (element instanceof PsiNewExpression) {
        newExpressions.add((PsiNewExpression)element);
      }
      else if (element.getParent() instanceof PsiNewExpression) {
        newExpressions.add((PsiNewExpression) element.getParent());
      }
      else {
        PsiImportStatement statement = PsiTreeUtil.getParentOfType(element, PsiImportStatement.class);
        if (statement != null && !myInlineThisOnly) {
          elementsToDelete.add(statement);
        }
        else {
          PsiTypeElement typeElement = PsiTreeUtil.getParentOfType(element, PsiTypeElement.class);
          if (typeElement != null) {
            replaceWithSuperType(typeElement, superType);
          }
        }
      }
    }

    Collections.sort(newExpressions, PsiUtil.BY_POSITION);
    for(PsiNewExpression newExpression: newExpressions) {
      replaceNewOrType(newExpression, superType);
    }

    for(PsiElement element: elementsToDelete) {
      try {
        if (element.isValid()) {
          element.delete();
        }
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
    if (!myInlineThisOnly) {
      try {
        myClass.delete();
      }
      catch(IncorrectOperationException e) {
        LOG.error(e);
      }
    }
  }

  private void replaceNewOrType(final PsiNewExpression psiNewExpression, final PsiClassType superType) {
    try {
      if (psiNewExpression.getArrayDimensions().length == 0 && psiNewExpression.getArrayInitializer() == null) {
        new InlineToAnonymousConstructorProcessor(myClass, psiNewExpression, superType).run();
      }
      else {
        PsiJavaCodeReferenceElement element = myClass.getManager().getElementFactory().createClassReferenceElement(superType.resolve());
        psiNewExpression.getClassReference().replace(element);        
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private void replaceWithSuperType(final PsiTypeElement typeElement, final PsiClassType superType) {
    PsiElementFactory factory = myClass.getManager().getElementFactory();
    PsiClassType psiType = (PsiClassType) typeElement.getType();
    PsiClassType.ClassResolveResult classResolveResult = psiType.resolveGenerics();
    PsiType substType = classResolveResult.getSubstitutor().substitute(superType);
    assert classResolveResult.getElement() == myClass;
    try {
      typeElement.replace(factory.createTypeElement(substType));
    }
    catch(IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private PsiClassType getSuperType() {
    PsiElementFactory factory = myClass.getManager().getElementFactory();

    PsiClassType superType;
    PsiClass superClass = myClass.getSuperClass();
    PsiClassType[] interfaceTypes = myClass.getImplementsListTypes();
    if (interfaceTypes.length > 0 && !InlineToAnonymousClassHandler.isRedundantImplements(superClass, interfaceTypes [0])) {
      assert interfaceTypes.length == 1;
      superType = interfaceTypes [0];
    }
    else {
      PsiClassType[] classTypes = myClass.getExtendsListTypes();
      if (classTypes.length > 0) {
        superType = classTypes [0];
      }
      else {
        superType = factory.createType(superClass);
      }
    }
    return superType;
  }

  protected String getCommandName() {
    return RefactoringBundle.message("inline.to.anonymous.command.name", myClass.getQualifiedName());
  }

  @Nullable
  public String getPreprocessUsagesMessage(final UsageInfo[] usages) {
    boolean hasUsages = false;
    for(UsageInfo usage: usages) {
      final PsiElement element = usage.getElement();
      if (element == null) continue;
      if (!PsiTreeUtil.isAncestor(myClass, element, false)) {
        hasUsages = true;
      }
      final PsiElement parentElement = element.getParent();
      if (parentElement != null) {
        if (parentElement.getParent() instanceof PsiClassObjectAccessExpression) {
          return "Class cannot be inlined because it has usages of its class literal";
        }
        if (ourCatchClausePattern.accepts(parentElement)) {
          return "Class cannot be inlined because it is used in a 'catch' clause";
        }
      }
      if (ourThrowsClausePattern.accepts(element)) {
        return "Class cannot be inlined because it is used in a 'throws' clause";
      }
      if (parentElement instanceof PsiThisExpression) {
        return "Class cannot be inlined because it is used as a 'this' qualifier";
      }
      if (parentElement instanceof PsiNewExpression) {
        final PsiNewExpression newExpression = (PsiNewExpression)parentElement;
        final PsiMethod[] constructors = myClass.getConstructors();
        if (constructors.length == 0) {
          PsiExpressionList newArgumentList = newExpression.getArgumentList();
          if (newArgumentList != null && newArgumentList.getExpressions().length > 0) {
            return "Class cannot be inlined because a call to its constructor is unresolved";
          }
        }
        else {
          final JavaResolveResult resolveResult = newExpression.resolveMethodGenerics();
          if (!resolveResult.isValidResult()) {
            return "Class cannot be inlined because a call to its constructor is unresolved";
          }
        }
      }
    }
    if (!hasUsages) {
      return RefactoringBundle.message("class.is.never.used");
    }
    return null;
  }

}
