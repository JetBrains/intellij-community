/*
 * @author max
 */
package com.intellij.psi.impl.smartPointers;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiSubstitutorImpl;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.impl.source.PsiImmediateClassType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class SmartTypePointerManagerImpl extends SmartTypePointerManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.smartPointers.SmartTypePointerManagerImpl");
  private final SmartPointerManager myPsiPointerManager;
  private final Project myProject;

  public SmartTypePointerManagerImpl(SmartPointerManager psiPointerManager, final Project project) {
    myPsiPointerManager = psiPointerManager;
    myProject = project;
  }

  @NotNull
  public SmartTypePointer createSmartTypePointer(PsiType type) {
    return type.accept(new SmartTypeCreatingVisitor());
  }

  private static class SimpleTypePointer implements SmartTypePointer {
    private final PsiType myType;

    private SimpleTypePointer(PsiType type) {
      myType = type;
    }

    public PsiType getType() {
      return myType;
    }
  }

  private static class ArrayTypePointer implements SmartTypePointer {
    private PsiType myType;
    private final SmartTypePointer myComponentTypePointer;

    public ArrayTypePointer(PsiType type, SmartTypePointer componentTypePointer) {
      myType = type;
      myComponentTypePointer = componentTypePointer;
    }

    public PsiType getType() {
      if (myType.isValid()) return myType;
      final PsiType type = myComponentTypePointer.getType();
      if (type == null) {
        myType = null;
      }
      else {
        myType = new PsiArrayType(type);
      }
      return myType;
    }
  }

  private static class WildcardTypePointer implements SmartTypePointer {
    private PsiWildcardType myType;
    private PsiManager myManager;
    private final SmartTypePointer myBoundPointer;
    private boolean myIsExtending;

    public WildcardTypePointer(PsiWildcardType type, SmartTypePointer boundPointer) {
      myType = type;
      myManager = myType.getManager();
      myBoundPointer = boundPointer;
      myIsExtending = myType.isExtends();
    }

    public PsiType getType() {
      if (myType.isValid()) return myType;
      if (myBoundPointer == null) {
        return PsiWildcardType.createUnbounded(myManager);
      }
      else {
        if (myIsExtending) {
          return PsiWildcardType.createExtends(myManager, myBoundPointer.getType());
        }
        else {
          return PsiWildcardType.createSuper(myManager, myBoundPointer.getType());
        }
      }
    }
  }


  private static class ClassTypePointer implements SmartTypePointer {
    private PsiType myType;
    private final SmartPsiElementPointer myClass;
    private final Map<SmartPsiElementPointer, SmartTypePointer> myMap;


    public ClassTypePointer(PsiType type,
                            SmartPsiElementPointer aClass,
                            Map<SmartPsiElementPointer, SmartTypePointer> map) {
      myType = type;
      myClass = aClass;
      myMap = map;
    }

    @Nullable
    public PsiType getType() {
      if (myType.isValid()) return myType;
      final PsiElement classElement = myClass.getElement();
      if (!(classElement instanceof PsiClass)) return null;
      Map<PsiTypeParameter, PsiType> resurrected = new HashMap<PsiTypeParameter, PsiType>();
      final Set<Map.Entry<SmartPsiElementPointer, SmartTypePointer>> set = myMap.entrySet();
      for (Map.Entry<SmartPsiElementPointer, SmartTypePointer> entry : set) {
        PsiElement element = entry.getKey().getElement();
        if (element instanceof PsiTypeParameter) {
          resurrected.put(((PsiTypeParameter)element), entry.getValue().getType());
        }
      }
      Iterator<PsiTypeParameter> iterator = PsiUtil.typeParametersIterator((PsiClass) classElement);
      while (iterator.hasNext()) {
        PsiTypeParameter typeParameter = iterator.next();
        if (!resurrected.containsKey(typeParameter)) {
          resurrected.put(typeParameter, null);
        }
      }
      final PsiSubstitutor resurrectedSubstitutor = PsiSubstitutorImpl.createSubstitutor(resurrected);
      myType = new PsiImmediateClassType(((PsiClass)classElement), resurrectedSubstitutor);
      return myType;
    }
  }

  private class ClassReferenceTypePointer implements SmartTypePointer {
    private PsiType myType;
    private final SmartPsiElementPointer mySmartPsiElementPointer;
    private final String myReferenceText;

    ClassReferenceTypePointer(PsiClassReferenceType type) {
      myType = type;
      final PsiJavaCodeReferenceElement reference = type.getReference();
      mySmartPsiElementPointer = myPsiPointerManager.createSmartPsiElementPointer(reference);
      myReferenceText = reference.getText();
    }

    public PsiType getType() {
      if (myType.isValid()) return myType;
      final PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)mySmartPsiElementPointer.getElement();
      final PsiElementFactory factory = JavaPsiFacade.getInstance(myProject).getElementFactory();
      if (referenceElement != null) {
        myType = factory.createType(referenceElement);
      }
      else {
        try {
          myType = factory.createTypeFromText(myReferenceText, null);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
      return myType;
    }
  }

  private class SmartTypeCreatingVisitor extends PsiTypeVisitor<SmartTypePointer> {
    public SmartTypePointer visitPrimitiveType(PsiPrimitiveType primitiveType) {
      return new SimpleTypePointer(primitiveType);
    }

    public SmartTypePointer visitArrayType(PsiArrayType arrayType) {
      return new ArrayTypePointer(arrayType, arrayType.getComponentType().accept(this));
    }

    public SmartTypePointer visitWildcardType(PsiWildcardType wildcardType) {
      final PsiType bound = wildcardType.getBound();
      final SmartTypePointer boundPointer;
      if (bound == null) {
        boundPointer = null;
      }
      else {
        boundPointer = bound.accept(this);
      }
      return new WildcardTypePointer(wildcardType, boundPointer);
    }

    public SmartTypePointer visitClassType(PsiClassType classType) {
      final PsiClassType.ClassResolveResult resolveResult = classType.resolveGenerics();
      final PsiClass aClass = resolveResult.getElement();
      if (aClass == null) {
        LOG.assertTrue(classType instanceof PsiClassReferenceType);
        return new ClassReferenceTypePointer((PsiClassReferenceType)classType);
      }
      if (classType instanceof PsiClassReferenceType) {
        classType = ((PsiClassReferenceType)classType).createImmediateCopy();
      }
      final PsiSubstitutor substitutor = resolveResult.getSubstitutor();
      final HashMap<SmartPsiElementPointer, SmartTypePointer> map = new HashMap<SmartPsiElementPointer, SmartTypePointer>();
      final Iterator<PsiTypeParameter> iterator = PsiUtil.typeParametersIterator(aClass);
      while (iterator.hasNext()) {
        PsiTypeParameter typeParameter = iterator.next();
        final PsiType substitutionResult = substitutor.substitute(typeParameter);
        if (substitutionResult != null) {
          final SmartPsiElementPointer pointer = myPsiPointerManager.createSmartPsiElementPointer(typeParameter);
          map.put(pointer, substitutionResult.accept(this));
        }
      }
      return new ClassTypePointer(classType, myPsiPointerManager.createSmartPsiElementPointer(aClass), map);
    }
  }

}