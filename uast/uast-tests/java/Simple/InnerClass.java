class AccessingProtectedMembersFromSubclass extends ProtectedMembers {

  public static class StaticInnerImpl1 extends ProtectedMembers.StaticInner {
  }

  public static class StaticInnerImpl2 extends StaticInner {
  }
}

class ProtectedMembers {
  protected void method() {
  }

  static protected void staticMethod() {
  }

  protected static class StaticInner {
  }
}