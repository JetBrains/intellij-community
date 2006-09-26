package com.intellij.psi.impl.compiled;

import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.PomField;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiConstantEvaluationHelperImpl;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.PsiVariableEx;
import com.intellij.psi.impl.SharedPsiElementImplUtil;
import com.intellij.psi.impl.cache.FieldView;
import com.intellij.psi.impl.cache.InitializerTooLongException;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.presentation.java.SymbolPresentationUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.cls.BytePointer;
import com.intellij.util.cls.ClsFormatException;
import com.intellij.util.cls.ClsUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.text.StringCharacterIterator;
import java.util.Set;

public class ClsFieldImpl extends ClsRepositoryPsiElement implements PsiField, PsiVariableEx, ClsModifierListOwner {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.compiled.ClsFieldImpl");

  private final ClsClassImpl myParent;
  private int myStartOffset;

  private String myName = null;
  private PsiIdentifier myNameIdentifier = null;
  private PsiTypeElement myType = null;
  private ClsModifierListImpl myModifierList = null;
  private PsiDocComment myDocComment = null;
  private Boolean myIsDeprecated = null;
  private PsiExpression myInitializer = null;
  private boolean myInitializerInitialized = false;
  private ClsAnnotationImpl[] myAnnotations = null;

  public ClsFieldImpl(ClsClassImpl parent, int startOffset) {
    super(parent.myManager, -1);
    myParent = parent;
    myStartOffset = startOffset;
  }

  public ClsFieldImpl(PsiManagerImpl manager, long repositoryId) {
    super(manager, repositoryId);
    myParent = null;
    myStartOffset = -1;
  }

  public void setRepositoryId(long repositoryId) {
    super.setRepositoryId(repositoryId);
    if (repositoryId >= 0) {
      myStartOffset = -1;
    }
  }

  @NotNull
  public PsiElement[] getChildren() {
    PsiDocComment docComment = getDocComment();
    PsiModifierList modifierList = getModifierList();
    PsiTypeElement type = getTypeElement();
    PsiIdentifier name = getNameIdentifier();

    int count =
      (docComment != null ? 1 : 0)
      + (modifierList != null ? 1 : 0)
      + (type != null ? 1 : 0)
      + (name != null ? 1 : 0);
    PsiElement[] children = new PsiElement[count];

    int offset = 0;
    if (docComment != null) {
      children[offset++] = docComment;
    }
    if (modifierList != null) {
      children[offset++] = modifierList;
    }
    if (type != null) {
      children[offset++] = type;
    }
    if (name != null) {
      children[offset++] = name;
    }

    return children;
  }

  public PsiElement getParent() {
    final long parentId = getParentId();
    if (parentId < 0) return myParent;
    return getRepositoryElementsManager().findOrCreatePsiElementById(parentId);
  }

  public PsiClass getContainingClass() {
    return (PsiClass)getParent();
  }

  public PsiIdentifier getNameIdentifier() {
    synchronized (PsiLock.LOCK) {
      if (myNameIdentifier == null) {
        myNameIdentifier = new ClsIdentifierImpl(this, getName());
      }
    }
    return myNameIdentifier;
  }

  public @NonNls String getName() {
    if (myName == null) {
    synchronized (PsiLock.LOCK) {
      long repositoryId = getRepositoryId();
      if (repositoryId < 0) {
        try {
          ClassFileData classFileData = myParent.getClassFileData();
          byte[] data = classFileData.getData();
          int offset = myStartOffset + 2;
          int b1 = data[offset++] & 0xFF;
          int b2 = data[offset++] & 0xFF;
          int index = (b1 << 8) + b2;
          offset = classFileData.getOffsetInConstantPool(index);
          myName = ClsUtil.readUtf8Info(data, offset);
        }
        catch (ClsFormatException e) {
          myName = "";
        }
      }
      else {
        myName = getRepositoryManager().getFieldView().getName(repositoryId);
      }
    }
    }
    return myName;
  }

