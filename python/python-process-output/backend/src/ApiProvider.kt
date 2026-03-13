package com.intellij.python.processOutput.backend

import com.intellij.platform.rpc.backend.RemoteApiProvider
import com.intellij.python.processOutput.common.BackEndApi
import com.intellij.python.processOutput.common.QueryId
import com.intellij.python.processOutput.common.QueryResponsePayload
import com.intellij.python.processOutput.common.resolveProcessOutputQuery
import fleet.rpc.remoteApiDescriptor

internal class ApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<BackEndApi>()) {
      BackendApiImpl
    }
  }
}

private object BackendApiImpl : BackEndApi {
  override suspend fun sendProcessOutputQueryResponse(
    requestId: QueryId,
    response: QueryResponsePayload,
  ) {
    resolveProcessOutputQuery(requestId, response)
  }
}