package de.plushnikov.intellij.lombok;

import com.intellij.openapi.util.Key;

public class UserMapKeys {

  private static final String LOMBOK_HAS_IMPLICIT_USAGE_PROPERTY = "lombok.hasImplicitUsage";
  private static final String LOMBOK_HAS_IMPLICIT_READ_PROPERTY = "lombok.hasImplicitRead";
  private static final String LOMBOK_HAS_IMPLICIT_WRITE_PROPERTY = "lombok.hasImplicitWrite";

  public static final Key<Boolean> USAGE_KEY = Key.create(LOMBOK_HAS_IMPLICIT_USAGE_PROPERTY);
  public static final Key<Boolean> READ_KEY = Key.create(LOMBOK_HAS_IMPLICIT_READ_PROPERTY);
  public static final Key<Boolean> WRITE_KEY = Key.create(LOMBOK_HAS_IMPLICIT_WRITE_PROPERTY);

}
