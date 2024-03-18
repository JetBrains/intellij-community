package com.jetbrains.performancePlugin.remotedriver.webservice

import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.registry.Registry
import com.jetbrains.performancePlugin.jmxDriver.InvokerService
import com.jetbrains.performancePlugin.remotedriver.dataextractor.TextToKeyCache
import com.jetbrains.performancePlugin.remotedriver.webservice.routing.CantFindRouteException
import com.jetbrains.performancePlugin.remotedriver.webservice.routing.Routing
import com.jetbrains.performancePlugin.remotedriver.webservice.routing.StaticFile
import com.jetbrains.performancePlugin.remotedriver.webservice.routing.route
import com.jetbrains.performancePlugin.remotedriver.xpath.XpathDataModelCreator
import com.jetbrains.performancePlugin.remotedriver.xpath.convertToHtml
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.*
import org.jetbrains.ide.RestService
import org.jetbrains.io.response
import org.jsoup.helper.W3CDom
import java.awt.Component

internal class UiHierarchyWebService : RestService() {
  init {
    if (!Registry.`is`("expose.ui.hierarchy.url")) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override fun getServiceName(): String {
    return "remote-driver"
  }

  override fun isMethodSupported(method: HttpMethod): Boolean {
    return method in listOf(HttpMethod.GET, HttpMethod.POST)
  }

  private val routing: Routing = route("/${PREFIX}/${getServiceName()}") {
    get("/") {
      hierarchy()
    }
    get("/raw") {
      rawHierarchy()
    }
    get("/{id}") {
      val id = pathParameters["id"] ?: throw IllegalStateException("id parameter is not found")
      val component = UiHierarchyWebServiceExtension.EP_NAME.extensionList.firstNotNullOf { it.getComponentById(id) }
      partialHierarchy(component)
    }
    static()
  }

  private fun partialHierarchy(component: Component): String {
    val doc = XpathDataModelCreator(TextToKeyCache) { c, e ->
      val ref = InvokerService.getInstance().invoker!!.putAdhocReference(c)
      e.setAttribute("remoteId", ref.id)
      e.setAttribute("hashCode", ref.identityHashCode.toString())
    }.create(component)
    return W3CDom().asString(doc)
  }

  private fun rawHierarchy(): String {
    val doc = XpathDataModelCreator(TextToKeyCache).create(null)
    return W3CDom().asString(doc)
  }

  private fun hierarchy(): String {
    val doc = XpathDataModelCreator(TextToKeyCache).create(null)
    return doc.convertToHtml()
  }

  override fun execute(urlDecoder: QueryStringDecoder, request: FullHttpRequest, context: ChannelHandlerContext): String? {
    try {
      val response = when (val result = routing.handleRequest(urlDecoder, request, context)) {
        is String -> response("text/html", Unpooled.wrappedBuffer(result.toByteArray()))
        is StaticFile -> response(result.type, Unpooled.wrappedBuffer(result.byteArray))
        else -> throw NotImplementedError("${result::class.java} type is not supported")
      }
      sendResponse(request, context, response)
    }
    catch (e: CantFindRouteException) {
      e.printStackTrace()
      sendStatus(HttpResponseStatus.BAD_REQUEST, HttpUtil.isKeepAlive(request), context.channel())
    }
    return null
  }
}

interface UiHierarchyWebServiceExtension {
  companion object {
    val EP_NAME = ExtensionPointName<UiHierarchyWebServiceExtension>("com.jetbrains.performancePlugin.remoteDriver.uiHierarchyExtension")
  }

  fun getComponentById(id: String): Component?
}
