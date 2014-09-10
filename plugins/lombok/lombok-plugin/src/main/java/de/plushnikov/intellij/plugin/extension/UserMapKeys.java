package de.plushnikov.intellij.plugin.extension;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

public class UserMapKeys {

  private static final String LOMBOK_HAS_IMPLICIT_USAGE_PROPERTY = "lombok.hasImplicitUsage";
  private static final String LOMBOK_HAS_IMPLICIT_READ_PROPERTY = "lombok.hasImplicitRead";
  private static final String LOMBOK_HAS_IMPLICIT_WRITE_PROPERTY = "lombok.hasImplicitWrite";

  private static final String LOMBOK_IS_PRESENT_PROPERTY = "lombok.annotation.present";

  public static final Key<Boolean> USAGE_KEY = Key.create(LOMBOK_HAS_IMPLICIT_USAGE_PROPERTY);
  public static final Key<Boolean> READ_KEY = Key.create(LOMBOK_HAS_IMPLICIT_READ_PROPERTY);
  public static final Key<Boolean> WRITE_KEY = Key.create(LOMBOK_HAS_IMPLICIT_WRITE_PROPERTY);

  public static final Key<LombokPresentData> HAS_LOMBOK_KEY = Key.create(LOMBOK_IS_PRESENT_PROPERTY);

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

  private static final long PRESENT_TIME = TimeUnit.SECONDS.toNanos(10);

  private static class LombokPresentData {
    private final boolean present;
    private final long nanoTime;

    private LombokPresentData(boolean present) {
      this.present = present;
      this.nanoTime = System.nanoTime();
    }
  }

  public static void updateLombokPresent(@NotNull UserDataHolder element, boolean isPresent) {
    element.putUserData(HAS_LOMBOK_KEY, new LombokPresentData(isPresent));
  }

  public static boolean isLombokPossiblePresent(@NotNull UserDataHolder element) {
    LombokPresentData userData = element.getUserData(HAS_LOMBOK_KEY);
    return null == userData || (userData.nanoTime < System.nanoTime() - PRESENT_TIME) || userData.present;
  }
}
