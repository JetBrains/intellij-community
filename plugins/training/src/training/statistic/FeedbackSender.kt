package training.statistic

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.Logger
import org.apache.http.client.fluent.Request
import org.apache.http.entity.ContentType
import org.apache.http.util.EntityUtils
import training.util.UrlProvider
import java.io.IOException
import javax.swing.SwingUtilities


class FeedbackSender(val urlProvider: UrlProvider,
                     val requestService: RequestService) {
    
    fun sendStatsData(answer: String) : Boolean {
        assertNotEDT()
        val url = urlProvider.statsServerPostUrl
        val isSentSuccessfully = sendContent(url, answer)
        return (isSentSuccessfully)
    }

    private fun assertNotEDT() {
        val isInTestMode = ApplicationManager.getApplication().isUnitTestMode
        assert(!SwingUtilities.isEventDispatchThread() || isInTestMode)
    }

    private fun sendContent(url: String, str: String): Boolean {
        val data = requestService.post(url, str)
        if (data != null && data.code >= 200 && data.code < 300) {
            return true
        }
        return false
    }
    
}


abstract class RequestService {
    abstract fun post(url: String, str: String): ResponseData?
    abstract fun get(url: String): ResponseData?
    
    companion object {
        fun getInstance() = ServiceManager.getService(RequestService::class.java)
    }
}

class SimpleRequestService: RequestService() {
    private val LOG = Logger.getInstance(SimpleRequestService::class.java)

    override fun post(url: String, str: String): ResponseData? {
        try {
            val response = Request.Post(url).bodyString(str, ContentType.TEXT_PLAIN).execute()
            val httpResponse = response.returnResponse()
            val text = EntityUtils.toString(httpResponse.entity)
            return ResponseData(httpResponse.statusLine.statusCode, text)
        }
        catch (e: IOException) {
            LOG.debug(e)
            return null
        }
    }

    override fun get(url: String): ResponseData? {
        try {
            var data: ResponseData? = null
            Request.Get(url).execute().handleResponse {
                val text = EntityUtils.toString(it.entity)
                data = ResponseData(it.statusLine.statusCode, text)
            }
            return data
        } catch (e: IOException) {
            LOG.debug(e)
            return null
        }
    }
}


data class ResponseData(val code: Int, val text: String = "") {
    
    fun isOK() = code >= 200 && code < 300
    
}