  public PsiElement setName(String name) throws IncorrectOperationException {
    SharedPsiElementImplUtil.setName(getNameIdentifier(), name);
    return this;
  }

  @NotNull
  public PsiType getType() {
    return getTypeElement().getType();
  }

  public PsiTypeElement getTypeElement() {
    if (myType == null) {
      synchronized (PsiLock.LOCK) {
        long repositoryId = getRepositoryId();
        if (repositoryId < 0) {
          if (!parseViaGenericSignature()) {
            try {
              ClassFileData classFileData = myParent.getClassFileData();
              byte[] data = classFileData.getData();
              int offset = myStartOffset + 4;
              int b1 = data[offset++] & 0xFF;
              int b2 = data[offset++] & 0xFF;
              int index = (b1 << 8) + b2;
              offset = classFileData.getOffsetInConstantPool(index);
              offset += 3; // skip tag and length
              String typeText = ClsUtil.getTypeText(data, offset);
              myType = new ClsTypeElementImpl(this, typeText, ClsTypeElementImpl.VARIANCE_NONE);
            }
            catch (ClsFormatException e) {
              myType = null;
            }
          }
        }
        else {
          String typeText = getRepositoryManager().getFieldView().getTypeText(repositoryId);
          myType = new ClsTypeElementImpl(this, typeText, ClsTypeElementImpl.VARIANCE_NONE);
        }
      }
    }
    return myType;
  }

  public String getSignatureAttribute() {
    try {
      ClassFileData data = myParent.getClassFileData();
      return data.readUtf8Attribute(data.findAttribute(myStartOffset + 6, "Signature"));
    }
    catch (ClsFormatException e) {
      return null;
    }
  }

  private boolean parseViaGenericSignature() {
    if (getRepositoryId() >= 0) return false;
    try {
      String signature = getSignatureAttribute();
      if (signature == null) return false;

      myType = new ClsTypeElementImpl(this, GenericSignatureParsing.parseTypeString(new StringCharacterIterator(signature, 0)),
                                      ClsTypeElementImpl.VARIANCE_NONE);
      return true;
    }
    catch (ClsFormatException e) {
      return false;
    }
  }

  public PsiModifierList getModifierList() {
    synchronized (PsiLock.LOCK) {
      if (myModifierList == null) {
        int flags = getAccessFlags();
        myModifierList = new ClsModifierListImpl(this, flags & ClsUtil.ACC_FIELD_MASK);
      }
    }
    return myModifierList;
  }

  public boolean hasModifierProperty(String name) {
    return getModifierList().hasModifierProperty(name);
  }

  private int getAccessFlags() {
  synchronized (PsiLock.LOCK) {
    long repositoryId = getRepositoryId();
    if (repositoryId < 0) {
      try {
        int offset = myStartOffset;
        byte[] data = myParent.getClassFileData().getData();
        if (offset + 2 > data.length) {
          throw new ClsFormatException();
        }
        int b1 = data[offset++] & 0xFF;
        int b2 = data[offset++] & 0xFF;
        return (b1 << 8) + b2;
      }
      catch (ClsFormatException e) {
        return 0;
      }
    }
    else {
      FieldView fieldView = getRepositoryManager().getFieldView();
      return fieldView.getModifiers(repositoryId);
    }
  }
  }

  public PsiExpression getInitializer() {
    synchronized (PsiLock.LOCK) {
      if (!myInitializerInitialized) {
        long repositoryId = getRepositoryId();
        if (repositoryId < 0) {
          try {
            myInitializer = createInitializerFromClassFile();
          }
          catch (ClsFormatException e) {
          }
        }
        else {
          myInitializer = createInitializerFromRepository();
        }
        myInitializerInitialized = true;
      }
    }
    return myInitializer;
  }

