package com.intellij.psi.impl.cache;

import com.intellij.lang.ASTNode;
import com.intellij.lexer.FilterLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiJavaFileImpl;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.impl.source.tree.JavaSharedImplUtil;
import com.intellij.psi.impl.source.tree.StdTokenSets;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ArrayUtil;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.PersistentStringEnumerator;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author max
 */
public class RecordUtil {
  @NonNls private static final String DEPRECATED_ANNOTATION_NAME = "Deprecated";
  @NonNls private static final String DEPRECATED_TAG = "@deprecated";

  private RecordUtil() {}

  private static final Ref<ArrayList<PsiClass>> ourList = new Ref<ArrayList<PsiClass>>();
  private static final String[][] ourEmptyStringStringArray = new String[0][];

  public static List<PsiClass> getInnerClasses(PsiElement psiElement, final CharSequence fileBuffer) {
    ourList.set(null);

    if (psiElement != null && mayContainClassesInside(psiElement, fileBuffer)) {
      psiElement.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override public void visitClass(PsiClass aClass) {
          if (ourList.isNull()) ourList.set(new ArrayList<PsiClass>());
          ourList.get().add(aClass);
        }

        @Override public void visitTypeParameter(PsiTypeParameter classParameter) {
          // just skip (because type parameter is class - bad!)
        }
      });
    }

    return ourList.get();
  }

  private static boolean mayContainClassesInside(PsiElement psiElement, final CharSequence fileBuffer) {
    PsiFile psiFile = psiElement.getContainingFile();

    boolean mayHaveClassesInside = false;
    if (psiFile instanceof PsiJavaFileImpl) {
      PsiJavaFileImpl impl = (PsiJavaFileImpl)psiFile;
      Lexer originalLexer = impl.createLexer();
      FilterLexer lexer = new FilterLexer(originalLexer, new FilterLexer.SetFilter(StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET));
      final TextRange range = psiElement.getTextRange();
      lexer.start(fileBuffer, range.getStartOffset(), range.getEndOffset());
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

  static String[][] createStringStringArray(int size) {
    if (size == 0) return ourEmptyStringStringArray;
    return new String[size][];
  }

  static boolean[] createBooleanArray(int size) {
    if (size == 0) return ArrayUtil.EMPTY_BOOLEAN_ARRAY;
    return new boolean[size];
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

  public static boolean isDeprecatedByAnnotation(PsiElement element) {
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

    return psiModifierList != null ? packModifierList(psiModifierList) : 0;
  }

  public static int packModifierList(final PsiModifierList psiModifierList) {
    int packed = 0;

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
    return packed;
  }

  public static boolean isDeprecatedByDocComment(PsiElement psiElement) {
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
    return psiElement instanceof PsiClass && ((PsiClass)psiElement).isInterface();
  }

  private static boolean isEnum(PsiElement psiElement) {
    return psiElement instanceof PsiClass && ((PsiClass)psiElement).isEnum();
  }

  private static boolean isAnnotationType(PsiElement psiElement) {
    return psiElement instanceof PsiClass && ((PsiClass)psiElement).isAnnotationType();
  }


  private static final TObjectIntHashMap<String> ourModifierNameToFlagMap;

  static {
    ourModifierNameToFlagMap = new TObjectIntHashMap<String>();

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
  }

  public static boolean hasModifierProperty(String psiModifier, int packed) {
    return (ourModifierNameToFlagMap.get(psiModifier) & packed) != 0;
  }

  public static int getLocalId(long id) {
    return (int)(id & 0x0FFFFFFFL);
  }


  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.cache.impl.repositoryCache");


  public static int readID(DataInput record, int prevId) throws IOException {
    return prevId + DataInputOutputUtil.readSINT(record);
  }

  public static int readID(DataInput record) throws IOException {
    int low = record.readUnsignedByte();
    return low + (DataInputOutputUtil.readINT(record) << 8);
  }

  public static void writeID(DataOutput record, int prevId, int id) throws IOException {
    DataInputOutputUtil.writeSINT(record, id - prevId);
  }

  public static void writeID(DataOutput record, int id) throws IOException {
    record.writeByte(id & 0xFF);
    DataInputOutputUtil.writeINT(record, id >>> 8);
  }

  public static PersistentStringEnumerator getNameStoreFile(String name, boolean toDelete, File cacheFolder)
    throws PersistentStringEnumerator.CorruptedException {
    File ioFile = new File(cacheFolder, name);
    if (toDelete) {
      LOG.assertTrue(FileUtil.delete(ioFile), "Cannot delete " + ioFile);
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

}
