/**
 * created at Jan 10, 2002
 * @author Jeka
 */
package com.intellij.compiler.classParsing;

import com.intellij.compiler.SymbolTable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.cls.ClsUtil;
import gnu.trove.TIntHashSet;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;

public class MethodInfo extends MemberInfo {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.classParsing.MethodInfo");

  private static final int[] EMPTY_INT_ARRAY = new int[0];
  private static final int[] EXCEPTION_INFO_UNAVAILABLE = new int[0];
  public static final MethodInfo[] EMPTY_ARRAY = new MethodInfo[0];

  private final int[] myThrownExceptions;
  // cached (lazy initialized) data
  private String mySignature = null;
  private String[] myParameterDescriptors = null;
  private String myReturnTypeSignature = null;
  private final boolean myIsConstructor;
  private final AnnotationConstantValue[][] myRuntimeVisibleParameterAnnotations;
  private final AnnotationConstantValue[][] myRuntimeInvisibleParameterAnnotations;
  private final ConstantValue myAnnotationDefault;

  public MethodInfo(int name, int descriptor, boolean isConstructor) {
    super(name, descriptor);
    myIsConstructor = isConstructor;
    myThrownExceptions = EXCEPTION_INFO_UNAVAILABLE;
    myRuntimeVisibleParameterAnnotations = AnnotationConstantValue.EMPTY_ARRAY_ARRAY;
    myRuntimeInvisibleParameterAnnotations = AnnotationConstantValue.EMPTY_ARRAY_ARRAY;
    myAnnotationDefault = ConstantValue.EMPTY_CONSTANT_VALUE;
  }

  public MethodInfo(int name,
                    int descriptor,
                    final int genericSignature,
                    int flags,
                    int[] exceptions,
                    boolean isConstructor,
                    final AnnotationConstantValue[] runtimeVisibleAnnotations,
                    final AnnotationConstantValue[] runtimeInvisibleAnnotations,
                    final AnnotationConstantValue[][] runtimeVisibleParameterAnnotations,
                    final AnnotationConstantValue[][] runtimeInvisibleParameterAnnotations, ConstantValue annotationDefault) {

    super(name, descriptor, genericSignature, flags, runtimeVisibleAnnotations, runtimeInvisibleAnnotations);
    myThrownExceptions = exceptions != null? exceptions : EMPTY_INT_ARRAY;
    myIsConstructor = isConstructor;
    myRuntimeVisibleParameterAnnotations = runtimeVisibleParameterAnnotations; // todo: pass as parameter
    myRuntimeInvisibleParameterAnnotations = runtimeInvisibleParameterAnnotations;
    myAnnotationDefault = annotationDefault;
  }

  public MethodInfo(DataInput in) throws IOException {
    super(in);
    myIsConstructor = in.readBoolean();
    int count = in.readInt();
    if (count == -1) {
      myThrownExceptions = EXCEPTION_INFO_UNAVAILABLE;
    }
    else if (count == 0) {
      myThrownExceptions = EMPTY_INT_ARRAY;
    }
    else {
      myThrownExceptions = new int[count];
      for (int idx = 0; idx < count; idx++) {
        myThrownExceptions[idx] = in.readInt();
      }
    }
    myRuntimeVisibleParameterAnnotations = loadParameterAnnotations(in);
    myRuntimeInvisibleParameterAnnotations = loadParameterAnnotations(in);
    myAnnotationDefault = MemberInfoExternalizer.loadConstantValue(in);
  }

  public void save(DataOutput out) throws IOException {
    super.save(out);
    out.writeBoolean(myIsConstructor);
    if (isExceptionInfoAvailable()) {
      out.writeInt(myThrownExceptions.length);
    }
    else {
      out.writeInt(-1);
    }
    for (int idx = 0; idx < myThrownExceptions.length; idx++) {
      out.writeInt(myThrownExceptions[idx]);
    }
    saveParameterAnnotations(out, myRuntimeVisibleParameterAnnotations);
    saveParameterAnnotations(out, myRuntimeInvisibleParameterAnnotations);
    MemberInfoExternalizer.saveConstantValue(out, myAnnotationDefault);
  }

  private boolean isExceptionInfoAvailable() {
    return myThrownExceptions != EXCEPTION_INFO_UNAVAILABLE;
  }

