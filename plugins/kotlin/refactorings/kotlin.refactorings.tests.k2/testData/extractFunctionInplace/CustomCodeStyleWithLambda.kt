// CHANGED_NAME: extracted

class BackendCompletionSession
class RpcCompletionRequestId(val id: Int)

class X {
  fun closeSession(session: BackendCompletionSession, request: RpcCompletionRequestId) {
    println("Stopping $session")
    <selection>run {
      println("Closing $session")
      println("Removing ${request.id}")
    }</selection>
  }
}