/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs.impl;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.impl.java.stubs.JavaClassElementType;
import com.intellij.psi.impl.java.stubs.PsiClassStub;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;

public class PsiClassStubImpl extends StubBase<PsiClass> implements PsiClassStub {
  private final String myQualifiedName;
  private final String myName;
  private final String myBaseRefText;
  private final byte myFlags;

  private final static int DEPRECATED = 0x01;
  private final static int INTERFACE = 0x02;
  private final static int ENUM = 0x04;
  private final static int ENUM_CONSTANT_INITIALIZER = 0x08;
  private final static int ANONYMOUS = 0x10;
  private final static int ANON_TYPE = 0x20;
  private final static int IN_QUALIFIED_NEW = 0x40;
  private LanguageLevel myLanguageLevel = null;
  private String mySourceFileName = null;

  public PsiClassStubImpl(final JavaClassElementType type,
                          final StubElement parent,
                          final String qualifiedName,
                          final String name,
                          final String baseRefText,
                          final byte flags) {
    super(parent, type);
    myQualifiedName = qualifiedName;
    myName = name;
    myBaseRefText = baseRefText;
    myFlags = flags;
  }

  public String getName() {
    return myName;
  }

  public String getQualifiedName() {
    return myQualifiedName;
  }

  public String getBaseClassReferenceText() {
    return myBaseRefText;
  }

  public boolean isDeprecated() {
    return (myFlags & DEPRECATED) != 0;
  }

  public boolean isInterface() {
    return (myFlags & INTERFACE) != 0;
  }

  public boolean isEnum() {
    return (myFlags & ENUM) != 0;
  }

  public boolean isEnumConstantInitializer() {
    return isEnumConstInitializer(myFlags);
  }

  public static boolean isEnumConstInitializer(final byte flags) {
    return (flags & ENUM_CONSTANT_INITIALIZER) != 0;
  }

  public boolean isAnonymous() {
    return isAnonymous(myFlags);
  }

  public static boolean isAnonymous(final byte flags) {
    return (flags & ANONYMOUS) != 0;
  }

  public boolean isAnnotationType() {
    return (myFlags & ANON_TYPE) != 0;
  }

  public LanguageLevel getLanguageLevel() {
    return myLanguageLevel;
  }

  public String getSourceFileName() {
    return mySourceFileName;
  }

  public void setLanguageLevel(final LanguageLevel languageLevel) {
    myLanguageLevel = languageLevel;
  }

  public void setSourceFileName(final String sourceFileName) {
    mySourceFileName = sourceFileName;
  }

  public boolean isAnonymousInQualifiedNew() {
    return (myFlags & IN_QUALIFIED_NEW) != 0;
  }

  public byte getFlags() {
    return myFlags;
  }

  public static byte packFlags(boolean isDeprecated,
                               boolean isInterface,
                               boolean isEnum,
                               boolean isEnumConstantInitializer,
                               boolean isAnonymous,
                               boolean isAnnotationType,
                               boolean isInQualifiedNew) {
    byte flags = 0;
    if (isDeprecated) flags |= DEPRECATED;
    if (isInterface) flags |= INTERFACE;
    if (isEnum) flags |= ENUM;
    if (isEnumConstantInitializer) flags |= ENUM_CONSTANT_INITIALIZER;
    if (isAnonymous) flags |= ANONYMOUS;
    if (isAnnotationType) flags |= ANON_TYPE;
    if (isInQualifiedNew) flags |= IN_QUALIFIED_NEW;
    return flags;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.
        append("PsiClassStub[");

    if (isInterface()) {
      builder.append("interface ");
    }

    if (isAnonymous()) {
      builder.append("anonymous ");
    }

    if (isEnum()) {
      builder.append("enum ");
    }

    if (isAnnotationType()) {
      builder.append("annotation ");
    }

    if (isEnumConstantInitializer()) {
      builder.append("enumInit ");
    }

    if (isDeprecated()) {
      builder.append("deprecated ");
    }

    builder.
        append("name=").append(getName()).
        append(" fqn=").append(getQualifiedName());

    if (getBaseClassReferenceText() != null) {
      builder.append(" baseref=").append(getBaseClassReferenceText());
    }


    if (isAnonymousInQualifiedNew()) {
      builder.append(" inqualifnew");
    }

    builder.append("]");

    return builder.toString();
  }
}