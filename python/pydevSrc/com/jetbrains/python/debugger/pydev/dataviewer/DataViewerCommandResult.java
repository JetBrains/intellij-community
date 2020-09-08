// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger.pydev.dataviewer;

import static com.jetbrains.python.debugger.pydev.dataviewer.DataViewerCommandResult.ResultType.FILE_NOT_FOUND_ERROR;
import static com.jetbrains.python.debugger.pydev.dataviewer.DataViewerCommandResult.ResultType.UNHANDLED_ERROR;

public class DataViewerCommandResult {
  public enum ResultType {
    OK,
    FILE_NOT_FOUND_ERROR,
    NOT_IMPLEMENTED_ERROR,
    UNHANDLED_ERROR,
  }

  private final ResultType type;
  private final String log;

  public boolean isSuccess() {
    return type == ResultType.OK;
  }

  public ResultType getErrorType() {
    return type;
  }

  public String getLog() {
    return log;
  }

  public static DataViewerCommandResult makeErrorResult(ResultType error, String log) {
    assert error != ResultType.OK;
    return new DataViewerCommandResult(error, log);
  }

  public static DataViewerCommandResult makeSuccessResult(String data) {
    return new DataViewerCommandResult(ResultType.OK, data);
  }

  public final static DataViewerCommandResult NOT_IMPLEMENTED = makeErrorResult(ResultType.NOT_IMPLEMENTED_ERROR, "Not implemented");

  private DataViewerCommandResult(ResultType type, String log) {
    this.type = type;
    this.log = log;
  }

  public static DataViewerCommandResult errorFromExportTraceback(String response) {
    if (response.startsWith("FileNotFoundError") || response.startsWith("OSError")) {
      return makeErrorResult(FILE_NOT_FOUND_ERROR, response);
    }
    else {
      return makeErrorResult(UNHANDLED_ERROR, response);
    }
  }
}
