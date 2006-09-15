package com.intellij.psi.impl.cache.impl.repositoryCache;

import com.intellij.lang.ASTNode;
import com.intellij.lexer.FilterLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.cache.ModifierFlags;
import com.intellij.psi.impl.compiled.ClsTypeElementImpl;
import com.intellij.psi.impl.source.PsiJavaFileImpl;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.impl.source.tree.SharedImplUtil;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ArrayUtil;
import com.intellij.util.io.PersistentStringEnumerator;
import com.intellij.util.io.RecordDataOutput;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author max
 */
public class RecordUtil {
  private static final @NonNls String DEPRECATED_ANNOTATION_NAME = "Deprecated";
  private static final @NonNls String DEPRECATED_TAG = "@deprecated";

  private RecordUtil() {}

  private static final Ref<ArrayList<PsiClass>> ourList = new Ref<ArrayList<PsiClass>>();
  private static final String[][] ourEmptyStringStringArray = new String[0][];
  private static final TypeInfo[] ourEmptyTypeArray = new TypeInfo[0];

  public static List<PsiClass> getInnerClasses(PsiElement psiElement) {
    ourList.set(null);

    if (psiElement != null && mayContainClassesInside(psiElement)) {
      psiElement.accept(new PsiRecursiveElementVisitor() {
        public void visitClass(PsiClass aClass) {
          if (ourList.isNull()) ourList.set(new ArrayList<PsiClass>());
          ourList.get().add(aClass);
        }

        public void visitTypeParameter(PsiTypeParameter classParameter) {
          // just skip (because type parameter is class - bad!)
        }
      });
    }

    return ourList.get();
  }

  private static boolean mayContainClassesInside(PsiElement psiElement) {
    PsiFile psiFile = psiElement.getContainingFile();
    boolean mayHaveClassesInside = false;
    if (psiFile instanceof PsiJavaFileImpl) {
      PsiJavaFileImpl impl = (PsiJavaFileImpl)psiFile;
      Lexer originalLexer = impl.createLexer();
      FilterLexer lexer = new FilterLexer(originalLexer, new FilterLexer.SetFilter(ElementType.WHITE_SPACE_OR_COMMENT_BIT_SET));
      final char[] buffer = psiElement.textToCharArray();
      lexer.start(buffer, 0, buffer.length);
      boolean isInNewExpression = false;
      boolean isRightAfterNewExpression = false;
      int angleLevel = 0;
      int parenLevel = 0;
      do {
        IElementType tokenType = lexer.getTokenType();
        if (tokenType == null) break;

        if (tokenType == JavaTokenType.NEW_KEYWORD) {
          isInNewExpression = true;
        }
        else if (tokenType == JavaTokenType.LPARENTH) {
          if (isInNewExpression) parenLevel++;
        }
        else if (tokenType == JavaTokenType.LT) {
          if (isInNewExpression) angleLevel++;
        }
        else if (tokenType == JavaTokenType.GT) {
          if (isInNewExpression) angleLevel--;
        }
        else if (tokenType == JavaTokenType.RPARENTH) {
          if (isInNewExpression) {
            parenLevel--;
            if (parenLevel == 0) {
              isRightAfterNewExpression = true;
            }
          }
        }
        else if (tokenType == JavaTokenType.LBRACE) {
          if (isInNewExpression || isRightAfterNewExpression) {
            mayHaveClassesInside = true;
          }
        }
        else if (tokenType == JavaTokenType.LBRACKET) {
          if (parenLevel == 0 && angleLevel == 0) isInNewExpression = false;
        }
        else if (tokenType == JavaTokenType.INTERFACE_KEYWORD || tokenType == JavaTokenType.CLASS_KEYWORD ||
                 tokenType == JavaTokenType.ENUM_KEYWORD) {
          mayHaveClassesInside = true;
        }

        if (isInNewExpression && isRightAfterNewExpression) {
          isInNewExpression = false;
        }
        else {
          isRightAfterNewExpression = false;
        }

        lexer.advance();
      }
      while (!mayHaveClassesInside);
    }
    return mayHaveClassesInside;
  }

