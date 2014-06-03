/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.util.xml;

import com.intellij.openapi.util.Comparing;
import com.intellij.util.Function;
import com.intellij.util.ReflectionUtil;

/**
 * @author peter
 */
public class NamedEnumUtil {
  private static final Function<Enum, String> NAMED_SHOW = new Function<Enum, String>() {
    @Override
    public String fun(final Enum s) {
      return ((NamedEnum) s).getValue();
    }
  };
  private static final Function<Enum, String> SIMPLE_SHOW = new Function<Enum, String>() {
    @Override
    public String fun(final Enum s) {
      return s.name();
    }
  };
  
  public static <T extends Enum> T getEnumElementByValue(final Class<T> enumClass, final String value, Function<Enum, String> show) {
    for (final T t : enumClass.getEnumConstants()) {
      if (Comparing.equal(value, show.fun(t))) {
        return t;
      }
    }
    return null;
  }
  public static <T extends Enum> T getEnumElementByValue(final Class<T> enumClass, final String value) {
    return getEnumElementByValue(enumClass, value, getShow(enumClass));
  }

  private static <T extends Enum> Function<Enum, String> getShow(final Class<T> enumClass) {
    return ReflectionUtil.isAssignable(NamedEnum.class, enumClass) ? NAMED_SHOW : SIMPLE_SHOW;
  }

  public static <T extends Enum> String getEnumValueByElement(final T element) {
    return element == null ? null : getShow(element.getClass()).fun(element);
  }

}
