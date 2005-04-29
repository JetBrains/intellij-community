package com.intellij.refactoring.ui;

import com.intellij.codeInsight.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.RefactoringHierarchyUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * @author dsl
 */
public class TypeSelectorManagerImpl implements TypeSelectorManager {
  private final PsiType myDefaultType;
  private final PsiExpression myMainOccurence;
  private final PsiExpression[] myOccurences;
  private final PsiType[] myTypesForMain;
  private final PsiType[] myTypesForAll;
  private final boolean myIsOneSuggestion;
  private TypeSelector myTypeSelector;
  private final PsiElementFactory myFactory;
  private ExpectedTypesProviderImpl.ExpectedClassProvider myOccurrenceClassProvider;
  private ExpectedTypesProvider myExpectedTypesProvider;

  public TypeSelectorManagerImpl(Project project, PsiType type, PsiExpression mainOccurence, PsiExpression[] occurences) {
    myFactory = PsiManager.getInstance(project).getElementFactory();
    myDefaultType = type;
    myMainOccurence = mainOccurence;
    myOccurences = occurences;
    myExpectedTypesProvider = ExpectedTypesProvider.getInstance(project);

    myOccurrenceClassProvider = createOccurrenceClassProvider();
    myTypesForMain = getTypesForMain();
    myTypesForAll = getTypesForAll();

    myIsOneSuggestion =
        myTypesForMain.length == 1 && myTypesForAll.length == 1 &&
        myTypesForAll[0].equals(myTypesForMain[0]);
    if (myIsOneSuggestion) {
      myTypeSelector = new TypeSelector(myTypesForAll[0]);
    }
    else {
      myTypeSelector = new TypeSelector();
    }
  }

  public TypeSelectorManagerImpl(Project project, PsiType type, PsiExpression[] occurences) {
    myFactory = PsiManager.getInstance(project).getElementFactory();
    myDefaultType = type;
    myMainOccurence = null;
    myOccurences = occurences;
    myExpectedTypesProvider = ExpectedTypesProvider.getInstance(project);
    myOccurrenceClassProvider = createOccurrenceClassProvider();
    myTypesForAll = getTypesForAll();
    myTypesForMain = null;
    myIsOneSuggestion = true;
    myTypeSelector = new TypeSelector();
    myTypeSelector.setTypes(myTypesForAll);
  }

  private ExpectedTypesProvider.ExpectedClassProvider createOccurrenceClassProvider() {
    final Set<PsiClass> occurrenceClasses = new HashSet<PsiClass>();
    for (final PsiExpression occurence : myOccurences) {
      final PsiType occurrenceType = occurence.getType();
      final PsiClass aClass = PsiUtil.resolveClassInType(occurrenceType);
      if (aClass != null) {
        occurrenceClasses.add(aClass);
      }
    }
    final ExpectedTypesProvider.ExpectedClassProvider expectedClassProvider = new ExpectedTypeUtil.ExpectedClassesFromSetProvider(occurrenceClasses);
    return expectedClassProvider;
  }

  private PsiType[] getTypesForMain() {
    final ExpectedTypeInfo[] expectedTypes = myExpectedTypesProvider.getExpectedTypes(myMainOccurence, false, myOccurrenceClassProvider);
    final ArrayList<PsiType> allowedTypes = new ArrayList<PsiType>();
    RefactoringHierarchyUtil.processSuperTypes(myDefaultType, new RefactoringHierarchyUtil.SuperTypeVisitor() {
      public void visitType(PsiType aType) {
        checkIfAllowed(aType);
      }

      public void visitClass(PsiClass aClass) {
        checkIfAllowed(myFactory.createType(aClass));
      }

      private void checkIfAllowed(PsiType type) {
        if (expectedTypes.length > 0) {
          final ExpectedTypeInfo
              typeInfo = myExpectedTypesProvider.createInfo(type, ExpectedTypeInfo.TYPE_STRICTLY, type, TailType.NONE);
          for (int i = 0; i < expectedTypes.length; i++) {
            ExpectedTypeInfo expectedType = expectedTypes[i];
            if (expectedType.intersect(typeInfo).length != 0) {
              allowedTypes.add(type);
              break;
            }
          }
        }
        else {
          allowedTypes.add(type);
        }
      }
    });

    ArrayList<PsiType> result = normalizeTypeList(allowedTypes);
    return result.toArray(new PsiType[result.size()]);
  }

  private PsiType[] getTypesForAll() {
    final ArrayList<ExpectedTypeInfo[]> expectedTypesFromAll = new ArrayList<ExpectedTypeInfo[]>();
    for (PsiExpression occurence : myOccurences) {

      final ExpectedTypeInfo[] expectedTypes = myExpectedTypesProvider.getExpectedTypes(occurence, false, myOccurrenceClassProvider);
      if (expectedTypes.length > 0) {
        expectedTypesFromAll.add(expectedTypes);
      }
    }

    final ArrayList<PsiType> allowedTypes = new ArrayList<PsiType>();
    RefactoringHierarchyUtil.processSuperTypes(myDefaultType, new RefactoringHierarchyUtil.SuperTypeVisitor() {
      public void visitType(PsiType aType) {
        checkIfAllowed(aType);
      }

      public void visitClass(PsiClass aClass) {
        checkIfAllowed(myFactory.createType(aClass));
      }

      private void checkIfAllowed(PsiType type) {
        final ExpectedTypeInfo typeInfo = myExpectedTypesProvider.createInfo(type, ExpectedTypeInfo.TYPE_STRICTLY, type, TailType.NONE);
        for (ExpectedTypeInfo[] expectedTypes : expectedTypesFromAll) {
          boolean validFound = false;
          for (ExpectedTypeInfo expectedType : expectedTypes) {
            if (expectedType.intersect(typeInfo).length != 0) {
              validFound = true;
              break;
            }
          }
          if (!validFound) return;
        }
        allowedTypes.add(type);
      }
    });

    final ArrayList<PsiType> result = normalizeTypeList(allowedTypes);
    return result.toArray(new PsiType[result.size()]);
//    expectedTypesFromAll.add(new ExpectedTypeInfoImpl[]{myDefaultExpectedTypeInfo});
//    final ExpectedTypeInfoImpl[] expectedTypeInfos = ExpectedTypeUtil.intersect(expectedTypesFromAll);
//    return getSuggestions(expectedTypeInfos);
  }

  private ArrayList<PsiType> normalizeTypeList(final ArrayList<PsiType> typeList) {
    ArrayList<PsiType> result = new ArrayList<PsiType>();
    TypeListCreatingVisitor visitor = new TypeListCreatingVisitor(result, myFactory);
    for (int i = 0; i < typeList.size(); i++) {
      PsiType psiType = typeList.get(i);
      visitor.visitType(psiType);
    }

    for (int index = 0; index < result.size(); index++) {
      PsiType psiType = result.get(index);
      if (psiType.equals(myDefaultType)) {
        result.remove(index);
        break;
      }
    }

    final PsiPrimitiveType unboxedType = PsiPrimitiveType.getUnboxedType(myDefaultType);
    if (unboxedType != null) {
      result.remove(unboxedType);
      result.add(0, unboxedType);
    }
    result.add(0, myDefaultType);
    return result;
  }

  public void setAllOccurences(boolean allOccurences) {
    if (myIsOneSuggestion) return;
    if (allOccurences) {
      myTypeSelector.setTypes(myTypesForAll);
    }
    else {
      myTypeSelector.setTypes(myTypesForMain);
    }
  }

  public TypeSelector getTypeSelector() {
    return myTypeSelector;
  }

}
