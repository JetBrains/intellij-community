package com.jetbrains.python.console.pydev

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.util.net.NetUtils
import org.apache.xmlrpc.*
import java.net.MalformedURLException
import java.net.URL
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * Subclass of XmlRpcClient that will monitor the process so that if the process is destroyed, we stop waiting
 * for messages from it.

 * @author Fabio
 */
class PydevXmlRpcClient
/**
 * Constructor (see fields description)
 */
@Throws(MalformedURLException::class)
constructor(private val process: Process, hostname: String?, port: Int) : IPydevXmlRpcClient {


  /**
   * Internal xml-rpc client (responsible for the actual communication with the server)
   */
  private val impl: XmlRpcClient
  private val requestSynchronizer = Semaphore(1, true)


  init {
    XmlRpc.setDefaultInputEncoding("UTF8") //even though it uses UTF anyway
    impl = XmlRpcClientLite(hostname ?: NetUtils.getLocalHostString(), port)
    impl.maxThreads = 1
  }

  constructor(process: Process, port: Int) : this(process = process, hostname = null, port = port)


  override fun execute(command: String, args: Array<Any>): Any {
    return execute(command, args, TIME_LIMIT)
  }

  /**
   * Executes a command in the server.
   *
   *
   * Within this method, we should be careful about being able to return if the server dies.
   * If we wanted to have a timeout, this would be the place to add it.

   * @return the result from executing the given command in the server.
   */
  @Throws(XmlRpcException::class)
  override fun execute(command: String, args: Array<Any>, timeoutMillis: Long): Any {

    val result = arrayOf<Any?>(null)
    val started = System.currentTimeMillis()
    /*  Try to leave at least 'MIN_TIME_SLICE' time for actual execution
        if not possible then divide the wait times evenly
     */
    val semTimeout = maxOf(timeoutMillis - MIN_TIME_SLICE, MIN_TIME_SLICE / 2)
    val progress = ProgressManager.getInstance().progressIndicator ?: EmptyProgressIndicator()
    try {
      if (!requestSynchronizer.tryAcquireWithIndicator(progress, timeoutMillis = semTimeout)) {
        throw XmlRpcException(-1, "Timeout while connecting to server")
      }
    }
    catch (e: ProcessCanceledException) {
      throw e
    }
    //make an async call so that we can keep track of not actually having an answer.
    try {
      impl.executeAsync(command, Vector(Arrays.asList(*args)), object : AsyncCallback {

        override fun handleError(error: Exception, url: URL, method: String) {
          requestSynchronizer.release()
          result[0] = makeError(error.message ?: "Unknown Error")
        }

        override fun handleResult(recievedResult: Any, url: URL, method: String) {
          requestSynchronizer.release()
          result[0] = recievedResult
        }
      })
    }
    catch (t: Throwable) { // Should not show but just in case!
      requestSynchronizer.release()
      throw t
    }

    //busy loop waiting for the answer (or having the console die).
    while (result[0] == null && System.currentTimeMillis() - started < timeoutMillis) {
      progress.checkCanceled()
      val exitValue = process.waitFor(10, TimeUnit.MILLISECONDS)
      if (exitValue) {
        result[0] = makeError(String.format("Console already exited with value: %s while waiting for an answer.\n", exitValue))
        break
      }
    }
    return result[0] ?: throw XmlRpcException(-1, "Timeout while connecting to server")
  }

  fun makeError(error: String): Array<Any> {
    return arrayOf(error)
  }

  private fun Semaphore.tryAcquireWithIndicator(indicator: ProgressIndicator,
                                                timeoutMillis: Long = TIME_LIMIT,
                                                pollIntervalMillis: Long = 50): Boolean {
    indicator.checkCanceled()
    val started = System.currentTimeMillis()
    while (!this.tryAcquire(1, pollIntervalMillis, java.util.concurrent.TimeUnit.MILLISECONDS)) {
      indicator.checkCanceled()
      if (System.currentTimeMillis() - started >= timeoutMillis) {
        return false
      }
    }
    return true
  }

  companion object {

    /**
     * ItelliJ Logging
     */
    private val LOG = Logger.getInstance(PydevXmlRpcClient::class.java.name)
    private val MIN_TIME_SLICE: Long = 1000
    private val TIME_LIMIT: Long = 40000
  }
}
