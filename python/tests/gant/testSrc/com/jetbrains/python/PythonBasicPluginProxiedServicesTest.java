// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python;

import com.intellij.serviceContainer.ContainerUtilKt;
import com.intellij.testFramework.junit5.TestApplication;
import com.jetbrains.python.allure.Layers;
import com.jetbrains.python.allure.Subsystems;
import com.jetbrains.python.ast.PyAstFunction;
import com.jetbrains.python.ast.impl.PyAstPsiElementFactory;
import com.jetbrains.python.debugger.PyEmptySignatureCacheManager;
import com.jetbrains.python.debugger.PySignature;
import com.jetbrains.python.debugger.PySignatureCacheManager;
import kotlin.LazyKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JetBrains Light moves between the light and the smart mode without a restart, and {@code PythonCore} then overrides the open defaults
 * of the basic Python modules. A caller can keep a default from the light mode. A proxy forwards that default to the override.
 * The checks make sure that each default is in {@code proxied-services.list} and that the generator can make a proxy for each key.
 * The test is in Java, because the proxy generator and the default classes are Kotlin {@code internal} declarations of other modules.
 */
@TestApplication
@Subsystems.IDE
@Layers.Functional
@SuppressWarnings("KotlinInternalInJava")
public class PythonBasicPluginProxiedServicesTest {
  /** The open defaults of the basic Python modules that {@code PythonCore} overrides. */
  private static final List<String> OVERRIDDEN_DEFAULT_KEYS = List.of(
    "com.jetbrains.python.PyLanguageFacade",
    "com.jetbrains.python.PyPsiElementFactory",
    "com.jetbrains.python.debugger.PySignatureCacheManager"
  );

  @Test
  public void everyOverriddenDefaultOfTheBasicModulesIsListed() {
    assertThat(ContainerUtilKt.getProxiedServicesList()).containsAll(OVERRIDDEN_DEFAULT_KEYS);
  }

  @Test
  public void eachListedServiceIsProxied() {
    Map<Class<?>, Object> defaults = new LinkedHashMap<>();
    defaults.put(PyLanguageFacade.class, new PyDefaultLanguageFacade());
    defaults.put(PyPsiElementFactory.class, new PyAstPsiElementFactory());
    defaults.put(PySignatureCacheManager.class, new PyEmptySignatureCacheManager());
    assertThat(defaults.keySet()).extracting(Class::getName).containsExactlyElementsOf(OVERRIDDEN_DEFAULT_KEYS);
    for (var entry : defaults.entrySet()) {
      Object proxy = proxyOf(entry.getKey(), entry.getValue());
      assertThat(proxy).isInstanceOf(entry.getKey()).isInstanceOf(com.intellij.serviceContainer.ServiceProxyInstrumentation.class);
      assertThat(proxy.getClass().getName()).startsWith(entry.getKey().getName() + "$Proxy");
    }
  }

  @Test
  public void aProxyForwardsAndResets() {
    PySignatureCacheManager proxy = proxyOf(PySignatureCacheManager.class, new PyEmptySignatureCacheManager());
    assertFalse(proxy.clearCache());

    setForwarding(proxy, new ClearingSignatureCacheManager());
    assertTrue(proxy.clearCache());

    setForwarding(proxy, null);
    assertFalse(proxy.clearCache());
  }

  private static <T> @NotNull T proxyOf(@NotNull Class<T> key, @NotNull Object delegate) {
    return com.intellij.serviceContainer.ServiceProxyGenerator.INSTANCE.createInstance(key, delegate);
  }

  /** Makes the proxy forward to {@code target}, or to its own delegate again for {@code null}, as the container does on a move. */
  private static void setForwarding(@NotNull Object proxy, @Nullable Object target) {
    var instrumentation = (com.intellij.serviceContainer.ServiceProxyInstrumentation)proxy;
    instrumentation.setForwarding(target == null ? null : LazyKt.lazyOf(target));
  }

  /** Stands for the override of {@code PythonCore}: it differs from the empty default in {@link #clearCache()}. */
  private static final class ClearingSignatureCacheManager extends PySignatureCacheManager {
    @Override
    public void recordSignature(@NotNull PySignature signature) {
    }

    @Override
    public @Nullable String findParameterType(@NotNull PyAstFunction function, @NotNull String name) {
      return null;
    }

    @Override
    public @Nullable PySignature findSignature(@NotNull PyAstFunction function) {
      return null;
    }

    @Override
    public boolean clearCache() {
      return true;
    }
  }
}
