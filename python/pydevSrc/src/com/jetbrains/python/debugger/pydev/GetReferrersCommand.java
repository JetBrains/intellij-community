// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger.pydev;

import com.intellij.util.io.URLUtil;
import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.PyDebuggerException;
import com.jetbrains.python.debugger.PyReferringObjectsValue;

import java.util.List;

public class GetReferrersCommand extends RunCustomOperationCommand<List<PyDebugValue>> {

  public GetReferrersCommand(RemoteDebugger target, String threadId, String frameId, PyReferringObjectsValue value) {
    super(target, createVariableLocator(threadId, frameId, value), "from _pydevd_bundle.pydevd_referrers import get_referrer_info",
          "get_referrer_info");
  }

  @Override
  protected ResponseProcessor<List<PyDebugValue>> createResponseProcessor() {
    return new ResponseProcessor<>() {
      @Override
      protected List<PyDebugValue> parseResponse(ProtocolFrame response) throws PyDebuggerException {
        return ProtocolParser.parseReferrers(URLUtil.decode(response.getPayload()), getDebugger().getDebugProcess());
      }
    };
  }


  private static PyVariableLocator createVariableLocator(final String threadId, final String frameId, final PyReferringObjectsValue var) {
    return new PyVariableLocator() {
      @Override
      public String getThreadId() {
        return threadId;
      }


      @Override
      public String getPyDBLocation() {
        if (var.getId() == null) {
          return threadId + "\t" + frameId + "\tFRAME\t" + var.getName();
        }
        //Ok, this only happens when we're dealing with references with no proper scope given and we need to get
        //things by id (which is usually not ideal). In this case we keep the proper thread id and set the frame id
        //as the id of the object to be searched later on based on the list of all alive objects.
        return getThreadId() + "\t" + var.getId() + "\tBY_ID";
      }
    };
  }
}
