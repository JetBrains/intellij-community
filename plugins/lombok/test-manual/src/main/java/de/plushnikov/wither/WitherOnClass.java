package de.plushnikov.wither;

@lombok.experimental.Wither
class WitherOnClass1 {
  @lombok.experimental.Wither(lombok.AccessLevel.NONE)
  boolean isNone;

  boolean isPublic;

  WitherOnClass1(boolean isNone, boolean isPublic) {
  }

  public static void main(String[] args) {
    new WitherOnClass1(true, true).withPublic(true);
  }
}

@lombok.experimental.Wither(lombok.AccessLevel.PROTECTED)
class WitherOnClass2 {
  @lombok.experimental.Wither(lombok.AccessLevel.NONE)
  boolean isNone;

  boolean isProtected;

  @lombok.experimental.Wither(lombok.AccessLevel.PACKAGE)
  boolean isPackage;

  WitherOnClass2(boolean isNone, boolean isProtected, boolean isPackage) {
  }

  public static void main(String[] args) {
    new WitherOnClass2(false, false, false).withPackage(true).withProtected(false);
  }
}

@lombok.experimental.Wither
class WitherOnClass3 {
  String couldBeNull;

  @lombok.NonNull
  String nonNull;

  WitherOnClass3(String couldBeNull, String nonNull) {
  }

  public static void main(String[] args) {
    new WitherOnClass3("", "").withCouldBeNull("").withNonNull("");
  }
}

@lombok.experimental.Wither
@lombok.experimental.Accessors(prefix = "f")
class WitherOnClass4 {
  final int fX = 10;

  final int fY;

  WitherOnClass4(int y) {
    this.fY = y;
  }

  public static void main(String[] args) {
    new WitherOnClass4(1).withY(2);
  }
}