  private PsiExpression createInitializerFromClassFile() throws ClsFormatException {
    ClassFileData classFileData = myParent.getClassFileData();
    BytePointer ptr = classFileData.findAttribute(myStartOffset + 6, "ConstantValue");
    if (ptr == null) return null;
    ptr.offset += 4; // skip length
    int index = ClsUtil.readU2(ptr);
    int offsetInPool = classFileData.getOffsetInConstantPool(index);

    ptr = new BytePointer(classFileData.getData(), offsetInPool);
    int kind = ClsUtil.readU1(ptr);

    PsiType type = getType();
    if (PsiType.INT == type) {
      if (kind != ClsUtil.CONSTANT_Integer) {
        throw new ClsFormatException();
      }
      int v = ClsUtil.readU4(ptr);
      return createNumberExpr(v);
    }
    else if (PsiType.LONG == type) {
      if (kind != ClsUtil.CONSTANT_Long) {
        throw new ClsFormatException();
      }
      int valueH = ClsUtil.readU4(ptr);
      int valueL = ClsUtil.readU4(ptr);
      long v = ((long)valueH << 32) | (valueL & 0xFFFFFFFFL);
      return createNumberExpr(v);
    }
    else if (PsiType.SHORT == type) {
      if (kind != ClsUtil.CONSTANT_Integer) {
        throw new ClsFormatException();
      }
      int v = ClsUtil.readU4(ptr);
      return createNumberExpr(v);
    }
    else if (PsiType.BYTE == type) {
      if (kind != ClsUtil.CONSTANT_Integer) {
        throw new ClsFormatException();
      }
      int v = ClsUtil.readU4(ptr);
      return createNumberExpr(v);
    }
    else if (PsiType.CHAR == type) {
      if (kind != ClsUtil.CONSTANT_Integer) {
        throw new ClsFormatException();
      }
      char v = (char)ClsUtil.readU4(ptr);
      Object value = new Character(v);
      String text = literalToString(value.toString(), '\'');
      return new ClsLiteralExpressionImpl(this, text, type, value);
    }
    else if (PsiType.BOOLEAN == type) {
      if (kind != ClsUtil.CONSTANT_Integer) {
        throw new ClsFormatException();
      }
      int v = ClsUtil.readU4(ptr);
      Object value = v != 0 ? Boolean.TRUE : Boolean.FALSE;
      String text = value.toString();
      return new ClsLiteralExpressionImpl(this, text, type, value);
    }
    else if (PsiType.FLOAT == type) {
      if (kind != ClsUtil.CONSTANT_Float) {
        throw new ClsFormatException();
      }
      float v = ClsUtil.readFloat(ptr);
      @NonNls String text;
      if (Float.isInfinite(v)) {
        text = v > 0 ? "Float.POSITIVE_INFINITY" : "Float.NEGATIVE_INFINITY";
      }
      else if (Float.isNaN(v)) {
        text = "Float.NaN";
      }
      else {
        text = Float.toString(v) + "f";
      }
      return ClsParsingUtil.createExpressionFromText(text, getManager(), this);
    }
    else if (PsiType.DOUBLE == type) {
      if (kind != ClsUtil.CONSTANT_Double) {
        throw new ClsFormatException();
      }
      double v = ClsUtil.readDouble(ptr);
      @NonNls String text;
      if (Double.isInfinite(v)) {
        text = v > 0 ? "Double.POSITIVE_INFINITY" : "Double.NEGATIVE_INFINITY";
      }
      else if (Double.isNaN(v)) {
        text = "Double.NaN";
      }
      else {
        text = Double.toString(v);
      }
      return ClsParsingUtil.createExpressionFromText(text, getManager(), this);
    }
    else if (getTypeElement() != null && "java.lang.String".equals(((ClsTypeElementImpl)getTypeElement()).getCanonicalText())) {
      if (kind != ClsUtil.CONSTANT_String) {
        throw new ClsFormatException();
      }
      int stringIndex = ClsUtil.readU2(ptr);
      ptr.offset = classFileData.getOffsetInConstantPool(stringIndex);
      String value = ClsUtil.readUtf8Info(ptr);
      String text = literalToString(value, '"');
      return new ClsLiteralExpressionImpl(this, text, type, value);
    }
    else {
      throw new ClsFormatException();
    }
  }

