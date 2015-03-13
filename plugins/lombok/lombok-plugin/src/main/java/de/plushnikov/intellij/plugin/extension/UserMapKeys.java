package de.plushnikov.intellij.plugin.extension;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import org.jetbrains.annotations.NotNull;

public class UserMapKeys {

  private static final String LOMBOK_IS_PRESENT_PROPERTY = "lombok.annotation.present";

  public static final Key<Boolean> HAS_LOMBOK_KEY = Key.create(LOMBOK_IS_PRESENT_PROPERTY);

  public static void updateLombokPresent(@NotNull UserDataHolder element, boolean isPresent) {
    element.putUserData(HAS_LOMBOK_KEY, isPresent);
  }

  public static boolean isLombokPossiblePresent(@NotNull UserDataHolder element) {
    Boolean userData = element.getUserData(HAS_LOMBOK_KEY);
    return null == userData || userData;
  }
}
