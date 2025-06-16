package com.intellij.mcpserver;

import com.intellij.mcpserver.stdio.ClassPathMarker;
import io.github.oshai.kotlinlogging.KotlinLogging;
import io.modelcontextprotocol.kotlin.sdk.server.Server;
import kotlin.KotlinVersion;
import kotlin.coroutines.jvm.internal.DebugProbesKt;
import kotlin.internal.jdk7.JDK7PlatformImplementations;
import kotlin.internal.jdk8.JDK8PlatformImplementations;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.future.FutureKt;

import java.util.List;

// written in Java because some of these classes are not available in Kotlin
// e.g. JDK8PlatformImplementations,DebugProbesKt, etc...
// partially copied from com.intellij.execution.process.mediator.daemon.DaemonProcessRuntimeClasspath
final class McpStdioRunnerClasspath {
  public static final List<Class<?>> CLASSPATH_CLASSES = List.of(ClassPathMarker.class, // entry point
                                                                 Server.class,
                                                                 KotlinLogging.class,
                                                                 KotlinVersion.class, // kotlin-stdlib
                                                                 JDK8PlatformImplementations.class, // kotlin-stdlib-jdk8
                                                                 JDK7PlatformImplementations.class, // kotlin-stdlib-jdk7
                                                                 CoroutineScope.class, // kotlinx-coroutines-core
                                                                 FutureKt.class, // kotlinx-coroutines-jdk8
                                                                 DebugProbesKt.class, // kotlin coroutine debug
                                                                 // ktor-client-cio-jvm
                                                                 io.ktor.client.engine.cio.CIO.class,
                                                                 // ktor-client-core-jvm
                                                                 io.ktor.client.HttpClient.class,

                                                                 // ktor-http-jvm
                                                                 io.ktor.http.HttpStatusCode.class,

                                                                 // ktor-events-jvm
                                                                 io.ktor.events.Events.class,

                                                                 // ktor-websocket-serialization-jvm
                                                                 //io.ktor.websocket.serialization.WebSocketContentConverter.class,

                                                                 // ktor-serialization-jvm
                                                                 io.ktor.serialization.ContentConverter.class,

                                                                 // ktor-sse-jvm
                                                                 io.ktor.sse.ServerSentEvent.class,

                                                                 // ktor-http-cio-jvm
                                                                 io.ktor.http.cio.HttpHeadersMap.class,

                                                                 // ktor-network-jvm
                                                                 io.ktor.network.sockets.Socket.class,

                                                                 // ktor-io-jvm
                                                                 io.ktor.utils.io.ByteReadChannel.class,

                                                                 // ktor-websockets-jvm
                                                                 io.ktor.websocket.WebSocketSession.class,

                                                                 // ktor-network-tls-jvm
                                                                 io.ktor.network.tls.TLSConfig.class,

                                                                 // ktor-utils-jvm
                                                                 io.ktor.util.AttributeKey.class,
                                                                 // kotlinx-coroutines-core
                                                                 kotlinx.coroutines.Dispatchers.class,

                                                                 // kotlinx-io-core-jvm
                                                                 kotlinx.io.Buffer.class,

                                                                 // kotlinx-io-bytestring-jvm
                                                                 kotlinx.io.bytestring.ByteString.class,

                                                                 // kotlinx-serialization-core-jvm
                                                                 kotlinx.serialization.Serializable.class,

                                                                 // kotlinx-serialization-json-jvm
                                                                 kotlinx.serialization.json.Json.class,
                                                                 // slf4j-api
                                                                 org.slf4j.Logger.class,

                                                                 // slf4j-jdk14
                                                                 org.slf4j.jul.JDK14LoggerAdapter.class);
}
