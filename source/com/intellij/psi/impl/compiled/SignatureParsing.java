/*
 * @author max
 */
package com.intellij.psi.impl.compiled;

import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.impl.java.stubs.PsiTypeParameterListStub;
import com.intellij.psi.impl.java.stubs.PsiTypeParameterStub;
import com.intellij.psi.impl.java.stubs.impl.PsiClassReferenceListStubImpl;
import com.intellij.psi.impl.java.stubs.impl.PsiTypeParameterListStubImpl;
import com.intellij.psi.impl.java.stubs.impl.PsiTypeParameterStubImpl;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.cls.ClsFormatException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.text.CharacterIterator;
import java.util.ArrayList;

public class SignatureParsing {
  private SignatureParsing() {
  }

  public static PsiTypeParameterListStub parseTypeParametersDeclaration(CharacterIterator signatureIterator, StubElement parentStub)
      throws ClsFormatException {
    PsiTypeParameterListStub list = new PsiTypeParameterListStubImpl(parentStub);
    if (signatureIterator.current() == '<') {
      signatureIterator.next();
      while (signatureIterator.current() != '>') {
        parseTypeParameter(signatureIterator, list);
      }
      signatureIterator.next();
    }

    return list;
  }

  private static PsiTypeParameterStub parseTypeParameter(CharacterIterator singatureIterator, PsiTypeParameterListStub parent)
      throws ClsFormatException {
    StringBuffer name = new StringBuffer();
    while (singatureIterator.current() != ':' && singatureIterator.current() != CharacterIterator.DONE) {
      name.append(singatureIterator.current());
      singatureIterator.next();
    }
    if (singatureIterator.current() == CharacterIterator.DONE) {
      throw new ClsFormatException();
    }

    PsiTypeParameterStub parameterStub = new PsiTypeParameterStubImpl(parent, name.toString());

    ArrayList<String> bounds = new ArrayList<String>();
    while (singatureIterator.current() == ':') {
      singatureIterator.next();
      String bound = parseToplevelClassRefSignature(singatureIterator);
      if (bound != null && !bound.equals("java.lang.Object")) {
        bounds.add(bound);
      }
    }

    if (!bounds.isEmpty()) {
      String[] sbounds = bounds.toArray(new String[bounds.size()]);
      new PsiClassReferenceListStubImpl(parameterStub, sbounds, PsiReferenceList.Role.EXTENDS_LIST);
    }

    return parameterStub;
  }

  @Nullable
  public static String parseToplevelClassRefSignature(CharacterIterator signature) throws ClsFormatException {
    if (signature.current() == 'L') {
      return parseParameterizedClassRefSignature(signature);
    }
    if (signature.current() == 'T') {
      return parseTypeVariableRefSignature(signature);
    }
    return null;
  }

  private static String parseTypeVariableRefSignature(CharacterIterator signature) {
    signature.next();
    StringBuffer id = new StringBuffer();
    while (signature.current() != ';' && signature.current() != '>') {
      id.append(signature.current());
      signature.next();
    }

    if (signature.current() == ';') {
      signature.next();
    }

    return id.toString();
  }

  private static String parseParameterizedClassRefSignature(CharacterIterator signature) throws ClsFormatException {
    assert signature.current() == 'L';

    signature.next();
    StringBuffer canonicalText = new StringBuffer();
    while (signature.current() != ';' && signature.current() != CharacterIterator.DONE) {
      switch (signature.current()) {
        case '$':
        case '/':
        case '.':
          canonicalText.append('.');
          break;
        case '<':
          canonicalText.append('<');
          signature.next();
          do {
            processTypeArgument(signature, canonicalText);
          }
          while (signature.current() != '>');
          canonicalText.append('>');
          break;
        case ' ':
          break;
        default:
          canonicalText.append(signature.current());
      }
      signature.next();
    }

    if (signature.current() == CharacterIterator.DONE) {
      throw new ClsFormatException();
    }

    for (int index = 0; index < canonicalText.length(); index++) {
      final char c = canonicalText.charAt(index);
      if ('0' <= c && c <= '1') {
        if (index > 0 && canonicalText.charAt(index - 1) == '.') {
          canonicalText.setCharAt(index - 1, '$');
        }
      }
    }

    signature.next();

    return canonicalText.toString();
  }