  static int[] createIntArray(int size) {
    if (size == 0) return ArrayUtil.EMPTY_INT_ARRAY;
    return new int[size];
  }

  static String[] createStringArray(int size) {
    if (size == 0) return ArrayUtil.EMPTY_STRING_ARRAY;
    return new String[size];
  }

  static String[][] createStringStringArray(int size) {
    if (size == 0) return ourEmptyStringStringArray;
    return new String[size][];
  }

  static boolean[] createBooleanArray(int size) {
    if (size == 0) return ArrayUtil.EMPTY_BOOLEAN_ARRAY;
    return new boolean[size];
  }

  static TypeInfo[] createTypeInfoArray(int size) {
    if (size == 0) return ourEmptyTypeArray;
    TypeInfo[] res = new TypeInfo[size];
    for (int i = 0; i < res.length; i++) {
      res[i] = new TypeInfo();
    }
    return res;
  }

  public static int packFlags(PsiElement psiElement) {
    int packed = packModifiers(psiElement);
    if (isDeprecatedByDocComment(psiElement)) {
      packed |= ModifierFlags.DEPRECATED_MASK;
    } else { //No need for yet another deprecated flag if the first is true
      if (isDeprecatedByAnnotation(psiElement)) {
        packed |= ModifierFlags.ANNOTATION_DEPRECATED_MASK;
      }
    }

    if (isInterface(psiElement)) {
      packed |= ModifierFlags.INTERFACE_MASK;
    }

    if (isEnum(psiElement)) {
      packed |= ModifierFlags.ENUM_MASK;
    }

    if (isAnnotationType(psiElement)) {
      packed |= ModifierFlags.ANNOTATION_TYPE_MASK;
    }

    return packed;
  }

  private static boolean isDeprecatedByAnnotation(PsiElement element) {
    if (element instanceof PsiModifierListOwner) {
      PsiModifierList modifierList = ((PsiModifierListOwner)element).getModifierList();
      if (modifierList != null) {
        PsiAnnotation[] annotations = modifierList.getAnnotations();
        for (PsiAnnotation annotation : annotations) {
          PsiJavaCodeReferenceElement nameElement = annotation.getNameReferenceElement();
          if (nameElement != null && DEPRECATED_ANNOTATION_NAME.equals(nameElement.getReferenceName())) return true;
        }
      }
    }

    return false;
  }


  private static int packModifiers(PsiElement psiElement) {
    PsiModifierList psiModifierList = null;

    if (psiElement instanceof PsiModifierListOwner) {
      psiModifierList = ((PsiModifierListOwner)psiElement).getModifierList();
    }
    int packed = 0;

    if (psiModifierList != null) {
      if (psiModifierList.hasModifierProperty(PsiModifier.ABSTRACT)) {
        packed |= ModifierFlags.ABSTRACT_MASK;
      }
      if (psiModifierList.hasModifierProperty(PsiModifier.FINAL)) {
        packed |= ModifierFlags.FINAL_MASK;
      }
      if (psiModifierList.hasModifierProperty(PsiModifier.NATIVE)) {
        packed |= ModifierFlags.NATIVE_MASK;
      }
      if (psiModifierList.hasModifierProperty(PsiModifier.STATIC)) {
        packed |= ModifierFlags.STATIC_MASK;
      }
      if (psiModifierList.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
        packed |= ModifierFlags.SYNCHRONIZED_MASK;
      }
      if (psiModifierList.hasModifierProperty(PsiModifier.TRANSIENT)) {
        packed |= ModifierFlags.TRANSIENT_MASK;
      }
      if (psiModifierList.hasModifierProperty(PsiModifier.VOLATILE)) {
        packed |= ModifierFlags.VOLATILE_MASK;
      }
      if (psiModifierList.hasModifierProperty(PsiModifier.PRIVATE)) {
        packed |= ModifierFlags.PRIVATE_MASK;
      }
      if (psiModifierList.hasModifierProperty(PsiModifier.PROTECTED)) {
        packed |= ModifierFlags.PROTECTED_MASK;
      }
      if (psiModifierList.hasModifierProperty(PsiModifier.PUBLIC)) {
        packed |= ModifierFlags.PUBLIC_MASK;
      }
      if (psiModifierList.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) {
        packed |= ModifierFlags.PACKAGE_LOCAL_MASK;
      }
      if (psiModifierList.hasModifierProperty(PsiModifier.STRICTFP)) {
        packed |= ModifierFlags.STRICTFP_MASK;
      }
    }

    return packed;
  }

