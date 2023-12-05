//skip compare content

import lombok.experimental.Delegate;

class DelegateOnStatic {
  @Delegate private static final java.lang.Runnable staticField = null;
}

class DelegateOnStaticMethod {
  @Delegate
  private static final java.lang.Runnable staticMethod() {
    return null;
  }

  ;
}