  private PsiExpression createNumberExpr(long v) {
    if (v < 0) {
      ClsLiteralExpressionImpl literalExpression = (ClsLiteralExpressionImpl)_createNumberExpr(-v, true);
      ClsPrefixExpressionImpl prefixExpression = new ClsPrefixExpressionImpl(this, literalExpression);
      literalExpression.setParent(prefixExpression);
      return prefixExpression;
    }

    return _createNumberExpr(v, false);
  }

  private PsiExpression _createNumberExpr(long v, boolean negated) {
    @NonNls String text = Long.toString(v);
    if (negated && StringUtil.startsWithChar(text, '-')) {
      LOG.assertTrue(v == -1L << 63);
      text = text.substring(1);
    }

    Object value;
    PsiType type;
    if (0 <= v && v <= Integer.MAX_VALUE || negated && v == (long)Integer.MAX_VALUE + 1) {
      type = PsiType.INT;
      value = new Integer((int)v);
    }
    else {
      type = PsiType.LONG;
      text += "L";
      value = new Long(v);
    }
    return new ClsLiteralExpressionImpl(this, text, type, value);
  }

  private PsiExpression createInitializerFromRepository() {
    String initializerText;
    try {
        initializerText = getRepositoryManager().getFieldView().getInitializerText(getRepositoryId());
      }
    catch (InitializerTooLongException e) {
      return null; //??
    }

    if (initializerText == null) return null;

    return ClsParsingUtil.createExpressionFromText(initializerText, getManager(), this);
  }

  private static String literalToString(String value, char quote) {
    int length = value.length();
    @NonNls StringBuffer buffer = new StringBuffer(length + 3);
    buffer.append(quote);

    for (int i = 0; i < length; i++) {
      char c = value.charAt(i);
      if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
        buffer.append(c);
      continue;
      }

      switch (c) {
      case '\b':
             buffer.append("\\b");
             break;
      case '\t':
             buffer.append("\\t");
             break;
      case '\n':
             buffer.append("\\n");
             break;
      case '\f':
             buffer.append("\\f");
             break;
      case '\r':
             buffer.append("\\r");
             break;
      case '\\':
             buffer.append("\\\\");
             break;
      default:
             if (c == quote) {
               buffer.append("\\" + quote);
             }
             else if (Character.isISOControl(c)) {
               String hexCode = Integer.toHexString(c).toUpperCase();
               buffer.append("\\x");
               int paddingCount = 4 - hexCode.length();
               while (paddingCount-- > 0) {
                 buffer.append(0);
               }
               buffer.append(hexCode);
             }
             else {
               buffer.append(c);
             }
      }
    }

