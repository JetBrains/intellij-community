package com.intellij.psi.impl.compiled;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiElementFactoryImpl;
import com.intellij.psi.impl.cache.DeclarationView;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.parsing.JavaParsingContext;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.cls.BytePointer;
import com.intellij.util.io.RecordDataOutput;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * @author ven
 */
public class ClsAnnotationsUtil {
  private static final Logger LOG = Logger.getInstance("com.intellij.psi.impl.compiled.ClsAnnotationsUtil");
  @NonNls private static final String RUNTIME_VISIBLE_ANNOTATIONS_ATT = "RuntimeVisibleAnnotations";
  @NonNls private static final String RUNTIME_INVISIBLE_ANNOTATIONS_ATT = "RuntimeInvisibleAnnotations";

  private ClsAnnotationsUtil() {}

  @NotNull private static PsiAnnotationMemberValue getMemberValue(PsiElement element, ClsElementImpl parent) {
    if (element instanceof PsiLiteralExpression) {
      PsiLiteralExpression expr = (PsiLiteralExpression)element;
      return new ClsLiteralExpressionImpl(parent, element.getText(), expr.getType(), expr.getValue());
    }
    else if (element instanceof PsiPrefixExpression) {
      PsiExpression operand = ((PsiPrefixExpression) element).getOperand();
      ClsLiteralExpressionImpl literal = (ClsLiteralExpressionImpl) getMemberValue(operand, null);
      ClsPrefixExpressionImpl prefixExpression = new ClsPrefixExpressionImpl(parent, literal);
      literal.setParent(prefixExpression);
      return prefixExpression;
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
        clsInitializers[i] = getMemberValue(initializers[i], arrayValue);
      }
      arrayValue.setInitializers(clsInitializers);
      return arrayValue;

    }
    else if (element instanceof PsiAnnotation) {
      PsiAnnotation psiAnnotation = (PsiAnnotation)element;
      ClsJavaCodeReferenceElementImpl ref = new ClsJavaCodeReferenceElementImpl(null, psiAnnotation.getNameReferenceElement().getCanonicalText());
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

  @NotNull public static PsiAnnotationMemberValue createMemberValueFromText(String text, PsiManager manager, ClsElementImpl parent) {
    PsiJavaFile dummyJavaFile = ((PsiElementFactoryImpl)JavaPsiFacade.getInstance(manager.getProject()).getElementFactory()).getDummyJavaFile(); // kind of hack - we need to resolve classes from java.lang
    final FileElement holderElement = com.intellij.psi.impl.source.DummyHolderFactory.createHolder(manager, dummyJavaFile).getTreeElement();
    final LanguageLevel languageLevel = PsiUtil.getLanguageLevel(parent);
    JavaParsingContext context = new JavaParsingContext(holderElement.getCharTable(), languageLevel);
    TreeElement element = context.getDeclarationParsing().parseMemberValueText(manager, text, languageLevel);
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

  @NotNull public static ClsAnnotationImpl[] getAnnotationsImpl(ClsRepositoryPsiElement element, AttributeReader reader,
                                                       ClsModifierListImpl modifierList) {
    long id = element.getRepositoryId();
    if (id < 0) {
      ClassFileData data = reader.getClassFileData();
      BytePointer pointer1 = reader.readAttribute(RUNTIME_VISIBLE_ANNOTATIONS_ATT);
      if (pointer1 != null) {
        pointer1.offset += 4;
        ClsAnnotationImpl[] ann1 = data.readAnnotations(modifierList, pointer1);
        BytePointer pointer2 = reader.readAttribute(RUNTIME_INVISIBLE_ANNOTATIONS_ATT);
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
        BytePointer pointer2 = reader.readAttribute(RUNTIME_INVISIBLE_ANNOTATIONS_ATT);
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
      ClsAnnotationImpl[] result = annotationTexts.length == 0 ? ClsAnnotationImpl.EMPTY_ARRAY : new ClsAnnotationImpl[annotationTexts.length];
      for (int i = 0; i < annotationTexts.length; i++) {
        result[i] = (ClsAnnotationImpl)createMemberValueFromText(annotationTexts[i], element.getManager(), modifierList);
      }

      return result;
    }
  }

  public static void writeAnnotations(PsiModifierListOwner owner, RecordDataOutput record) throws IOException {
    PsiModifierList modifierList = owner.getModifierList();
    if (modifierList != null) {
      PsiAnnotation[] annotations = modifierList.getAnnotations();
      record.writeInt(annotations.length);
      for (PsiAnnotation annotation : annotations) {
        record.writeUTF(annotation.getText());
      }
    }
    else {
      record.writeInt(0);
    }
  }
}
