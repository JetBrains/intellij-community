package org.jetbrains.debugger.memory.utils;

import com.sun.jdi.VirtualMachine;
import org.jetbrains.annotations.NotNull;

public class AndroidUtil {
  public static final int ANDROID_INSTANCES_COUNT_BATCH_SIZE = 500;

  public static boolean isAndroidVM(@NotNull VirtualMachine vm) {
    return vm.name().toLowerCase().contains("dalvik");
  }
}
