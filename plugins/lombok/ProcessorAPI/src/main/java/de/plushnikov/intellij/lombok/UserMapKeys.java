package de.plushnikov.intellij.lombok;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class UserMapKeys {

  private static final String LOMBOK_HAS_IMPLICIT_USAGE_PROPERTY = "lombok.hasImplicitUsage";
  private static final String LOMBOK_HAS_IMPLICIT_READ_PROPERTY = "lombok.hasImplicitRead";
  private static final String LOMBOK_HAS_IMPLICIT_WRITE_PROPERTY = "lombok.hasImplicitWrite";

  public static final Key<Boolean> USAGE_KEY = Key.create(LOMBOK_HAS_IMPLICIT_USAGE_PROPERTY);
  public static final Key<Boolean> READ_KEY = Key.create(LOMBOK_HAS_IMPLICIT_READ_PROPERTY);
  public static final Key<Boolean> WRITE_KEY = Key.create(LOMBOK_HAS_IMPLICIT_WRITE_PROPERTY);

  public static void removeAllUsagesFrom(@NotNull UserDataHolder element) {
    if (null != element.getUserData(READ_KEY)) {
      element.putUserData(READ_KEY, null);
    }
    if (null != element.getUserData(WRITE_KEY)) {
      element.putUserData(WRITE_KEY, null);
    }
    if (null != element.getUserData(USAGE_KEY)) {
      element.putUserData(USAGE_KEY, null);
    }
  }

  public static void addGeneralUsageFor(@NotNull UserDataHolder element) {
    element.putUserData(USAGE_KEY, Boolean.TRUE);
  }

  public static void addReadUsageFor(@NotNull UserDataHolder element) {
    element.putUserData(READ_KEY, Boolean.TRUE);
  }

  public static void addWriteUsageFor(@NotNull UserDataHolder element) {
    element.putUserData(WRITE_KEY, Boolean.TRUE);
  }

  public static void addReadUsageFor(@NotNull Collection<? extends UserDataHolder> elements) {
    for (UserDataHolder element : elements) {
      addReadUsageFor(element);
    }
  }
}