  public boolean areExceptionsEqual(MethodInfo info) {
    if (myThrownExceptions.length != info.myThrownExceptions.length) {
      return false;
    }
    if (myThrownExceptions.length != 0) { // optimization
      TIntHashSet exceptionsSet = new TIntHashSet();
      for (int idx = 0; idx < myThrownExceptions.length; idx++) {
        exceptionsSet.add(myThrownExceptions[idx]);
      }
      for (int idx = 0; idx < info.myThrownExceptions.length; idx++) {
        int exception = info.myThrownExceptions[idx];
        if (!exceptionsSet.contains(exception)) {
          return false;
        }
      }
    }
    return true;
  }

  public int[] getThrownExceptions() {
    return myThrownExceptions;
  }

  public String getDescriptor(SymbolTable symbolTable) {
    if (mySignature == null) {
      String descriptor = symbolTable.getSymbol(getDescriptor());
      String name = symbolTable.getSymbol(getName());
      mySignature = name + descriptor.substring(0, descriptor.indexOf(')') + 1);
    }
    return mySignature;
  }

  public String getReturnTypeDescriptor(SymbolTable symbolTable) {
    if (myReturnTypeSignature == null) {
      String descriptor = symbolTable.getSymbol(getDescriptor());
      myReturnTypeSignature = descriptor.substring(descriptor.indexOf(')') + 1, descriptor.length());
    }
    return myReturnTypeSignature;
  }

  public String[] getParameterDescriptors(SymbolTable symbolTable) {
    if (myParameterDescriptors == null) {
      String descriptor = symbolTable.getSymbol(getDescriptor());
      int endIndex = descriptor.indexOf(')');
      if (endIndex <= 0) {
        LOG.assertTrue(false, "Corrupted method descriptor: "+descriptor);
      }
      myParameterDescriptors = parseParameterDescriptors(descriptor.substring(1, endIndex));
    }
    return myParameterDescriptors;
  }

  public boolean isAbstract() {
    return ClsUtil.isAbstract(getFlags());
  }

  public boolean isConstructor() {
    return myIsConstructor;
  }

  private String[] parseParameterDescriptors(String signature) {
    ArrayList list = new ArrayList();
    String paramSignature = parseFieldType(signature);
    while (paramSignature != null && !"".equals(paramSignature)) {
      list.add(paramSignature);
      signature = signature.substring(paramSignature.length());
      paramSignature = parseFieldType(signature);
    }
    return (String[])list.toArray(new String[list.size()]);
  }

  private String parseFieldType(String signature) {
    if (signature.length() == 0) {
      return null;
    }
    if (signature.charAt(0) == 'B') {
      return "B";
    }
    if (signature.charAt(0) == 'C') {
      return "C";
    }
    if (signature.charAt(0) == 'D') {
      return "D";
    }
    if (signature.charAt(0) == 'F') {
      return "F";
    }
    if (signature.charAt(0) == 'I') {
      return "I";
    }
    if (signature.charAt(0) == 'J') {
      return "J";
    }
    if (signature.charAt(0) == 'S') {
      return "S";
    }
    if (signature.charAt(0) == 'Z') {
      return "Z";
    }
    if (signature.charAt(0) == 'L') {
      return signature.substring(0, signature.indexOf(";") + 1);
    }
    if (signature.charAt(0) == '[') {
      String s = parseFieldType(signature.substring(1));
      return (s != null)? ("[" + s) : null;
    }
    return null;
  }

  public AnnotationConstantValue[][] getRuntimeVisibleParameterAnnotations() {
    return myRuntimeVisibleParameterAnnotations;
  }

  public AnnotationConstantValue[][] getRuntimeInvisibleParameterAnnotations() {
    return myRuntimeInvisibleParameterAnnotations;
  }

  public String toString() {
    return mySignature;
  }

  private AnnotationConstantValue[][] loadParameterAnnotations(DataInput in) throws IOException {
    final int size = in.readInt();
    if (size == 0) {
      return AnnotationConstantValue.EMPTY_ARRAY_ARRAY;
    }
    final AnnotationConstantValue[][] paramAnnotations = new AnnotationConstantValue[size][];
    for (int idx = 0; idx < size; idx++) {
      paramAnnotations[idx] = loadAnnotations(in);
    }
    return paramAnnotations;
  }

  private void saveParameterAnnotations(DataOutput out, AnnotationConstantValue[][] parameterAnnotations) throws IOException {
    out.writeInt(parameterAnnotations.length);
    for (int idx = 0; idx < parameterAnnotations.length; idx++) {
      saveAnnotations(out, parameterAnnotations[idx]);
    }
  }

  public ConstantValue getAnnotationDefault() {
    return myAnnotationDefault;
  }

}
