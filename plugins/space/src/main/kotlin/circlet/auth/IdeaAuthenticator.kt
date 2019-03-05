package circlet.auth

import circlet.common.oauth.*
import circlet.workspaces.*
import com.intellij.openapi.diagnostic.*
import io.ktor.application.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import klogging.*
import runtime.reactive.*
import runtime.utils.*
import java.awt.*
import java.net.*
import java.util.concurrent.*
import kotlin.coroutines.*

val log = KLoggers.logger("authTokenInteractive")

suspend fun authTokenInteractive(lifetime: Lifetime, config: WorkspaceConfiguration): OAuthTokenResponse {

    val port = selectFreePort(10000)

    val redirectUri = "http://localhost:$port/auth"

    return suspendCoroutine { cnt ->

        val server = try {
            embeddedServer(Jetty, port, "localhost") {
                routing {
                    get("auth") {
                        val token = config.handleCodeFlowRedirect(call.request.uri, redirectUri)
                        call.respondText("<script>close()</script>", io.ktor.http.ContentType("text", "html"))
                        cnt.resume(token)
                    }
                }
            }.start(wait = false)
        }
        catch (th: Throwable) {
            log.error(th, "Can't start server at: $redirectUri")
            throw th
        }

        lifetime.add {
            server.stop(100, 5000, TimeUnit.MILLISECONDS)
        }

        val uri = URI(config.codeFlowURL(redirectUri))
        Desktop.getDesktop().browse(uri)
    }
}