    buffer.append(quote);
    return buffer.toString();
  }

  public boolean hasInitializer() {
    return getInitializer() != null;
  }

  public Object computeConstantValue() {
    return computeConstantValue(new THashSet<PsiVariable>());
  }

  public Object computeConstantValue(Set<PsiVariable> visitedVars) {
    if (!hasModifierProperty(PsiModifier.FINAL)) return null;
    PsiExpression initializer = getInitializer();
    if (initializer == null) return null;

    final String qName = getContainingClass().getQualifiedName();
    if ("java.lang.Float".equals(qName)) {
      final @NonNls String name = getName();
      if ("POSITIVE_INFINITY".equals(name)) return new Float(Float.POSITIVE_INFINITY);
      if ("NEGATIVE_INFINITY".equals(name)) return new Float(Float.NEGATIVE_INFINITY);
      if ("NaN".equals(name)) return new Float(Float.NaN);
    }
    else if ("java.lang.Double".equals(qName)) {
      final @NonNls String name = getName();
      if ("POSITIVE_INFINITY".equals(name)) return new Double(Double.POSITIVE_INFINITY);
      if ("NEGATIVE_INFINITY".equals(name)) return new Double(Double.NEGATIVE_INFINITY);
      if ("NaN".equals(name)) return new Double(Double.NaN);
    }

    return PsiConstantEvaluationHelperImpl.computeCastTo(initializer, getType(), visitedVars);
  }

  public boolean isDeprecated() {
  synchronized (PsiLock.LOCK) {
    if (myIsDeprecated == null) {
      long repositoryId = getRepositoryId();
      if (repositoryId < 0) {
        try {
          boolean isDeprecated = myParent.getClassFileData().findAttribute(myStartOffset + 6, "Deprecated") != null;
          myIsDeprecated = isDeprecated ? Boolean.TRUE : Boolean.FALSE;
        }
        catch (ClsFormatException e) {
          myIsDeprecated = Boolean.FALSE;
        }
      }
      else {
        boolean isDeprecated = getRepositoryManager().getFieldView().isDeprecated(repositoryId);
        myIsDeprecated = isDeprecated ? Boolean.TRUE : Boolean.FALSE;
      }
    }
  }
               return myIsDeprecated.booleanValue();
  }

  public PsiDocComment getDocComment() {
    if (!isDeprecated()) return null;

    synchronized (PsiLock.LOCK) {
      if (myDocComment == null) {
        myDocComment = new ClsDocCommentImpl(this);
      }
    }
    return myDocComment;
  }

  public void normalizeDeclaration() throws IncorrectOperationException {
  }

  public void appendMirrorText(final int indentLevel, final StringBuffer buffer) {
    ClsDocCommentImpl docComment = (ClsDocCommentImpl)getDocComment();
    if (docComment != null) {
      docComment.appendMirrorText(indentLevel, buffer);
      goNextLine(indentLevel, buffer);
    }
    ((ClsElementImpl)getModifierList()).appendMirrorText(indentLevel, buffer);
    ((ClsElementImpl)getTypeElement()).appendMirrorText(indentLevel, buffer);
    buffer.append(' ');
    ((ClsElementImpl)getNameIdentifier()).appendMirrorText(indentLevel, buffer);
    if (getInitializer() != null) {
      buffer.append(" = ");
      buffer.append(getInitializer().getText());
    }
    buffer.append(';');
  }

  public void setMirror(TreeElement element) {
    LOG.assertTrue(isValid());
    LOG.assertTrue(myMirror == null);
    myMirror = element;

    PsiField mirror = (PsiField)SourceTreeToPsiMap.treeElementToPsi(element);
    if (getDocComment() != null) {
        ((ClsElementImpl)getDocComment()).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirror.getDocComment()));
    }
      ((ClsElementImpl)getModifierList()).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirror.getModifierList()));
      ((ClsElementImpl)getTypeElement()).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirror.getTypeElement()));
      ((ClsElementImpl)getNameIdentifier()).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirror.getNameIdentifier()));
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitField(this);
  }

  public String toString() {
    return "PsiField:" + getName();
  }

  public PsiElement getNavigationElement() {
    PsiClass sourceClassMirror = ((ClsClassImpl)getParent()).getSourceMirrorClass();
    PsiElement sourceFieldMirror = sourceClassMirror != null ? sourceClassMirror.findFieldByName(myName, false) : null;
    return sourceFieldMirror != null ? sourceFieldMirror : this;
  }

  @NotNull
  public ClsAnnotationImpl[] getAnnotations() {
    if (myAnnotations == null) {
      ClsAnnotationsUtil.AttributeReader reader = new ClsAnnotationsUtil.AttributeReader() {
        public BytePointer readAttribute(String attributeName) {
          try {
            return myParent.getClassFileData().findAttribute(myStartOffset + 6, attributeName);
          }
          catch (ClsFormatException e) {
            return null;
          }
        }

        public ClassFileData getClassFileData() {
          return myParent.getClassFileData();
        }
      };
      myAnnotations = ClsAnnotationsUtil.getAnnotationsImpl(this, reader, myModifierList);
    }

    return myAnnotations;
  }

  public PomField getPom() {
    //TODO:
    return null;
  }

  public ItemPresentation getPresentation() {
    return SymbolPresentationUtil.getFieldPresentation(this);
  }
  public void setInitializer(PsiExpression initializer) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }
}