  private static boolean isDeprecatedByDocComment(PsiElement psiElement) {
    if (!(psiElement instanceof PsiDocCommentOwner)) return false;

    final PsiDocCommentOwner owner = (PsiDocCommentOwner)psiElement;
    if (owner instanceof PsiCompiledElement) {
      return owner.isDeprecated();
    }

    final ASTNode node = psiElement.getNode();
    final ASTNode docNode = node.findChildByType(JavaDocElementType.DOC_COMMENT);
    if (docNode == null || docNode.getText().indexOf(DEPRECATED_TAG) < 0) return false;

    PsiDocComment docComment = owner.getDocComment();
    return docComment != null && docComment.findTagByName("deprecated") != null;
  }

  private static boolean isInterface(PsiElement psiElement) {
    if (!(psiElement instanceof PsiClass)) return false;
    return ((PsiClass)psiElement).isInterface();
  }

  private static boolean isEnum(PsiElement psiElement) {
    if (!(psiElement instanceof PsiClass)) return false;
    return ((PsiClass)psiElement).isEnum();
  }

  private static boolean isAnnotationType(PsiElement psiElement) {
    if (!(psiElement instanceof PsiClass)) return false;
    return ((PsiClass)psiElement).isAnnotationType();
  }


  private static final TObjectIntHashMap<String> ourModifierNameToFlagMap;
  private static final TObjectIntHashMap<String> ourFrequentTypeIndex;
  private static final TIntObjectHashMap<String> ourIndexFrequentType;

