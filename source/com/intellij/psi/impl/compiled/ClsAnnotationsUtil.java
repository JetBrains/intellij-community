package com.intellij.psi.impl.compiled;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiElementFactoryImpl;
import com.intellij.psi.impl.cache.DeclarationView;
import com.intellij.psi.impl.cache.impl.repositoryCache.RecordUtil;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.parsing.DeclarationParsing;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.cls.BytePointer;
import com.intellij.util.io.RecordDataOutput;

import java.io.IOException;

/**
 * @author ven
 */
public class ClsAnnotationsUtil {
  private static final Logger LOG = Logger.getInstance("com.intellij.psi.impl.compiled.ClsAnnotationsUtil");

  private ClsAnnotationsUtil() {}

  private static PsiAnnotationMemberValue getMemberValue(PsiElement element, ClsElementImpl parent) {
    if (element instanceof PsiLiteralExpression) {
      PsiLiteralExpression expr = (PsiLiteralExpression)element;
      return new ClsLiteralExpressionImpl(parent, element.getText(), expr.getType(), expr.getValue());

    }
    else if (element instanceof PsiClassObjectAccessExpression) {
      PsiClassObjectAccessExpression expr = (PsiClassObjectAccessExpression)element;
      return new ClsClassObjectAccessExpressionImpl(expr.getOperand().getType().getCanonicalText(), parent);

    }
    else if (element instanceof PsiArrayInitializerMemberValue) {
      PsiAnnotationMemberValue[] initializers = ((PsiArrayInitializerMemberValue)element).getInitializers();
      PsiAnnotationMemberValue[] clsInitializers = new PsiAnnotationMemberValue[initializers.length];
      ClsArrayInitializerMemberValueImpl arrayValue = new ClsArrayInitializerMemberValueImpl(parent);
      for (int i = 0; i < initializers.length; i++) {
        PsiAnnotationMemberValue innerValue = getMemberValue(initializers[i], arrayValue);
        clsInitializers[i] = innerValue;
      }
      arrayValue.setInitializers(clsInitializers);
      return arrayValue;

    }
    else if (element instanceof PsiAnnotation) {
      PsiAnnotation psiAnnotation = (PsiAnnotation)element;
      ClsJavaCodeReferenceElementImpl ref = new ClsJavaCodeReferenceElementImpl(null,
                                                                                psiAnnotation.getNameReferenceElement().getCanonicalText());
      ClsAnnotationImpl result = new ClsAnnotationImpl(ref, parent);
      ref.setParent(result);
      ClsAnnotationParameterListImpl list = new ClsAnnotationParameterListImpl(result);
      PsiNameValuePair[] psiAttributes = psiAnnotation.getParameterList().getAttributes();
      ClsNameValuePairImpl[] attributes = new ClsNameValuePairImpl[psiAttributes.length];
      list.setAttributes(attributes);
      for (int i = 0; i < attributes.length; i++) {
        attributes[i] = new ClsNameValuePairImpl(list);
        attributes[i].setNameIdentifier(new ClsIdentifierImpl(attributes[i], psiAttributes[i].getName()));
        attributes[i].setMemberValue(getMemberValue(psiAttributes[i].getValue(), attributes[i]));
      }

      result.setParameterList(list);
      return result;
    }
    else if (element instanceof PsiReferenceExpression) {
      return new ClsReferenceExpressionImpl(parent, (PsiReferenceExpression)element);
    }
    else {
      LOG.error("Unexpected source element for annotation member value: " + element);
      return null;
    }
  }

  public static PsiAnnotationMemberValue createMemberValueFromText(String text, PsiManager manager, ClsElementImpl parent) {
    PsiJavaFile dummyJavaFile = ((PsiElementFactoryImpl)manager.getElementFactory()).getDummyJavaFile(); // kind of hack - we need to resolve classes from java.lang
    final FileElement holderElement = new DummyHolder(manager, dummyJavaFile).getTreeElement();
    TreeElement element = DeclarationParsing.parseMemberValueText(manager, text.toCharArray(), holderElement.getCharTable());
    if (element == null) {
      LOG.error("Could not parse initializer:'" + text + "'");
      return null;
    }
    TreeUtil.addChildren(holderElement, element);
    PsiElement psiElement = SourceTreeToPsiMap.treeElementToPsi(element);
    return getMemberValue(psiElement, parent);
  }

  public interface AttributeReader {
    BytePointer readAttribute(String attributeName);

    ClassFileData getClassFileData();
  }

  public static ClsAnnotationImpl[] getAnnotationsImpl(ClsRepositoryPsiElement element, AttributeReader reader,
                                                       ClsModifierListImpl modifierList) {
    long id = element.getRepositoryId();
    if (id < 0) {
      ClassFileData data = reader.getClassFileData();
      BytePointer pointer1 = reader.readAttribute("RuntimeVisibleAnnotations");
      if (pointer1 != null) {
        pointer1.offset += 4;
        ClsAnnotationImpl[] ann1 = data.readAnnotations(modifierList, pointer1);
        BytePointer pointer2 = reader.readAttribute("RuntimeInvisibleAnnotations");
        if (pointer2 != null) {
          pointer2.offset += 4;
          ClsAnnotationImpl[] ann2 = data.readAnnotations(modifierList, pointer2);
          ClsAnnotationImpl[] result = ArrayUtil.mergeArrays(ann1, ann2, ClsAnnotationImpl.class);
          return result;
        }
        else {
          return ann1;
        }
      }
      else {
        BytePointer pointer2 = reader.readAttribute("RuntimeInvisibleAnnotations");
        if (pointer2 != null) {
          pointer2.offset += 4;
          return data.readAnnotations(modifierList, pointer2);
        }
        else {
          return ClsAnnotationImpl.EMPTY_ARRAY;
        }
      }
    }
    else {
      DeclarationView view = (DeclarationView)element.getRepositoryManager().getItemView(id);
      String[] annotationTexts = view.getAnnotations(id);
      ClsAnnotationImpl[] result = new ClsAnnotationImpl[annotationTexts.length];
      for (int i = 0; i < annotationTexts.length; i++) {
        result[i] =
        (ClsAnnotationImpl)ClsAnnotationsUtil.createMemberValueFromText(annotationTexts[i], element.getManager(), modifierList);
      }

      return result;
    }
  }

  public static void writeAnnotations(PsiModifierListOwner owner, RecordDataOutput record) throws IOException {
    PsiModifierList modifierList = owner.getModifierList();
    if (modifierList != null) {
      PsiAnnotation[] annotations = modifierList.getAnnotations();
      record.writeInt(annotations.length);
      for (int i = 0; i < annotations.length; i++) {
        record.writeUTF(annotations[i].getText());
      }
    }
    else {
      record.writeInt(0);
    }
  }

  public static void writeAnnotationsINT(PsiModifierListOwner owner, RecordDataOutput record) throws IOException {
    PsiModifierList modifierList = owner.getModifierList();
    if (modifierList != null) {
      PsiAnnotation[] annotations = modifierList.getAnnotations();
      RecordUtil.writeINT(record, annotations.length);
      for (int i = 0; i < annotations.length; i++) {
        RecordUtil.writeSTR(record, annotations[i].getText());
      }
    }
    else {
      RecordUtil.writeINT(record, 0);
    }
  }
}
