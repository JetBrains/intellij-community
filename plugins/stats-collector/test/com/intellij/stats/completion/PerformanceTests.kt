package com.intellij.stats.completion

import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.testFramework.PlatformLiteFixture
import com.intellij.util.Time
import org.mockito.Matchers
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.picocontainer.MutablePicoContainer


class PerformanceTests : LightFixtureCompletionTestCase() {
    private lateinit var container: MutablePicoContainer
    private lateinit var oldFilePathProvider: FilePathProvider

    private val path = "x.txt"
    private val runnable = "interface Runnable { void run();  void notify(); void wait(); void notifyAll(); }"
    private val text = """
class Test {
    public void run() {
        Runnable r = new Runnable() {
            public void run() {}
        };
        r<caret>
    }
}
"""

    override fun setUp() {
        super.setUp()
        val pico = ApplicationManager.getApplication().picoContainer as MutablePicoContainer
        val path = createTempFile(path).absolutePath
        val mockPathProvider = mock(FilePathProvider::class.java)
        `when`(mockPathProvider.statsFilePath).thenReturn(path)

        val logFilePathManager = LogFileManagerImpl()
        val oldLogFileManager = PlatformLiteFixture.registerComponentInstance(pico, LogFileManager::class.java, logFilePathManager)
    }

    fun `test do not block EDT on logging`() {
        myFixture.configureByText("Test.java", text)


        val urlProvider = ServiceManager.getService(UrlProvider::class.java)
        val logFileManager = ServiceManager.getService(LogFileManager::class.java)
        val requestService = mock(RequestService::class.java)
        `when`(requestService.post(Matchers.anyString(), Matchers.anyMapOf(String::class.java, String::class.java))).then {
            Thread.sleep(10L * Time.SECOND)
            println("Hoyyyyyaaa")
        }.thenReturn(ResponseData(200))

        val sender = StatisticSender(urlProvider, logFileManager, requestService)

        ApplicationManager.getApplication().executeOnPooledThread {
            sender.sendStatsData("xxx")
        }


        println()



        //        ApplicationManager.getApplication().executeOnPooledThread {
        //            val logFileManager = container.getComponentInstance(LogFileManager::class.java.name) as LogFileManager
        //            logFileManager.withFileLock {
        //                //long send here
        //                val start = System.currentTimeMillis()
        //                while (start + 5L * Time.SECOND < System.currentTimeMillis()) {
        //                }
        //            }
        //        }
        //        Thread.sleep(1L * Time.SECOND)
        //        myFixture.type('.')
        //        val before = System.currentTimeMillis()
        //        myFixture.completeBasic()
        //        val after = System.currentTimeMillis()
        //        println()


    }

}
