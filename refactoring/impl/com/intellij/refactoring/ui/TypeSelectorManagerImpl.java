package com.intellij.refactoring.ui;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypeUtil;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.TailType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.RefactoringHierarchyUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @author dsl
 */
public class TypeSelectorManagerImpl implements TypeSelectorManager {
  private final PsiType myDefaultType;
  private final PsiExpression myMainOccurence;
  private final PsiExpression[] myOccurrences;
  private final PsiType[] myTypesForMain;
  private final PsiType[] myTypesForAll;
  private final boolean myIsOneSuggestion;
  private TypeSelector myTypeSelector;
  private final PsiElementFactory myFactory;
  private ExpectedTypesProvider.ExpectedClassProvider myOccurrenceClassProvider;
  private ExpectedTypesProvider myExpectedTypesProvider;

  public TypeSelectorManagerImpl(Project project, PsiType type, PsiExpression mainOccurence, PsiExpression[] occurrences) {
    myFactory = JavaPsiFacade.getInstance(project).getElementFactory();
    myDefaultType = type;
    myMainOccurence = mainOccurence;
    myOccurrences = occurrences;
    myExpectedTypesProvider = ExpectedTypesProvider.getInstance(project);

    myOccurrenceClassProvider = createOccurrenceClassProvider();
    myTypesForMain = getTypesForMain();
    myTypesForAll = getTypesForAll(true);

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

  public TypeSelectorManagerImpl(Project project, PsiType type, PsiExpression[] occurrences) {
    this(project, type, occurrences, true);
  }

  public TypeSelectorManagerImpl(Project project, PsiType type, PsiExpression[] occurrences, boolean areTypesDirected) {
    myFactory = JavaPsiFacade.getInstance(project).getElementFactory();
    myDefaultType = type;
    myMainOccurence = null;
    myOccurrences = occurrences;
    myExpectedTypesProvider = ExpectedTypesProvider.getInstance(project);
    myOccurrenceClassProvider = createOccurrenceClassProvider();
    myTypesForAll = getTypesForAll(areTypesDirected);
    myTypesForMain = PsiType.EMPTY_ARRAY;
    myIsOneSuggestion = myTypesForAll.length == 1;

    if (myIsOneSuggestion) {
      myTypeSelector = new TypeSelector(myTypesForAll[0]);
    }
    else {
      myTypeSelector = new TypeSelector();
      myTypeSelector.setTypes(myTypesForAll);
    }
  }

  private ExpectedTypesProvider.ExpectedClassProvider createOccurrenceClassProvider() {
    final Set<PsiClass> occurrenceClasses = new HashSet<PsiClass>();
    for (final PsiExpression occurence : myOccurrences) {
      final PsiType occurrenceType = occurence.getType();
      final PsiClass aClass = PsiUtil.resolveClassInType(occurrenceType);
      if (aClass != null) {
        occurrenceClasses.add(aClass);
      }
    }
    return new ExpectedTypeUtil.ExpectedClassesFromSetProvider(occurrenceClasses);
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
        if (expectedTypes != null && expectedTypes.length > 0) {
          final ExpectedTypeInfo
              typeInfo = myExpectedTypesProvider.createInfo(type, ExpectedTypeInfo.TYPE_STRICTLY, type, TailType.NONE);
          for (ExpectedTypeInfo expectedType : expectedTypes) {
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

  private PsiType[] getTypesForAll(final boolean areTypesDirected) {
    final ArrayList<ExpectedTypeInfo[]> expectedTypesFromAll = new ArrayList<ExpectedTypeInfo[]>();
    for (PsiExpression occurrence : myOccurrences) {

      final ExpectedTypeInfo[] expectedTypes = myExpectedTypesProvider.getExpectedTypes(occurrence, false, myOccurrenceClassProvider);
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
        NextInfo:
        for (ExpectedTypeInfo[] expectedTypes : expectedTypesFromAll) {
          for (final ExpectedTypeInfo info : expectedTypes) {
            if (ExpectedTypeUtil.matches(type, info)) continue NextInfo;
          }
          return;
        }
        allowedTypes.add(type);
      }
    });

    final ArrayList<PsiType> result = normalizeTypeList(allowedTypes);
    if (!areTypesDirected) {
      Collections.reverse(result);
    }
    return result.toArray(new PsiType[result.size()]);
  }

  private ArrayList<PsiType> normalizeTypeList(final ArrayList<PsiType> typeList) {
    ArrayList<PsiType> result = new ArrayList<PsiType>();
    TypeListCreatingVisitor visitor = new TypeListCreatingVisitor(result, myFactory);
    for (PsiType psiType : typeList) {
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

  public boolean isSuggestedType(final String fqName) {
    for(PsiType type: myTypesForAll) {
      if (type.getCanonicalText().equals(fqName)) {
        return true;
      }
    }

    for(PsiType type: myTypesForMain) {
      if (type.getCanonicalText().equals(fqName)) {
        return true;
      }
    }

    return false;
  }

  public TypeSelector getTypeSelector() {
    return myTypeSelector;
  }

}
