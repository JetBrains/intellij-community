// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger.pydev;

/**
 * IVariableLocator knows how to produce location information
 * for CMD_GET_VARIABLE
 *
 * The location is specified as:
 *
 * thread_id, stack_frame, LOCAL|GLOBAL, attribute*
 */
public interface PyVariableLocator {

  String getThreadId();

  String getPyDBLocation();

}
