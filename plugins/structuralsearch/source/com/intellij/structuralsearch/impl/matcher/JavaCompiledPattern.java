package com.intellij.structuralsearch.impl.matcher;

import com.intellij.openapi.util.Key;

/**
* @author Eugene.Kudelevsky
*/
public class JavaCompiledPattern extends CompiledPattern {
  private static final String TYPED_VAR_PREFIX = "__$_";

  private boolean requestsSuperFields;
  private boolean requestsSuperMethods;
  private boolean requestsSuperInners;

  public String[] getTypedVarPrefixes() {
    return new String[] {TYPED_VAR_PREFIX};
  }

  public boolean isTypedVar(final String str) {
    if (str.charAt(0)=='@') {
      return str.regionMatches(1,TYPED_VAR_PREFIX,0,TYPED_VAR_PREFIX.length());
    } else {
      return str.startsWith(TYPED_VAR_PREFIX);
    }
  }

  public static final Key<String> ALL_CLASS_CONTENT_VAR_NAME_KEY = Key.create("AllClassContent");
  public static final String ALL_CLASS_UNMATCHED_CONTENT_VAR_ARTIFICIAL_NAME = "__class_unmatched__";

  public boolean isRequestsSuperFields() {
    return requestsSuperFields;
  }

  public void setRequestsSuperFields(boolean requestsSuperFields) {
    this.requestsSuperFields = requestsSuperFields;
  }

  public boolean isRequestsSuperInners() {
    return requestsSuperInners;
  }

  public void setRequestsSuperInners(boolean requestsSuperInners) {
    this.requestsSuperInners = requestsSuperInners;
  }

  public boolean isRequestsSuperMethods() {
    return requestsSuperMethods;
  }

  public void setRequestsSuperMethods(boolean requestsSuperMethods) {
    this.requestsSuperMethods = requestsSuperMethods;
  }
}
