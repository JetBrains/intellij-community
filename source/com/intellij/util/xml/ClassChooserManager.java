/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.psi.xml.XmlTag;

import java.util.Map;
import java.util.HashMap;
import java.lang.reflect.Type;

/**
 * @author peter
 */
public class ClassChooserManager {
  private static final Map<Class, ClassChooser> ourClassChoosers = new HashMap<Class, ClassChooser>();

  public static final ClassChooser getClassChooser(final Type type) {
    final Class aClass = DomUtil.getRawType(type);
    final ClassChooser classChooser = ourClassChoosers.get(aClass);
    return classChooser != null ? classChooser : new ClassChooser() {
      public Class chooseClass(final XmlTag tag) {
        return aClass;
      }

      public void distinguishTag(final XmlTag tag, final Class aClass) {
      }
    };
  }

  public static final <T extends DomElement> void registerClassChooser(final Class<T> aClass, final ClassChooser<T> classChooser) {
    ourClassChoosers.put(aClass, classChooser);
  }

  public static final <T extends DomElement> void unregisterClassChooser(Class<T> aClass) {
    ourClassChoosers.remove(aClass);
  }
}
