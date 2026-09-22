// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.updater;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Level;

import static com.intellij.updater.Runner.LOG;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * Windows downcalls for the updater: Restart Manager, the registry and the shell.
 * Load this class only when {@link Runtime#version()} is 22 or newer.
 */
@SuppressWarnings("SuspiciousPackagePrivateAccess")
public final class WindowsNative {
  public static final int MIN_FEATURE = 22;

  private static final int ERROR_SUCCESS = 0;
  private static final int ERROR_FILE_NOT_FOUND = 2;
  private static final int ERROR_MORE_DATA = 234;
  private static final int ERROR_NO_MORE_ITEMS = 259;

  private static final int CCH_RM_SESSION_KEY = 32;
  private static final int CCH_RM_MAX_APP_NAME = 255;
  private static final int CCH_RM_MAX_SVC_NAME = 63;

  private static final int KEY_READ = 0x20019;
  private static final int KEY_WRITE = 0x20006;

  private static final int REG_SZ = 1;
  private static final int RRF_RT_REG_SZ = 0x0002;
  private static final int RRF_RT_REG_EXPAND_SZ = 0x0004;
  private static final int RRF_NOEXPAND = 0x10000000;

  private static final int PROCESS_TERMINATE = 0x0001;
  private static final int SYNCHRONIZE = 0x00100000;
  private static final int WAIT_OBJECT_0 = 0;

  private static final long SHCNE_ASSOCCHANGED = 0x08000000L;
  private static final int SHCNF_IDLIST = 0;

  private static final int S_OK = 0;
  private static final int SHGFP_TYPE_CURRENT = 0;
  private static final int MAX_PATH = 260;

  /** Predefined hive handle; {@code winreg.h} sign-extends the {@code LONG} value. */
  static final long HKEY_CURRENT_USER = 0xFFFFFFFF80000001L;
  /** Predefined hive handle; {@code winreg.h} sign-extends the {@code LONG} value. */
  static final long HKEY_LOCAL_MACHINE = 0xFFFFFFFF80000002L;

  /**
   * Returns a supplier of the shared instance. The instance is {@code null} off Windows, before Java {@value #MIN_FEATURE},
   * or when a library fails to load. The first call loads the libraries, and every later call returns the same result.
   */
  public static @NotNull Supplier<@Nullable WindowsNative> supplier() {
    return () -> Holder.INSTANCE;
  }

  private static final class Holder {
    static final @Nullable WindowsNative INSTANCE = load();

    private static @Nullable WindowsNative load() {
      if (!Utils.IS_WINDOWS) {
        return null;
      }
      if (Runtime.version().feature() < MIN_FEATURE) {
        LOG.info("Windows native helpers need Java " + MIN_FEATURE + "+; the feature is skipped");
        return null;
      }
      try {
        // Touch the handles so a missing DLL fails here, not on the first call.
        Handles.ensureLoaded();
        return new WindowsNative();
      }
      catch (Throwable t) {
        LOG.log(Level.WARNING, "Windows native helpers failed to load", t);
        return null;
      }
    }
  }

  private WindowsNative() { }

  /**
   * {@code RM_PROCESS_INFO} on Windows x64 and ARM64. Size 668.
   * Safe to read on any OS; the size is checked on Windows.
   */
  @VisibleForTesting
  public static final StructLayout RM_PROCESS_INFO = MemoryLayout.structLayout(
    JAVA_INT.withName("dwProcessId"),
    JAVA_INT.withName("ftLow"),
    JAVA_INT.withName("ftHigh"),
    MemoryLayout.sequenceLayout(2L * (CCH_RM_MAX_APP_NAME + 1), JAVA_BYTE).withName("strAppName"),
    MemoryLayout.sequenceLayout(2L * (CCH_RM_MAX_SVC_NAME + 1), JAVA_BYTE).withName("strServiceShortName"),
    JAVA_INT.withName("ApplicationType"),
    JAVA_INT.withName("AppStatus"),
    JAVA_INT.withName("TSSessionId"),
    JAVA_INT.withName("bRestartable")
  ).withName("RM_PROCESS_INFO");

  private static final long APP_NAME_OFFSET = RM_PROCESS_INFO.byteOffset(MemoryLayout.PathElement.groupElement("strAppName"));
  private static final VarHandle PROCESS_ID = RM_PROCESS_INFO.varHandle(MemoryLayout.PathElement.groupElement("dwProcessId"));

  /**
   * @return processes that hold a lock on {@code file}, or an empty list when Restart Manager fails
   */
  @NotNull List<NativeFileManager.Process> processesUsing(@NotNull Path file, int initialBufferSize) {
    try (var arena = Arena.ofConfined()) {
      var session = arena.allocate(JAVA_INT);
      var sessionKey = arena.allocate(2L * (CCH_RM_SESSION_KEY + 1));
      var error = (int)Handles.RM_START_SESSION.invokeExact(session, 0, sessionKey);
      if (error != ERROR_SUCCESS) {
        LOG.warning("RmStartSession(): " + error);
        return List.of();
      }
      var sessionHandle = session.get(JAVA_INT, 0);
      try {
        var path = arena.allocateFrom(file.toString(), StandardCharsets.UTF_16LE);
        var files = arena.allocate(ADDRESS);
        files.set(ADDRESS, 0, path);
        error = (int)Handles.RM_REGISTER_RESOURCES.invokeExact(
          sessionHandle, 1, files, 0, MemorySegment.NULL, 0, MemorySegment.NULL);
        if (error != ERROR_SUCCESS) {
          LOG.warning("RmRegisterResources('" + file + "'): " + error);
          return List.of();
        }

        var arraySize = Math.max(initialBufferSize, 1);
        for (int retry = 0; retry < 5; retry++) {
          var procInfoNeeded = arena.allocate(JAVA_INT);
          var procInfo = arena.allocate(JAVA_INT);
          procInfo.set(JAVA_INT, 0, arraySize);
          var infos = arena.allocate(RM_PROCESS_INFO, arraySize);
          var rebootReasons = arena.allocate(JAVA_INT);
          error = (int)Handles.RM_GET_LIST.invokeExact(sessionHandle, procInfoNeeded, procInfo, infos, rebootReasons);
          if (error == ERROR_MORE_DATA) {
            arraySize = Math.max(procInfoNeeded.get(JAVA_INT, 0), arraySize + 1);
            continue;
          }
          if (error != ERROR_SUCCESS) {
            LOG.warning("RmGetList('" + file + "'): " + error);
            return List.of();
          }
          var n = procInfo.get(JAVA_INT, 0);
          var processes = new ArrayList<NativeFileManager.Process>(n);
          for (int i = 0; i < n; i++) {
            var info = infos.asSlice(i * RM_PROCESS_INFO.byteSize(), RM_PROCESS_INFO.byteSize());
            var pid = (int)PROCESS_ID.get(info, 0L);
            var name = readAppName(info);
            processes.add(new NativeFileManager.Process(pid, name));
          }
          return processes;
        }
      }
      finally {
        var ignored = (int)Handles.RM_END_SESSION.invokeExact(sessionHandle);
      }
    }
    catch (Throwable t) {
      LOG.log(Level.WARNING, "Restart Manager failed for '" + file + "'", t);
    }
    return List.of();
  }

  /** Ends the process and waits up to one second. */
  boolean terminateProcess(int pid, @NotNull String name) {
    try {
      var handle = (MemorySegment)Handles.OPEN_PROCESS.invokeExact(PROCESS_TERMINATE | SYNCHRONIZE, 0, pid);
      if (handle.address() == 0) {
        LOG.warning("Unable to find process " + name + '[' + pid + ']');
        return false;
      }
      try {
        var terminated = (int)Handles.TERMINATE_PROCESS.invokeExact(handle, 1);
        if (terminated == 0) {
          LOG.warning("TerminateProcess failed for " + name + '[' + pid + ']');
          return false;
        }
        var wait = (int)Handles.WAIT_FOR_SINGLE_OBJECT.invokeExact(handle, 1000);
        if (wait != WAIT_OBJECT_0) {
          LOG.warning("Timed out while waiting for process " + name + '[' + pid + "] to end");
          return false;
        }
        return true;
      }
      finally {
        var ignored = (int)Handles.CLOSE_HANDLE.invokeExact(handle);
      }
    }
    catch (Throwable t) {
      LOG.log(Level.WARNING, "Unable to terminate process " + name + '[' + pid + ']', t);
      return false;
    }
  }

  /**
   * @return the names of the direct subkeys, or an empty array when the key is missing
   */
  @NotNull String @NotNull [] registrySubKeys(long rootKey, @NotNull String key) throws NativeException {
    try (var opened = openKey(rootKey, key, KEY_READ)) {
      if (opened == null) {
        return new String[0];
      }
      return opened.subKeys();
    }
  }

  /**
   * @return a {@code REG_SZ}/{@code REG_EXPAND_SZ} value, not expanded, or {@code null} when missing
   */
  @Nullable String registryGetString(long rootKey, @NotNull String key, @NotNull String value) throws NativeException {
    try (var arena = Arena.ofConfined()) {
      var subKey = arena.allocateFrom(key, StandardCharsets.UTF_16LE);
      var valueName = arena.allocateFrom(value, StandardCharsets.UTF_16LE);
      var flags = RRF_RT_REG_SZ | RRF_RT_REG_EXPAND_SZ | RRF_NOEXPAND;
      var size = arena.allocate(JAVA_INT);
      var status = (int)Handles.REG_GET_VALUE.invokeExact(
        rootKey, subKey, valueName, flags, MemorySegment.NULL, MemorySegment.NULL, size);
      if (status == ERROR_FILE_NOT_FOUND) {
        return null;
      }
      if (status != ERROR_SUCCESS) {
        throw new NativeException("RegGetValueW", status);
      }
      var data = arena.allocate(size.get(JAVA_INT, 0));
      status = (int)Handles.REG_GET_VALUE.invokeExact(rootKey, subKey, valueName, flags, MemorySegment.NULL, data, size);
      if (status == ERROR_FILE_NOT_FOUND) {
        return null;
      }
      if (status != ERROR_SUCCESS) {
        throw new NativeException("RegGetValueW", status);
      }
      return decodeUtf16z(data.asSlice(0, size.get(JAVA_INT, 0)).toArray(JAVA_BYTE));
    }
    catch (NativeException e) {
      throw e;
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  void registrySetString(long rootKey, @NotNull String key, @NotNull String value, @NotNull String data) throws NativeException {
    try (var opened = openKey(rootKey, key, KEY_READ | KEY_WRITE)) {
      if (opened == null) {
        throw new NativeException("RegOpenKeyExW", ERROR_FILE_NOT_FOUND);
      }
      opened.setString(value, data);
    }
  }

  void registryCreateKey(long rootKey, @NotNull String key) throws NativeException {
    try (var arena = Arena.ofConfined()) {
      var subKey = arena.allocateFrom(key, StandardCharsets.UTF_16LE);
      var result = arena.allocate(JAVA_LONG);
      var disposition = arena.allocate(JAVA_INT);
      var status = (int)Handles.REG_CREATE_KEY_EX.invokeExact(
        rootKey, subKey, 0, MemorySegment.NULL, 0, KEY_READ, MemorySegment.NULL, result, disposition);
      if (status != ERROR_SUCCESS) {
        throw new NativeException("RegCreateKeyExW", status);
      }
      var handle = result.get(JAVA_LONG, 0);
      var ignored = (int)Handles.REG_CLOSE_KEY.invokeExact(handle);
    }
    catch (NativeException e) {
      throw e;
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  /** Copies every value of {@code from} to {@code to}, with the original type and data. */
  void registryCopyValues(long rootKey, @NotNull String from, @NotNull String to) throws NativeException {
    try (var source = openKey(rootKey, from, KEY_READ); var target = openKey(rootKey, to, KEY_READ | KEY_WRITE)) {
      if (source == null || target == null) {
        throw new NativeException("RegOpenKeyExW", ERROR_FILE_NOT_FOUND);
      }
      source.copyValuesTo(target);
    }
  }

  void registryDeleteKey(long rootKey, @NotNull String key) throws NativeException {
    var parentEnd = key.lastIndexOf('\\');
    if (parentEnd <= 0) {
      throw new IllegalArgumentException("Cannot delete a root key: " + key);
    }
    var parent = key.substring(0, parentEnd);
    var name = key.substring(parentEnd + 1);
    try (var opened = openKey(rootKey, parent, KEY_READ | KEY_WRITE)) {
      if (opened == null) {
        throw new NativeException("RegOpenKeyExW", ERROR_FILE_NOT_FOUND);
      }
      opened.deleteSubKey(name);
    }
  }

  void notifyShellAssociationsChanged() {
    try {
      Handles.SH_CHANGE_NOTIFY.invokeExact((int)SHCNE_ASSOCCHANGED, SHCNF_IDLIST, MemorySegment.NULL, MemorySegment.NULL);
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  /**
   * @return the current path of a {@code CSIDL} shell folder, with the user redirection applied, or {@code null} when the shell fails
   */
  @Nullable Path folderPath(int csidl) {
    try (var arena = Arena.ofConfined()) {
      var path = arena.allocate(2L * MAX_PATH);
      var result = (int)Handles.SH_GET_FOLDER_PATH.invokeExact(MemorySegment.NULL, csidl, MemorySegment.NULL, SHGFP_TYPE_CURRENT, path);
      if (result != S_OK) {
        LOG.warning("SHGetFolderPathW(" + csidl + "): 0x" + Integer.toHexString(result));
        return null;
      }
      return Path.of(decodeUtf16z(path.toArray(JAVA_BYTE)));
    }
    catch (Throwable t) {
      LOG.log(Level.WARNING, "SHGetFolderPathW(" + csidl + ')', t);
      return null;
    }
  }

  private static @Nullable Key openKey(long rootKey, @NotNull String key, int access) throws NativeException {
    try (var arena = Arena.ofConfined()) {
      var subKey = arena.allocateFrom(key, StandardCharsets.UTF_16LE);
      var result = arena.allocate(JAVA_LONG);
      var status = (int)Handles.REG_OPEN_KEY_EX.invokeExact(rootKey, subKey, 0, access, result);
      if (status == ERROR_FILE_NOT_FOUND) {
        return null;
      }
      if (status != ERROR_SUCCESS) {
        throw new NativeException("RegOpenKeyExW", status);
      }
      return new Key(result.get(JAVA_LONG, 0));
    }
    catch (NativeException e) {
      throw e;
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  /** A Win32 {@code LSTATUS} failure. {@link #errorCode} is the status. */
  static final class NativeException extends Exception {
    final int errorCode;

    NativeException(@NotNull String function, int errorCode) {
      super(function + " failed with Win32 error " + errorCode);
      this.errorCode = errorCode;
    }
  }

  private static final class Key implements AutoCloseable {
    private long handle;

    private Key(long handle) {
      this.handle = handle;
    }

    private long handle() {
      if (handle == 0L) {
        throw new IllegalStateException("Registry key is closed");
      }
      return handle;
    }

    @NotNull String @NotNull [] subKeys() throws NativeException {
      var key = handle();
      try (var arena = Arena.ofConfined()) {
        var count = arena.allocate(JAVA_INT);
        var maxNameLength = arena.allocate(JAVA_INT);
        queryInfo(key, count, maxNameLength, MemorySegment.NULL, MemorySegment.NULL, MemorySegment.NULL);
        var capacity = maxNameLength.get(JAVA_INT, 0) + 1;
        var name = arena.allocate(2L * capacity);
        var nameLength = arena.allocate(JAVA_INT);
        var result = new String[count.get(JAVA_INT, 0)];
        for (int i = 0; i < result.length; i++) {
          nameLength.set(JAVA_INT, 0, capacity);
          var status = (int)Handles.REG_ENUM_KEY_EX.invokeExact(
            key, i, name, nameLength, MemorySegment.NULL, MemorySegment.NULL, MemorySegment.NULL, MemorySegment.NULL);
          if (status == ERROR_NO_MORE_ITEMS) {
            return Arrays.copyOf(result, i);
          }
          if (status != ERROR_SUCCESS) {
            throw new NativeException("RegEnumKeyExW", status);
          }
          result[i] = utf16(name, nameLength.get(JAVA_INT, 0));
        }
        return result;
      }
      catch (NativeException e) {
        throw e;
      }
      catch (Throwable t) {
        throw new IllegalStateException(t);
      }
    }

    void copyValuesTo(@NotNull Key target) throws NativeException {
      var key = handle();
      var targetKey = target.handle();
      try (var arena = Arena.ofConfined()) {
        var count = arena.allocate(JAVA_INT);
        var maxNameLength = arena.allocate(JAVA_INT);
        var maxDataLength = arena.allocate(JAVA_INT);
        queryInfo(key, MemorySegment.NULL, MemorySegment.NULL, count, maxNameLength, maxDataLength);
        var nameCapacity = maxNameLength.get(JAVA_INT, 0) + 1;
        var name = arena.allocate(2L * nameCapacity);
        var nameLength = arena.allocate(JAVA_INT);
        var type = arena.allocate(JAVA_INT);
        var data = arena.allocate(Math.max(maxDataLength.get(JAVA_INT, 0), Long.BYTES));
        var dataLength = arena.allocate(JAVA_INT);
        for (int i = 0, n = count.get(JAVA_INT, 0); i < n; i++) {
          int status;
          while (true) {
            nameLength.set(JAVA_INT, 0, nameCapacity);
            dataLength.set(JAVA_INT, 0, (int)data.byteSize());
            status = (int)Handles.REG_ENUM_VALUE.invokeExact(
              key, i, name, nameLength, MemorySegment.NULL, type, data, dataLength);
            if (status != ERROR_MORE_DATA || dataLength.get(JAVA_INT, 0) <= data.byteSize()) {
              break;
            }
            data = arena.allocate(dataLength.get(JAVA_INT, 0));
          }
          if (status == ERROR_NO_MORE_ITEMS) {
            break;
          }
          if (status != ERROR_SUCCESS) {
            throw new NativeException("RegEnumValueW", status);
          }
          // RegEnumValueW terminates the name with a null character, so the buffer is a valid LPCWSTR.
          status = (int)Handles.REG_SET_VALUE_EX.invokeExact(
            targetKey, name, 0, type.get(JAVA_INT, 0), data, dataLength.get(JAVA_INT, 0));
          if (status != ERROR_SUCCESS) {
            throw new NativeException("RegSetValueExW", status);
          }
        }
      }
      catch (NativeException e) {
        throw e;
      }
      catch (Throwable t) {
        throw new IllegalStateException(t);
      }
    }

    void setString(@NotNull String value, @NotNull String data) throws NativeException {
      try (var arena = Arena.ofConfined()) {
        var valueName = arena.allocateFrom(value, StandardCharsets.UTF_16LE);
        var bytes = (data + '\0').getBytes(StandardCharsets.UTF_16LE);
        var buffer = arena.allocateFrom(JAVA_BYTE, bytes);
        var status = (int)Handles.REG_SET_VALUE_EX.invokeExact(
          handle(), valueName, 0, REG_SZ, buffer, bytes.length);
        if (status != ERROR_SUCCESS) {
          throw new NativeException("RegSetValueExW", status);
        }
      }
      catch (NativeException e) {
        throw e;
      }
      catch (Throwable t) {
        throw new IllegalStateException(t);
      }
    }

    void deleteSubKey(@NotNull String name) throws NativeException {
      try (var arena = Arena.ofConfined()) {
        var subKey = arena.allocateFrom(name, StandardCharsets.UTF_16LE);
        var status = (int)Handles.REG_DELETE_KEY.invokeExact(handle(), subKey);
        if (status != ERROR_SUCCESS) {
          throw new NativeException("RegDeleteKeyW", status);
        }
      }
      catch (NativeException e) {
        throw e;
      }
      catch (Throwable t) {
        throw new IllegalStateException(t);
      }
    }

    @Override
    public void close() {
      var key = handle;
      if (key != 0L) {
        handle = 0L;
        try {
          var ignored = (int)Handles.REG_CLOSE_KEY.invokeExact(key);
        }
        catch (Throwable t) {
          throw new IllegalStateException(t);
        }
      }
    }

    private static void queryInfo(
      long key,
      MemorySegment subKeys,
      MemorySegment maxSubKeyLength,
      MemorySegment values,
      MemorySegment maxValueNameLength,
      MemorySegment maxValueLength
    ) throws NativeException {
      try {
        var status = (int)Handles.REG_QUERY_INFO_KEY.invokeExact(
          key,
          MemorySegment.NULL, MemorySegment.NULL, MemorySegment.NULL,
          subKeys, maxSubKeyLength, MemorySegment.NULL,
          values, maxValueNameLength, maxValueLength,
          MemorySegment.NULL, MemorySegment.NULL);
        if (status != ERROR_SUCCESS) {
          throw new NativeException("RegQueryInfoKeyW", status);
        }
      }
      catch (NativeException e) {
        throw e;
      }
      catch (Throwable t) {
        throw new IllegalStateException(t);
      }
    }
  }

  private static @NotNull String readAppName(MemorySegment struct) {
    var bytes = struct.asSlice(APP_NAME_OFFSET, 2L * (CCH_RM_MAX_APP_NAME + 1)).toArray(JAVA_BYTE);
    return decodeUtf16z(bytes).trim();
  }

  private static @NotNull String utf16(MemorySegment name, int charCount) {
    return new String(name.asSlice(0, 2L * charCount).toArray(JAVA_BYTE), StandardCharsets.UTF_16LE);
  }

  private static @NotNull String decodeUtf16z(byte @NotNull [] data) {
    var text = new String(data, StandardCharsets.UTF_16LE);
    var terminator = text.indexOf('\0');
    return terminator >= 0 ? text.substring(0, terminator) : text;
  }

  /**
   * Library handles. The static init loads the DLLs, so a first touch on a non-Windows host fails.
   */
  private static final class Handles {
    private static final Linker LINKER = Linker.nativeLinker();

    private static final SymbolLookup RSTRTMGR;
    private static final SymbolLookup ADVAPI32;
    private static final SymbolLookup KERNEL32;
    private static final SymbolLookup SHELL32;

    static final MethodHandle RM_START_SESSION;
    static final MethodHandle RM_REGISTER_RESOURCES;
    static final MethodHandle RM_GET_LIST;
    static final MethodHandle RM_END_SESSION;

    static final MethodHandle REG_OPEN_KEY_EX;
    static final MethodHandle REG_CREATE_KEY_EX;
    static final MethodHandle REG_CLOSE_KEY;
    static final MethodHandle REG_GET_VALUE;
    static final MethodHandle REG_SET_VALUE_EX;
    static final MethodHandle REG_DELETE_KEY;
    static final MethodHandle REG_QUERY_INFO_KEY;
    static final MethodHandle REG_ENUM_KEY_EX;
    static final MethodHandle REG_ENUM_VALUE;

    static final MethodHandle OPEN_PROCESS;
    static final MethodHandle TERMINATE_PROCESS;
    static final MethodHandle WAIT_FOR_SINGLE_OBJECT;
    static final MethodHandle CLOSE_HANDLE;

    static final MethodHandle SH_CHANGE_NOTIFY;
    static final MethodHandle SH_GET_FOLDER_PATH;

    static {
      RSTRTMGR = SymbolLookup.libraryLookup("rstrtmgr", Arena.global());
      ADVAPI32 = SymbolLookup.libraryLookup("advapi32", Arena.global());
      KERNEL32 = SymbolLookup.libraryLookup("kernel32", Arena.global());
      SHELL32 = SymbolLookup.libraryLookup("shell32", Arena.global());

      // DWORD RmStartSession(DWORD* pSessionHandle, DWORD dwSessionFlags, WCHAR* strSessionKey)
      RM_START_SESSION = LINKER.downcallHandle(
        find(RSTRTMGR, "RmStartSession"),
        FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS));
      // DWORD RmRegisterResources(DWORD session, UINT nFiles, LPCWSTR* files, UINT nApps, RM_UNIQUE_PROCESS* apps, UINT nServices, LPCWSTR* services)
      RM_REGISTER_RESOURCES = LINKER.downcallHandle(
        find(RSTRTMGR, "RmRegisterResources"),
        FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS));
      // DWORD RmGetList(DWORD session, DWORD* needed, DWORD* count, RM_PROCESS_INFO* infos, DWORD* rebootReasons)
      // lpdwRebootReasons is DWORD*, not ULONG64*
      RM_GET_LIST = LINKER.downcallHandle(
        find(RSTRTMGR, "RmGetList"),
        FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
      RM_END_SESSION = LINKER.downcallHandle(
        find(RSTRTMGR, "RmEndSession"),
        FunctionDescriptor.of(JAVA_INT, JAVA_INT));

      REG_OPEN_KEY_EX = LINKER.downcallHandle(
        find(ADVAPI32, "RegOpenKeyExW"),
        FunctionDescriptor.of(JAVA_INT, JAVA_LONG, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS));
      REG_CREATE_KEY_EX = LINKER.downcallHandle(
        find(ADVAPI32, "RegCreateKeyExW"),
        FunctionDescriptor.of(JAVA_INT, JAVA_LONG, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
      REG_CLOSE_KEY = LINKER.downcallHandle(
        find(ADVAPI32, "RegCloseKey"),
        FunctionDescriptor.of(JAVA_INT, JAVA_LONG));
      REG_GET_VALUE = LINKER.downcallHandle(
        find(ADVAPI32, "RegGetValueW"),
        FunctionDescriptor.of(JAVA_INT, JAVA_LONG, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
      REG_SET_VALUE_EX = LINKER.downcallHandle(
        find(ADVAPI32, "RegSetValueExW"),
        FunctionDescriptor.of(JAVA_INT, JAVA_LONG, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT));
      REG_DELETE_KEY = LINKER.downcallHandle(
        find(ADVAPI32, "RegDeleteKeyW"),
        FunctionDescriptor.of(JAVA_INT, JAVA_LONG, ADDRESS));
      REG_QUERY_INFO_KEY = LINKER.downcallHandle(
        find(ADVAPI32, "RegQueryInfoKeyW"),
        FunctionDescriptor.of(JAVA_INT, JAVA_LONG, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
      REG_ENUM_KEY_EX = LINKER.downcallHandle(
        find(ADVAPI32, "RegEnumKeyExW"),
        FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
      REG_ENUM_VALUE = LINKER.downcallHandle(
        find(ADVAPI32, "RegEnumValueW"),
        FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS));

      OPEN_PROCESS = LINKER.downcallHandle(
        find(KERNEL32, "OpenProcess"),
        FunctionDescriptor.of(ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT));
      TERMINATE_PROCESS = LINKER.downcallHandle(
        find(KERNEL32, "TerminateProcess"),
        FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));
      WAIT_FOR_SINGLE_OBJECT = LINKER.downcallHandle(
        find(KERNEL32, "WaitForSingleObject"),
        FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));
      CLOSE_HANDLE = LINKER.downcallHandle(
        find(KERNEL32, "CloseHandle"),
        FunctionDescriptor.of(JAVA_INT, ADDRESS));

      // void SHChangeNotify(LONG wEventId, UINT uFlags, LPCVOID dwItem1, LPCVOID dwItem2); Win32 LONG is 32-bit
      SH_CHANGE_NOTIFY = LINKER.downcallHandle(
        find(SHELL32, "SHChangeNotify"),
        FunctionDescriptor.ofVoid(JAVA_INT, JAVA_INT, ADDRESS, ADDRESS));
      // HRESULT SHGetFolderPathW(HWND hwnd, int csidl, HANDLE hToken, DWORD dwFlags, LPWSTR pszPath)
      SH_GET_FOLDER_PATH = LINKER.downcallHandle(
        find(SHELL32, "SHGetFolderPathW"),
        FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS));
    }

    private static @NotNull MemorySegment find(@NotNull SymbolLookup library, @NotNull String name) {
      return library.find(name).orElseThrow(() -> new IllegalStateException("Missing native symbol " + name));
    }

    static void ensureLoaded() {
      // The static init already loaded the libraries. Reference a handle so the class init runs.
      if (RM_START_SESSION == null) {
        throw new IllegalStateException("Restart Manager is not loaded");
      }
    }

    private Handles() { }
  }
}