  private static void processTypeArgument(CharacterIterator signature, StringBuffer canonicalText) throws ClsFormatException {
    String typeArgument = parseClassOrTypeVariableElement(signature);
    canonicalText.append(typeArgument);
    if (signature.current() != '>') {
      canonicalText.append(',');
    }
  }

  public static String parseClassOrTypeVariableElement(CharacterIterator signature) throws ClsFormatException {
    char variance = parseVariance(signature);
    int arrayCount = 0;
    while (signature.current() == '[') {
      arrayCount++;
      signature.next();
    }
    if (signature.current() == 'T' || signature.current() == 'L') {
      String ref = signature.current() == 'T' ? parseTypeVariableRefSignature(signature) : parseParameterizedClassRefSignature(signature);
      while (arrayCount > 0) {
        ref += "[]";
        arrayCount--;
      }
      return decorateTypeText(ref, variance);
    }
    else if (variance == '*') {
      return decorateTypeText("*", variance);
    }
    else {
      throw new ClsFormatException();
    }
  }

  private static final char VARIANCE_NONE = '\0';
  private static final char VARIANCE_EXTENDS = '+';
  private static final char VARIANCE_SUPER = '-';
  private static final char VARIANCE_INVARIANT = '*';
  @NonNls private static final String VARIANCE_EXTENDS_PREFIX = "? extends ";
  @NonNls private static final String VARIANCE_SUPER_PREFIX = "? super ";

  private static String decorateTypeText(final String canonical, final char variance) {
    switch (variance) {
      case VARIANCE_NONE:
        return canonical;
      case VARIANCE_EXTENDS:
        return VARIANCE_EXTENDS_PREFIX + canonical;
      case VARIANCE_SUPER:
        return VARIANCE_SUPER_PREFIX + canonical;
      case VARIANCE_INVARIANT:
        return "?";
      default:
        assert false : "unknown variance";
        return null;
    }
  }

  private static char parseVariance(CharacterIterator signature) {
    char variance;
    switch (signature.current()) {
      case '+':
      case '-':
      case '*':
        variance = signature.current();
        signature.next();
        break;
      case '.':
      case '=':
        signature.next();
        // fall thru
      default:
        variance = '\0';
    }

    return variance;
  }

  public static String parseTypeString(CharacterIterator signature) throws ClsFormatException {
    @NonNls String text;
    int arrayDimensions = 0;
    while (signature.current() == '[') {
      arrayDimensions++;
      signature.next();
    }

    char variance = parseVariance(signature);
    switch (signature.current()) {

      case 'L':
        text = parseParameterizedClassRefSignature(signature);
        break;

      case 'T':
        text = parseTypeVariableRefSignature(signature);
        break;

      case 'B':
        text = "byte";
        signature.next();
        break;

      case 'C':
        text = "char";
        signature.next();
        break;

      case 'D':
        text = "double";
        signature.next();
        break;

      case 'F':
        text = "float";
        signature.next();
        break;

      case 'I':
        text = "int";
        signature.next();
        break;

      case 'J':
        text = "long";
        signature.next();
        break;

      case 'S':
        text = "short";
        signature.next();
        break;

      case 'Z':
        text = "boolean";
        signature.next();
        break;

      case 'V':
        text = "void";
        signature.next();
        break;

      default:
        throw new ClsFormatException();
    }

    for (int i = 0; i < arrayDimensions; i++) text += "[]";
    if (variance != '\0') {
      text = variance + text;
    }
    return text;
  }
}