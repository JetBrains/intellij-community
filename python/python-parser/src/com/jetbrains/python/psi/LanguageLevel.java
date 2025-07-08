// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtil;
import com.jetbrains.python.PyLanguageFacade;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;


public enum LanguageLevel {

  /**
   * @apiNote This level is not supported since 2018.1.
   */
  PYTHON24(204),
  /**
   * @apiNote This level is not supported since 2018.1.
   */
  PYTHON25(205),
  /**
   * @apiNote This level is not supported since 2019.1.
   */
  PYTHON26(206),
  PYTHON27(207),
  /**
   * @apiNote This level is not supported since 2018.1.
   * Use it only to distinguish Python 2 and Python 3.
   * Consider using {@link LanguageLevel#isPython2()}.
   * Replace {@code level.isOlderThan(PYTHON30)} with {@code level.isPython2()}
   * and {@code level.isAtLeast(PYTHON30)} with {@code !level.isPython2()}.
   */
  PYTHON30(300),
  /**
   * @apiNote This level is not supported since 2018.1.
   */
  PYTHON31(301),
  /**
   * @apiNote This level is not supported since 2018.1.
   */
  PYTHON32(302),
  /**
   * @apiNote This level is not supported since 2018.1.
   */
  PYTHON33(303),
  /**
   * @apiNote This level is not supported since 2019.1.
   */
  PYTHON34(304),
  /**
   * @apiNote This level is not supported since 2020.3.
   */
  PYTHON35(305),
  PYTHON36(306),
  PYTHON37(307),
  PYTHON38(308),
  PYTHON39(309),
  PYTHON310(310),
  PYTHON311(311),
  PYTHON312(312),
  PYTHON313(313),
  PYTHON314(314);

  public static final Comparator<LanguageLevel> VERSION_COMPARATOR = (first, second) -> {
    return first == second ? 0 : first.isOlderThan(second) ? -1 : 1;
  };

  /**
   * This value is mostly bound to the compatibility of our debugger and helpers.
   * You're free to gradually drop support of versions not mentioned here if they present too much hassle to maintain.
   */
  public static final List<LanguageLevel> SUPPORTED_LEVELS =
    Stream
      .of(values())
      .filter(v -> v.isAtLeast(PYTHON36) || v == PYTHON27)
      .toList();

  private static final LanguageLevel DEFAULT2 = PYTHON27;
  private static final LanguageLevel DEFAULT3 = PYTHON310;

  @ApiStatus.Internal
  public static LanguageLevel FORCE_LANGUAGE_LEVEL = null;

  public static @NotNull LanguageLevel getDefault() {
    return getLatest();
  }

  private final int myVersion;

  LanguageLevel(int version) {
    myVersion = version;
  }

  public int getMajorVersion() {
    return myVersion / 100;
  }

  public int getMinorVersion() {
    return myVersion % 100;
  }

  public boolean hasPrintStatement() {
    return isPython2();
  }

  public boolean isPython2() {
    return getMajorVersion() == 2;
  }

  public boolean isPy3K() {
    return getMajorVersion() == 3;
  }

  public boolean isOlderThan(@NotNull LanguageLevel other) {
    return myVersion < other.myVersion;
  }

  public boolean isAtLeast(@NotNull LanguageLevel other) {
    return myVersion >= other.myVersion;
  }

  /**
   * This function returns default value if argument can't be parsed, consider using {@link #fromPythonVersionSafe(String)}
   */
  @Contract("null->null;!null->!null")
  public static @Nullable LanguageLevel fromPythonVersion(@Nullable String pythonVersion) {
    if (pythonVersion == null) return null;
    var version = fromPythonVersionSafe(pythonVersion);
    return (version != null) ? version : getDefault();
  }

  /**
   * Provide version as string (i.e "3.12") to get language level
   *
   * @return language level or null if can't be parsed
   */
  public static @Nullable LanguageLevel fromPythonVersionSafe(@NotNull String pythonVersionOutput) {
    if (pythonVersionOutput.startsWith("2")) {
      if (pythonVersionOutput.startsWith("2.4")) {
        return PYTHON24;
      }
      if (pythonVersionOutput.startsWith("2.5")) {
        return PYTHON25;
      }
      if (pythonVersionOutput.startsWith("2.6")) {
        return PYTHON26;
      }
      if (pythonVersionOutput.startsWith("2.7")) {
        return PYTHON27;
      }
      return DEFAULT2;
    }
    if (pythonVersionOutput.startsWith("3")) {
      if (pythonVersionOutput.startsWith("3.0")) {
        return PYTHON30;
      }
      if (pythonVersionOutput.startsWith("3.1.") || pythonVersionOutput.equals("3.1")) {
        return PYTHON31;
      }
      if (pythonVersionOutput.startsWith("3.2")) {
        return PYTHON32;
      }
      if (pythonVersionOutput.startsWith("3.3")) {
        return PYTHON33;
      }
      if (pythonVersionOutput.startsWith("3.4")) {
        return PYTHON34;
      }
      if (pythonVersionOutput.startsWith("3.5")) {
        return PYTHON35;
      }
      if (pythonVersionOutput.startsWith("3.6")) {
        return PYTHON36;
      }
      if (pythonVersionOutput.startsWith("3.7")) {
        return PYTHON37;
      }
      if (pythonVersionOutput.startsWith("3.8")) {
        return PYTHON38;
      }
      if (pythonVersionOutput.startsWith("3.9")) {
        return PYTHON39;
      }
      if (pythonVersionOutput.startsWith("3.10")) {
        return PYTHON310;
      }
      if (pythonVersionOutput.startsWith("3.11")) {
        return PYTHON311;
      }
      if (pythonVersionOutput.startsWith("3.12")) {
        return PYTHON312;
      }
      if (pythonVersionOutput.startsWith("3.13")) {
        return PYTHON313;
      }
      if (pythonVersionOutput.startsWith("3.14")) {
        return PYTHON314;
      }
      return DEFAULT3;
    }
    return null;
  }

  public @NotNull String toPythonVersion() {
    return getMajorVersion() + "." + getMinorVersion();
  }

  public static @NotNull LanguageLevel forElement(@NotNull PsiElement element) {
    return PyLanguageFacade.getINSTANCE().getEffectiveLanguageLevel(element);
  }

  public static @NotNull LanguageLevel getLatest() {
    return ArrayUtil.getLastElement(values());
  }

  @Override
  public String toString() {
    return toPythonVersion();
  }
}
