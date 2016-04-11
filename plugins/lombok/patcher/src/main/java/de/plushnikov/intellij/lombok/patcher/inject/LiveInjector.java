/*
 * Copyright (C) 2009-2014 The Project Lombok Authors.
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package de.plushnikov.intellij.lombok.patcher.inject;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;

/**
 * This class allows you to inject a java agent 'on the fly' into your VM. This feature only works on sun-derived VMs, such as the openJDK, sun JVMs,
 * and apple's VMs.
 * <p>
 * While an agent can be injected in both 1.5 and 1.6 VMs, class reloading is only supported on 1.6 VMs, so if you want to be 1.5 compatible,
 * and want to transform classes, you'll need to ensure you inject the agent before the class you're interested in gets loaded. Otherwise, you can
 * just reload these classes from within the injected agent.
 * <p>
 * As a convenience, you can choose to inject this class (which is also an agent), but as a java agent <b>has</b> to come in a jar file, this only
 * works if this class has been loaded from a jar file on the local file system. If it has indeed been loaded that way, then this class can take care
 * of injecting itself. A common scenario is to distribute your patching application together with {@code lombok.patcher} in a single jar file.
 * That way, you can use the live injector, and your injected agent can then run patchscripts that call into your own code, and all your code's dependencies
 * will be available because they, too, are in this unified jar file.
 */
public class LiveInjector {
  /**
   * This interface is used internally by the {@code LiveInjector} to interface with the VM core.
   */
  public interface LibInstrument extends Library {
    void Agent_OnAttach(Pointer vm, String name, Pointer Reserved);
  }

  /**
   * This interface is used internally by the {@code LiveInjector} to interface with the VM core.
   */
  public interface LibJVM extends Library {
    int JNI_GetCreatedJavaVMs(PointerByReference vms, int count, IntByReference found);
  }

  /**
   * Injects the jar file that contains the code for {@code LiveInjector} as an agent. Its your job to make sure this jar is in fact an agent jar.
   *
   * @throws IllegalStateException If this is not a sun-derived v1.6 VM.
   */
  public void injectSelf() throws IllegalStateException {
    inject(ClassRootFinder.findClassRootOfSelf());
  }

  /**
   * Injects a jar file into the current VM as a live-loaded agent. The provided jar will be loaded into its own separate class loading context,
   * and its manifest is checked for an {@code Agent-Class} to load. That class should have a static method named {@code agentmain} which will
   * be called, with an {@link java.lang.instrument.Instrumentation} object that you're probably after.
   *
   * @throws IllegalStateException If this is not a sun-derived v1.6 VM.
   */
  public void inject(String jarFile) throws IllegalStateException {
    File f = new File(jarFile);
    if (!f.isFile()) {
      throw new IllegalArgumentException("Live Injection is not possible unless the classpath root to inject is a jar file.");
    }
    if (System.getProperty("lombok.patcher.safeInject", null) != null) {
      slowInject(jarFile);
    } else {
      fastInject(jarFile);
    }
  }

  private void fastInject(String jarFile) throws IllegalStateException {
    try {
      Class.forName("sun.instrument.InstrumentationImpl");
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException("agent injection only works on a sun-derived 1.6 or higher VM");
    }

    LibJVM libjvm = (LibJVM) Native.loadLibrary(LibJVM.class);
    PointerByReference vms = new PointerByReference();
    IntByReference found = new IntByReference();
    libjvm.JNI_GetCreatedJavaVMs(vms, 1, found);
    LibInstrument libinstrument = (LibInstrument) Native.loadLibrary(LibInstrument.class);
    Pointer vm = vms.getValue();
    libinstrument.Agent_OnAttach(vm, jarFile, null);
  }

  private void slowInject(String jarFile) throws IllegalStateException {
    String ownPidS = ManagementFactory.getRuntimeMXBean().getName();
    ownPidS = ownPidS.substring(0, ownPidS.indexOf('@'));
    int ownPid = Integer.parseInt(ownPidS);
    boolean unsupportedEnvironment = false;
    Throwable exception = null;
    try {
      Class<?> vmClass = Class.forName("com.sun.tools.attach.VirtualMachine");
      Object vm = vmClass.getMethod("attach", String.class).invoke(null, String.valueOf(ownPid));
      vmClass.getMethod("loadAgent", String.class).invoke(vm, jarFile);
    } catch (ClassNotFoundException e) {
      unsupportedEnvironment = true;
    } catch (NoSuchMethodException e) {
      unsupportedEnvironment = true;
    } catch (InvocationTargetException e) {
      exception = e.getCause();
      if (exception == null) {
        exception = e;
      }
    } catch (Throwable t) {
      exception = t;
    }

    if (unsupportedEnvironment) {
      throw new IllegalStateException("agent injection only works on a sun-derived 1.6 or higher VM");
    }
    if (exception != null) {
      throw new IllegalStateException("agent injection not supported on this platform due to unknown reason", exception);
    }
  }
}
