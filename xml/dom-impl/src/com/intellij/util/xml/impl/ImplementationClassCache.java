/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.util.xml.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ReflectionCache;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.xml.DomReflectionUtil;
import com.intellij.util.xml.Implementation;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Comparator;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * @author peter
 */
class ImplementationClassCache {
  private static final Comparator<Class> CLASS_COMPARATOR = new Comparator<Class>() {
    public int compare(final Class o1, final Class o2) {
      if (o1.isAssignableFrom(o2)) return 1;
      if (o2.isAssignableFrom(o1)) return -1;
      if (o1.equals(o2)) return 0;
      throw new AssertionError("Incompatible implementation classes: " + o1 + " & " + o2);
    }
  };


  private final MultiMap<String, DomImplementationClassEP> myImplementationClasses = new MultiMap<String, DomImplementationClassEP>();
  private final ConcurrentFactoryMap<Class, Class> myCache = new ConcurrentFactoryMap<Class, Class>() {
      @Nullable
        protected Class create(final Class concreteInterface) {
          final TreeSet<Class> set = new TreeSet<Class>(CLASS_COMPARATOR);
          findImplementationClassDFS(concreteInterface, set);
          if (!set.isEmpty()) {
            return set.first();
          }
          final Implementation implementation = DomReflectionUtil.findAnnotationDFS(concreteInterface, Implementation.class);
          return implementation == null ? null : implementation.value();
        }
    };

  ImplementationClassCache() {
    for (DomImplementationClassEP ep : DomImplementationClassEP.EP_NAME.getExtensions()) {
      myImplementationClasses.putValue(ep.interfaceName, ep);
    }
  }

  private void findImplementationClassDFS(final Class concreteInterface, SortedSet<Class> results) {
    final Collection<DomImplementationClassEP> values = myImplementationClasses.get(concreteInterface.getName());
    for (DomImplementationClassEP value : values) {
      if (value.getInterfaceClass() == concreteInterface) {
        results.add(value.getImplementationClass());
        return;
      }
    }
    for (final Class aClass1 : ReflectionCache.getInterfaces(concreteInterface)) {
      findImplementationClassDFS(aClass1, results);
    }
  }

  public final void registerImplementation(final Class domElementClass, final Class implementationClass,
                                           @Nullable final Disposable parentDisposable) {
    final DomImplementationClassEP ep = new DomImplementationClassEP() {
      @Override
      public Class getInterfaceClass() {
        return domElementClass;
      }

      @Override
      public Class getImplementationClass() {
        return implementationClass;
      }
    };
    myImplementationClasses.putValue(domElementClass.getName(), ep);
    if (parentDisposable != null) {
      Disposer.register(parentDisposable, new Disposable() {
        public void dispose() {
          myImplementationClasses.remove(domElementClass.getName());
        }
      });
    }
    myCache.clear();
  }

  public Class get(Class key) {
    return myCache.get(key);
  }

  public void clear() {
    myCache.clear();
  }

}
