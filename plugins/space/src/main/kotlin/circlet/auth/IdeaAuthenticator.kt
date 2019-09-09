package circlet.auth

import circlet.platform.api.oauth.*
import circlet.platform.workspaces.*
import io.ktor.application.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import libraries.coroutines.extra.*
import libraries.klogging.*
import runtime.utils.*
import java.awt.*
import java.net.*
import java.util.concurrent.*
import kotlin.coroutines.*

val log = KLoggers.logger()

suspend fun accessTokenInteractive(lifetime: Lifetime, config: WorkspaceConfiguration): OAuthTokenResponse {

    val port = selectFreePort()
    val codeFlow = CodeFlowConfig(config, "http://localhost:$port/auth")

    return suspendCancellableCoroutine { cnt ->

        val server = try {
            embeddedServer(Jetty, port, "localhost") {
                routing {
                    get("auth") {
                        val token = codeFlow.handleCodeFlowRedirect(call.request.uri)
                        call.respondText("<script>close()</script>", io.ktor.http.ContentType("text", "html"))
                        cnt.resume(token)
                    }
                }
            }.start(wait = false)
        }
        catch (th: Throwable) {
            log.error(th, "Can't start server at: ${codeFlow.redirectUri}")
            throw th
        }

        lifetime.add {
            server.stop(100, 5000, TimeUnit.MILLISECONDS)
        }

        val uri = URI(codeFlow.codeFlowURL())
        Desktop.getDesktop().browse(uri)
    }
}

