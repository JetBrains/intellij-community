// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Collectors;
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
  PYTHON312(312);

  /**
   * This value is mostly bound to the compatibility of our debugger and helpers.
   * You're free to gradually drop support of versions not mentioned here if they present too much hassle to maintain.
   */
  public static final List<LanguageLevel> SUPPORTED_LEVELS =
    List.copyOf(
      Stream
        .of(values())
        .filter(v -> v.isAtLeast(PYTHON36) || v == PYTHON27)
        .collect(Collectors.toList())
    );

  private static final LanguageLevel DEFAULT2 = PYTHON27;
  private static final LanguageLevel DEFAULT3 = PYTHON310;

  @ApiStatus.Internal
  public static LanguageLevel FORCE_LANGUAGE_LEVEL = null;

  @NotNull
  public static LanguageLevel getDefault() {
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

  @Nullable
  @Contract("null->null;!null->!null")
  public static LanguageLevel fromPythonVersion(@Nullable String pythonVersion) {
    if (pythonVersion == null) return null;

    if (pythonVersion.startsWith("2")) {
      if (pythonVersion.startsWith("2.4")) {
        return PYTHON24;
      }
      if (pythonVersion.startsWith("2.5")) {
        return PYTHON25;
      }
      if (pythonVersion.startsWith("2.6")) {
        return PYTHON26;
      }
      if (pythonVersion.startsWith("2.7")) {
        return PYTHON27;
      }
      return DEFAULT2;
    }
    if (pythonVersion.startsWith("3")) {
      if (pythonVersion.startsWith("3.0")) {
        return PYTHON30;
      }
      if (pythonVersion.startsWith("3.1.") || pythonVersion.equals("3.1")) {
        return PYTHON31;
      }
      if (pythonVersion.startsWith("3.2")) {
        return PYTHON32;
      }
      if (pythonVersion.startsWith("3.3")) {
        return PYTHON33;
      }
      if (pythonVersion.startsWith("3.4")) {
        return PYTHON34;
      }
      if (pythonVersion.startsWith("3.5")) {
        return PYTHON35;
      }
      if (pythonVersion.startsWith("3.6")) {
        return PYTHON36;
      }
      if (pythonVersion.startsWith("3.7")) {
        return PYTHON37;
      }
      if (pythonVersion.startsWith("3.8")) {
        return PYTHON38;
      }
      if (pythonVersion.startsWith("3.9")) {
        return PYTHON39;
      }
      if (pythonVersion.startsWith("3.10")) {
        return PYTHON310;
      }
      if (pythonVersion.startsWith("3.11")) {
        return PYTHON311;
      }
      if (pythonVersion.startsWith("3.12")) {
        return PYTHON312;
      }
      return DEFAULT3;
    }
    return getDefault();
  }

  @NotNull
  public String toPythonVersion() {
    return getMajorVersion() + "." + getMinorVersion();
  }

  @NotNull
  public static LanguageLevel forElement(@NotNull PsiElement element) {
    return PyPsiFacade.getInstance(element.getProject()).getLanguageLevel(element);
  }

  @NotNull
  public static LanguageLevel getLatest() {
    return ArrayUtil.getLastElement(values());
  }

  @Override
  public String toString() {
    return toPythonVersion();
  }
}
