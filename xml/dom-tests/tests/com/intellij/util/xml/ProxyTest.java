// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.xml.ui.DomUIFactory;
import net.sf.cglib.proxy.AdvancedProxy;
import net.sf.cglib.proxy.InvocationHandler;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ProxyTest {

  @Test
  public void testExtendClass() throws Throwable {
    final List<String> invocations = new ArrayList<>();
    Implementation implementation = AdvancedProxy.createProxy(Implementation.class, new Class[]{Interface3.class}, new InvocationHandler() {
      @Override
      public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        invocations.add(method.getName());
        if (Object.class.equals(method.getDeclaringClass())) {
          return method.invoke(this, args);
        }
        return Implementation.class.getMethod("getField").invoke(proxy);
      }
    }, "239");
    //noinspection ResultOfMethodCallIgnored
    implementation.hashCode();
    implementation.method();
    assertEquals("239", implementation.getFoo());
    implementation.setField("42");
    assertEquals("42", implementation.getBar());
    assertEquals("42", implementation.toString());
    assertEquals(Arrays.asList("hashCode", "getFoo", "getFoo", "getBar"), invocations);

    assertEquals("42", Interface1.class.getMethod("getFoo").invoke(implementation));

    assertEquals("42", Interface3.class.getMethod("bar").invoke(implementation));

    assertEquals("42", Interface1.class.getMethod("foo").invoke(implementation));
    assertEquals("42", Interface2.class.getMethod("foo").invoke(implementation));
    assertEquals("42", Interface2.class.getMethod("foo").invoke(implementation));
    assertEquals("42", Implementation.class.getMethod("foo").invoke(implementation));
  }

  public interface Interface1 {
    Object getFoo();
    Object foo();
  }

  public interface Interface2 extends Interface1 {
    @Override
    CharSequence getFoo();
    @Override
    CharSequence foo();
  }

  public interface Interface3 extends Interface1 {
    @Override
    String foo();
    String bar();
  }

  private abstract static class Implementation implements Interface2 {
    private String myField;

    protected Implementation(final String field) {
      myField = field;
    }

    public String getField() {
      return myField;
    }

    public void setField(final String field) {
      myField = field;
    }

    public void method() {
      getFoo();
    }

    @Override
    public String toString() {
      return myField;
    }

    @Override
    public abstract String getFoo();

    public abstract String getBar();

    @Override
    public abstract String foo();
  }

  @SuppressWarnings("CastToIncompatibleInterface")
  @Test
  public void testAddInterfaces() {
    final BaseImpl proxy = AdvancedProxy.createProxy(BaseImpl.class, BaseIEx.class);
    assertEquals("a", proxy.sayA());
    assertEquals("a", ((BaseI)proxy).sayA());
    assertEquals("a", ((BaseIEx)proxy).sayA());
  }

  public interface BaseI {
    Object sayA();
  }

  public interface BaseIEx extends BaseI {
    @Override
    CharSequence sayA();
  }

  public static abstract class BaseImpl implements BaseI {
    @Override
    public String sayA() {
      return "a";
    }
  }

  public static abstract class AbstractBase implements BaseI {
    @Override
    public abstract String sayA();

    public abstract static class AbstractBaseImpl extends AbstractBase {
    }
  }

  @Test
  public void testCovariantFromInterface() {
    final AbstractBase.AbstractBaseImpl proxy = AdvancedProxy.createProxy(AbstractBase.AbstractBaseImpl.class, ArrayUtil.EMPTY_CLASS_ARRAY,
                                                                          new InvocationHandler() {
                                                                            @Override
                                                                            public Object invoke(Object proxy,
                                                                                                 Method method,
                                                                                                 Object[] args) {
                                                                              return "a";
                                                                            }
                                                                          }, false, ArrayUtilRt.EMPTY_OBJECT_ARRAY);
    assertEquals("a", proxy.sayA());
    assertEquals("a", proxy.sayA());
    assertEquals("a", ((BaseI)proxy).sayA());
  }

  public static class CovariantFromBaseClassTest {
    public interface Intf {
      String sayA();
    }

    public static class Base {
      public Object sayA() {
        return "beeee";
      }
    }

    public abstract static class Impl extends Base implements Intf {
      @Override
      public abstract String sayA();
    }
  }

  @Test
  public void testCovariantFromBaseClass() {
    final CovariantFromBaseClassTest.Impl proxy = AdvancedProxy.createProxy(CovariantFromBaseClassTest.Impl.class,
                                                                            ArrayUtil.EMPTY_CLASS_ARRAY, new InvocationHandler() {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
          return "a";
        }
      }, false, ArrayUtilRt.EMPTY_OBJECT_ARRAY);
    assertEquals("a", proxy.sayA());
    assertEquals("a", ((CovariantFromBaseClassTest.Base)proxy).sayA());
    //noinspection RedundantCast
    assertEquals("a", ((CovariantFromBaseClassTest.Intf)proxy).sayA());
  }

  @Test
  public void testGenericMethodInvocationJava8() throws Throwable {
    ConcreteInterface proxy = AdvancedProxy.createProxy(new InvocationHandler() {
      @Override
      public Object invoke(Object proxy, Method method, Object[] args) {
        return 42;
      }
    }, null, ConcreteInterface.class);
    Method foo = DomUIFactory.findMethod(GenericInterface.class, "foo");
    assert foo != null;
    assertEquals(42, proxy.foo("a"));
    assertEquals(42, foo.invoke(proxy, "a"));
  }

  @Test
  public void testThreadCallbacksAreClearedWhenConstructorThrows() throws Exception {
    // the first proxy is the prototype of the factory, the second one goes through the generated newInstance
    ThrowingConstructorImpl prototype = AdvancedProxy.createProxy(ThrowingConstructorImpl.class);
    ThrowingConstructorImpl.ourThrow = true;
    try {
      assertThrows(ProcessCanceledException.class, () -> AdvancedProxy.createProxy(ThrowingConstructorImpl.class));
    }
    finally {
      ThrowingConstructorImpl.ourThrow = false;
    }

    Field threadCallbacks = prototype.getClass().getDeclaredField("CGLIB$THREAD_CALLBACKS");
    threadCallbacks.setAccessible(true);
    assertNull(((ThreadLocal<?>)threadCallbacks.get(null)).get());
  }

  public abstract static class ThrowingConstructorImpl implements BaseI {
    static volatile boolean ourThrow;

    public ThrowingConstructorImpl() {
      if (ourThrow) {
        throw new ProcessCanceledException();
      }
    }
  }

  interface GenericInterface<T> {
    Object foo(T t);
  }

  interface ConcreteInterface extends GenericInterface<String> {
    @Override
    Object foo(String t);
  }
}