  static {
    ourModifierNameToFlagMap = new TObjectIntHashMap<String>();
    ourFrequentTypeIndex = new TObjectIntHashMap<String>();
    ourIndexFrequentType = new TIntObjectHashMap<String>();

    initMaps();
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static void initMaps() {
    ourModifierNameToFlagMap.put(PsiModifier.PUBLIC, ModifierFlags.PUBLIC_MASK);
    ourModifierNameToFlagMap.put(PsiModifier.PROTECTED, ModifierFlags.PROTECTED_MASK);
    ourModifierNameToFlagMap.put(PsiModifier.PRIVATE, ModifierFlags.PRIVATE_MASK);
    ourModifierNameToFlagMap.put(PsiModifier.PACKAGE_LOCAL, ModifierFlags.PACKAGE_LOCAL_MASK);
    ourModifierNameToFlagMap.put(PsiModifier.STATIC, ModifierFlags.STATIC_MASK);
    ourModifierNameToFlagMap.put(PsiModifier.ABSTRACT, ModifierFlags.ABSTRACT_MASK);
    ourModifierNameToFlagMap.put(PsiModifier.FINAL, ModifierFlags.FINAL_MASK);
    ourModifierNameToFlagMap.put(PsiModifier.NATIVE, ModifierFlags.NATIVE_MASK);
    ourModifierNameToFlagMap.put(PsiModifier.SYNCHRONIZED, ModifierFlags.SYNCHRONIZED_MASK);
    ourModifierNameToFlagMap.put(PsiModifier.TRANSIENT, ModifierFlags.TRANSIENT_MASK);
    ourModifierNameToFlagMap.put(PsiModifier.VOLATILE, ModifierFlags.VOLATILE_MASK);
    ourModifierNameToFlagMap.put(PsiModifier.STRICTFP, ModifierFlags.STRICTFP_MASK);
    ourModifierNameToFlagMap.put("interface", ModifierFlags.INTERFACE_MASK);
    ourModifierNameToFlagMap.put("deprecated", ModifierFlags.DEPRECATED_MASK);
    ourModifierNameToFlagMap.put("@Deprecated", ModifierFlags.ANNOTATION_DEPRECATED_MASK);
    ourModifierNameToFlagMap.put("enum", ModifierFlags.ENUM_MASK);
    ourModifierNameToFlagMap.put("@", ModifierFlags.ANNOTATION_TYPE_MASK);


    ourFrequentTypeIndex.put("boolean", 1);
    ourIndexFrequentType.put(1, "boolean");

    ourFrequentTypeIndex.put("byte", 2);
    ourIndexFrequentType.put(2, "byte");

    ourFrequentTypeIndex.put("char", 3);
    ourIndexFrequentType.put(3, "char");

    ourFrequentTypeIndex.put("double", 4);
    ourIndexFrequentType.put(4, "double");

    ourFrequentTypeIndex.put("float", 5);
    ourIndexFrequentType.put(5, "float");

    ourFrequentTypeIndex.put("int", 6);
    ourIndexFrequentType.put(6, "int");

    ourFrequentTypeIndex.put("long", 7);
    ourIndexFrequentType.put(7, "long");

    ourFrequentTypeIndex.put("null", 8);
    ourIndexFrequentType.put(8, "null");

    ourFrequentTypeIndex.put("short", 9);
    ourIndexFrequentType.put(9, "short");

    ourFrequentTypeIndex.put("void", 10);
    ourIndexFrequentType.put(10, "void");

    ourFrequentTypeIndex.put("Object", 11);
    ourIndexFrequentType.put(11, "Object");

    ourFrequentTypeIndex.put("java.lang.Object", 12);
    ourIndexFrequentType.put(12, "java.lang.Object");

    ourFrequentTypeIndex.put("String", 13);
    ourIndexFrequentType.put(13, "String");

    ourFrequentTypeIndex.put("java.lang.String", 14);
    ourIndexFrequentType.put(14, "java.lang.String");
  }


  public static boolean hasModifierProperty(String psiModifier, int packed) {
    return (ourModifierNameToFlagMap.get(psiModifier) & packed) != 0;
  }

  public static void readType(DataInput record, TypeInfo view) throws IOException {
    int flags = record.readByte();

    if (flags == 0x02) {
      view.arrayCount = 0;
      view.isEllipsis = false;
      view.text = null;
      return;
    }

    view.arrayCount = record.readByte();
    view.isEllipsis = record.readBoolean();
    if (flags == 0x00) {
      view.text = record.readUTF();
    }
    else {
      view.text = ourIndexFrequentType.get(flags >> 2);
    }
  }

  public static void skipType(DataInput record) throws IOException {
    byte flags = record.readByte();
    if (flags == 0x02) return;
    record.readByte();
    record.readBoolean();
    if (flags == 0x00) {
      skipUTF(record);
    }
  }

  public static void skipUTF(DataInput record) throws IOException {
    record.skipBytes(record.readUnsignedShort());
  }

  public static String createTypeText(TypeInfo typeInfo) {
    if (typeInfo.arrayCount == 0) return typeInfo.text;
    if (typeInfo.text == null) return null;

    StringBuffer buf = new StringBuffer(typeInfo.text);
    final int arrayCount = !typeInfo.isEllipsis ? typeInfo.arrayCount : typeInfo.arrayCount - 1;
    for (int i = 0; i < arrayCount; i++) buf.append("[]");
    if (typeInfo.isEllipsis) {
      buf.append("...");
    }

    return buf.toString();
  }

  public static int getLocalId(long id) {
    return (int)(id & 0x0FFFFFFFL);
  }


  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.cache.impl.repositoryCache");

  public static String readNAME(DataInput record, PersistentStringEnumerator nameStore) throws IOException {
    final int low = record.readUnsignedByte();
    final int nameId = (readINT(record) << 8) | low;
    return nameStore.valueOf(nameId);
  }

  public static void writeNAME(RecordDataOutput record, final String name, PersistentStringEnumerator nameStore) throws IOException {
    final int nameId = nameStore.enumerate(name);
    record.writeByte(nameId & 0xFF);
    writeINT(record, (nameId >> 8));
  }

  public static void skipNAME(DataInput record) throws IOException {
    record.readUnsignedByte();
    readINT(record);
  }

  public static void readTYPE(DataInput record, TypeInfo view, PersistentStringEnumerator nameStore) throws IOException {
    final int b = 0xFF & record.readByte();
    final int tag = b & 0x3;
    final int index = 0xF & (b >> 2);
    final int flags = 0x3 & (b >> 6);

    if (tag == 0x02) {
      view.arrayCount = 0;
      view.isEllipsis = false;
      view.text = null;
      return;
    }

    view.arrayCount = (flags & 1) != 0 ? record.readByte() : 0;
    view.isEllipsis = (flags & 2) != 0;
    if (tag == 0x00) {
      view.text = readNAME(record, nameStore);
      //view.text = readSTR(record);
    }
    else {
      view.text = ourIndexFrequentType.get(index);
    }
  }

  public static void writeTYPE(RecordDataOutput record,
                               PsiType type,
                               PsiTypeElement typeElement,
                               PersistentStringEnumerator nameStore)
    throws IOException {
    if (typeElement == null) {
      record.writeByte(0x02);
      return;
    }

    final boolean isEllipsis = type instanceof PsiEllipsisType;
    int arrayCount = type.getArrayDimensions();

    while (typeElement.getFirstChild() instanceof PsiTypeElement) {
      typeElement = (PsiTypeElement)typeElement.getFirstChild();
    }

    String text = typeElement instanceof PsiCompiledElement
                  ? ((ClsTypeElementImpl)typeElement).getCanonicalText()
                  : typeElement.getText();
    int frequentIndex = ourFrequentTypeIndex.get(text);
    LOG.assertTrue(frequentIndex == 0 || frequentIndex < 16);
    int flags = arrayCount == 0 ? 0 : 1;
    if (isEllipsis) flags |= 2;
    if (frequentIndex != 0) {
      record.writeByte((flags << 6) | 0x01 | (frequentIndex << 2));
      if (arrayCount != 0) {
        record.writeByte(arrayCount);
      }
    }
    else {
      record.writeByte((flags << 6) | 0x00);
      if (arrayCount != 0) {
        record.writeByte(arrayCount);
      }
      writeNAME(record, text, nameStore);
      //writeSTR(record, text);
    }
  }

  public static void skipTYPE(DataInput record) throws IOException {
    final byte b = record.readByte();
    final int tag = b & 0x3;
    final int flags = 0x3 & (b >> 6);
    if (tag == 0x02) return;
    if ((flags & 1) != 0) {
      record.readByte();
    }
    if (tag == 0x00) {
      skipNAME(record);
      //skipSTR(record);
    }
  }

  public static void skipINT(DataInput record) throws IOException {
    readINT(record);
  }

  public static int readINT(DataInput record) throws IOException {
    final int val = record.readUnsignedByte();
    if (val < 192) {
      return val;
    }

    for (int res = val - 192, sh = 6; ; sh += 7) {
      int next = record.readUnsignedByte();
      res |= (next & 0x7F) << sh;
      if ((next & 0x80) == 0) {
        return res;
      }
    }
  }

  public static void writeINT(RecordDataOutput record, int val) throws IOException {
    /*
    if (0 <= val && val < 255)
      record.writeByte(val);
    else {
      record.writeByte(255);
      record.writeInt(val);
    }
    */
    if (0 <= val && val < 192) {
      record.writeByte(val);
    }
    else {
      record.writeByte(192 + (val & 0x3F));
      val >>>= 6;
      while (val >= 128) {
        record.writeByte((val & 0x7F) | 0x80);
        val >>>= 7;
      }
      record.writeByte(val);
    }
  }

  public static void skipSINT(DataInput record) throws IOException {
    readSINT(record);
  }

  public static int readSINT(DataInput record) throws IOException {
    return readINT(record) - 64;
  }

  public static void writeSINT(RecordDataOutput record, int val) throws IOException {
    writeINT(record, val + 64);
  }


  public static int readID(DataInput record, int prevId) throws IOException {
    return prevId + readSINT(record);
  }

  public static int readID(DataInput record) throws IOException {
    int low = record.readUnsignedByte();
    return low + (readINT(record) << 8);
  }

  public static void writeID(RecordDataOutput record, int prevId, int id) throws IOException {
    writeSINT(record, id - prevId);
  }

  public static void writeID(RecordDataOutput record, int id) throws IOException {
    record.writeByte(id & 0xFF);
    writeINT(record, id >>> 8);
  }

  private final static long timeBase = 33l * 365l * 24l * 3600l * 1000l;

  public static void writeTIME(RecordDataOutput record, long timestamp) throws IOException {
    long relStamp = timestamp - timeBase;
    if (relStamp < 0 || relStamp >= 0xFF00000000l) {
      record.writeByte(255);
      record.writeLong(timestamp);
    }
    else {
      record.writeByte((int)(relStamp >> 32));
      record.writeByte((int)(relStamp >> 24));
      record.writeByte((int)(relStamp >> 16));
      record.writeByte((int)(relStamp >> 8));
      record.writeByte((int)(relStamp >> 0));
    }
  }

  public static long readTIME(DataInput record) throws IOException {
    final int first = record.readUnsignedByte();
    if (first == 255) {
      return record.readLong();
    }
    else {
      final int second = record.readUnsignedByte();

      final int third = record.readUnsignedByte() << 16;
      final int fourth = record.readUnsignedByte() << 8;
      final int fifth = record.readUnsignedByte();
      return ((((long)((first << 8) | second)) << 24) | (third | fourth | fifth)) + timeBase;
    }
  }

  public static PersistentStringEnumerator getNameStoreFile(String name, boolean toDelete, File cacheFolder)
    throws PersistentStringEnumerator.CorruptedException {
    File ioFile = new File(cacheFolder, name);
    if (toDelete) {
      LOG.assertTrue(FileUtil.delete(ioFile));
    }

    PersistentStringEnumerator names = null;
    try {
      names = new PersistentStringEnumerator(ioFile);
    }
    catch (PersistentStringEnumerator.CorruptedException e) {
      throw e;
    }
    catch (IOException e) {
      LOG.error(e);
      if (!toDelete)
        names = getNameStoreFile (name, true, cacheFolder);
    }

    return names;
  }

  static PsiType getNonDetachedVariableType(final PsiVariable variable) {
    if (variable instanceof PsiCompiledElement || variable instanceof PsiEnumConstant) {
      return variable.getType();
    }

    PsiTypeElement typeElement = variable.getTypeElement();
    if (typeElement == null) return variable.getType();
    
    PsiIdentifier nameIdentifier = variable.getNameIdentifier();
    return SharedImplUtil.getType(typeElement, nameIdentifier, null);
  }

  @Nullable
  static PsiType getNonDetachedMethodReturnType(final PsiMethod method) {
    if (method instanceof PsiCompiledElement) {
      return method.getReturnType();
    }
    
    PsiTypeElement typeElement = method.getReturnTypeElement();
    if (typeElement == null) return null;
    PsiParameterList parameterList = method.getParameterList();
    return SharedImplUtil.getType(typeElement, parameterList, null);
  }
}
