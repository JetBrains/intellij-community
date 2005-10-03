package com.intellij.compiler.classParsing;

import com.intellij.util.ArrayUtil;
import com.intellij.openapi.compiler.CompilerBundle;

import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 4, 2004
 */
public class GenericMethodSignature {
  private final String myFormalTypeParams;
  private final String[] myParamSignatures;
  private final String myReturnTypeSignature;
  private final String myThrowsSignature;

  private GenericMethodSignature(String formalTypeParams, String[] paramSignatures, String returnTypeSignature, String throwsSignature) {
    myFormalTypeParams = formalTypeParams;
    myParamSignatures = paramSignatures;
    myReturnTypeSignature = returnTypeSignature;
    myThrowsSignature = throwsSignature;
  }

  public String getFormalTypeParams() {
    return myFormalTypeParams;
  }

  public String[] getParamSignatures() {
    return myParamSignatures;
  }

  public String getReturnTypeSignature() {
    return myReturnTypeSignature;
  }

  public String getThrowsSignature() {
    return myThrowsSignature;
  }

  public static GenericMethodSignature parse(String methodSignature) throws SignatureParsingException {
    final StringCharacterIterator it = new StringCharacterIterator(methodSignature);

    final StringBuffer formals = new StringBuffer();
    if (it.current() == '<') {
      SignatureParser.INSTANCE.parseFormalTypeParameters(it, formals);
    }

    if (it.current() != '(') {
      throw new SignatureParsingException(CompilerBundle.message("error.signature.parsing.expected.other.symbol", "(", formals.toString()));
    }

    it.next(); // skip '('

    final String[] paramSignatures;
    if (it.current() != ')') {
      final List<String> params = new ArrayList<String>();
      while (it.current() != ')') {
        final StringBuffer typeSignature = new StringBuffer();
        SignatureParser.INSTANCE.parseTypeSignature(it, typeSignature);
        params.add(typeSignature.toString());
      }
      paramSignatures = params.toArray(new String[params.size()]);
    }
    else {
      paramSignatures = ArrayUtil.EMPTY_STRING_ARRAY;
    }
    it.next(); // skip ')'

    final StringBuffer returnTypeSignature = new StringBuffer();
    SignatureParser.INSTANCE.parseReturnType(it, returnTypeSignature);

    final StringBuffer throwsSignature = new StringBuffer();
    if (it.current() != CharacterIterator.DONE) {
      SignatureParser.INSTANCE.parseThrowsSignature(it, throwsSignature);
    }
    
    return new GenericMethodSignature(formals.toString(), paramSignatures, returnTypeSignature.toString(), throwsSignature.toString());
  }
}
