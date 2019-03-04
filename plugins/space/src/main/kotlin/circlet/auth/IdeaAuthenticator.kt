package circlet.auth

import circlet.client.api.*
import circlet.common.oauth.*
import circlet.components.*
import circlet.utils.*
import circlet.workspaces.*
import io.ktor.application.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import klogging.*
import runtime.net.*
import runtime.reactive.*
import runtime.utils.*
import java.awt.*
import java.net.*
import java.util.concurrent.*
import kotlin.coroutines.*

val log = logger<IdeaAuthenticator>()

class IdeaAuthenticator(val lifetime: Lifetime, val config: WorkspaceConfiguration) {

    suspend fun authTokenInteractive(): OAuthTokenResponse {

        val port = selectFreePort(10000)
        val redirectUri = "http://localhost:$port/auth"

        return suspendCoroutine { cnt ->

            val server = try {
                embeddedServer(Jetty, port, "localhost") {
                    routing {
                        get("auth") {
                            val token = token(call.request.uri, redirectUri)
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

            Desktop.getDesktop().browse(URI(
                codeFlowURL(config.server, config.clientId, redirectUri, isOffline = true)
            ))
        }
    }

    private suspend fun token(uri: String, redirectUri: String): OAuthTokenResponse {
        val query = Uri.extractQuery(uri) ?: return OAuthTokenResponse.Error("500", "Error parsing redirect uri: $uri")
        val queryParameters = Uri.parseQuery(query)
        val code = queryParameters["code"].firstOrNull() ?: return OAuthTokenResponse.Error("500", "Error parsing redirect uri, code is missing: $uri")
        return codeFlowToken(config.server, config.clientId, redirectUri, config.clientSecret, code)
    }

    suspend fun localAuthToken(): OAuthTokenResponse {
        // todo: handle nulls.
        val state = circletWorkspace.workspaces.value!!.workspaceStateFromPersistence()!!
        return refreshTokenFlow(
            circletURL = config.server,
            clientId = config.clientId,
            clientSecret = config.clientSecret,
            refreshToken = state.token.refreshToken!!
        )
    }
}

