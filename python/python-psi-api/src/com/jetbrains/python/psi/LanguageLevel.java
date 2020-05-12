/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.psi;

import com.google.common.collect.ImmutableList;
import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author yole
 */
public enum LanguageLevel {

  /**
   * @deprecated This level is not supported since 2018.1.
   */
  @Deprecated
  PYTHON24(24, false, true, false, false),
  /**
   * @deprecated This level is not supported since 2018.1.
   */
  @Deprecated
  PYTHON25(25, false, true, false, false),
  /**
   * @apiNote This level is not supported since 2019.1.
   */
  PYTHON26(26, true, true, false, false),
  PYTHON27(27, true, true, true, false),
  /**
   * @deprecated This level is not supported since 2018.1.
   * Use it only to distinguish Python 2 and Python 3.
   * Consider using {@link LanguageLevel#isPython2()}.
   * Replace {@code level.isOlderThan(PYTHON30)} with {@code level.isPython2()}
   * and {@code level.isAtLeast(PYTHON30)} with {@code !level.isPython2()}.
   */
  @Deprecated
  PYTHON30(30, true, false, false, true),
  /**
   * @deprecated This level is not supported since 2018.1.
   */
  @Deprecated
  PYTHON31(31, true, false, true, true),
  /**
   * @deprecated This level is not supported since 2018.1.
   */
  @Deprecated
  PYTHON32(32, true, false, true, true),
  /**
   * @deprecated This level is not supported since 2018.1.
   */
  @Deprecated
  PYTHON33(33, true, false, true, true),
  /**
   * @apiNote This level is not supported since 2019.1.
   */
  PYTHON34(34, true, false, true, true),
  PYTHON35(35, true, false, true, true),
  PYTHON36(36, true, false, true, true),
  PYTHON37(37, true, false, true, true),
  PYTHON38(38, true, false, true, true),
  PYTHON39(39, true, false, true, true);

  /**
   * This value is mostly bound to the compatibility of our debugger and helpers.
   * You're free to gradually drop support of versions not mentioned here if they present too much hassle to maintain.
   */
  public static final List<LanguageLevel> SUPPORTED_LEVELS =
    ImmutableList.copyOf(
      Stream
        .of(values())
        .filter(v -> v.myVersion > 34 || v.myVersion == 27)
        .collect(Collectors.toList())
    );

  private static final LanguageLevel DEFAULT2 = PYTHON27;
  private static final LanguageLevel DEFAULT3 = PYTHON39;

  @ApiStatus.Internal
  public static LanguageLevel FORCE_LANGUAGE_LEVEL = null;

  @NotNull
  public static LanguageLevel getDefault() {
    return getLatest();
  }

  private final int myVersion;

  private final boolean myHasWithStatement;
  private final boolean myHasPrintStatement;
  private final boolean mySupportsSetLiterals;
  private final boolean myIsPy3K;

  LanguageLevel(int version, boolean hasWithStatement, boolean hasPrintStatement, boolean supportsSetLiterals, boolean isPy3K) {
    myVersion = version;
    myHasWithStatement = hasWithStatement;
    myHasPrintStatement = hasPrintStatement;
    mySupportsSetLiterals = supportsSetLiterals;
    myIsPy3K = isPy3K;
  }

  /**
   * @return an int where major and minor version are represented decimally: "version 2.5" is 25.
   */
  public int getVersion() {
    return myVersion;
  }

  public boolean hasWithStatement() {
    return myHasWithStatement;
  }

  public boolean hasPrintStatement() {
    return myHasPrintStatement;
  }

  public boolean supportsSetLiterals() {
    return mySupportsSetLiterals;
  }

  public boolean isPython2() {
    return !myIsPy3K;
  }

  public boolean isPy3K() {
    return myIsPy3K;
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
      if (pythonVersion.startsWith("3.1")) {
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
      return DEFAULT3;
    }
    return getDefault();
  }

  @Nullable
  @Contract("null->null;!null->!null")
  public static String toPythonVersion(@Nullable LanguageLevel level) {
    if (level == null) return null;
    final int version = level.getVersion();
    return version / 10 + "." + version % 10;
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
    return toPythonVersion(this);
  }
}
