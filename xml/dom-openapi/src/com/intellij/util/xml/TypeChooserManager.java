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

import com.intellij.psi.xml.XmlTag;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author peter
 */
public class TypeChooserManager {
  private final Map<Type, TypeChooser> myClassChoosers = new ConcurrentHashMap<>();

  public TypeChooser getTypeChooser(final Type type) {
    final TypeChooser typeChooser = myClassChoosers.get(type);
    return typeChooser != null ? typeChooser : new TypeChooser() {
      @Override
      public Type chooseType(final XmlTag tag) {
        return type;
      }

      @Override
      public void distinguishTag(final XmlTag tag, final Type aClass) {
      }

      @Override
      public Type[] getChooserTypes() {
        return new Type[]{type};
      }
    };
  }

  public void registerTypeChooser(final Type aClass, final TypeChooser typeChooser) {
    myClassChoosers.put(aClass, typeChooser);
  }

  public void unregisterTypeChooser(Type aClass) {
    myClassChoosers.remove(aClass);
  }

  public final void copyFrom(TypeChooserManager manager) {
    myClassChoosers.putAll(manager.myClassChoosers);
  }
}
