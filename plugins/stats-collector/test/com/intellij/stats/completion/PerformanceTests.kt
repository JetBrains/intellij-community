package com.intellij.stats.completion

import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.UsefulTestCase
import org.mockito.Matchers
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.picocontainer.MutablePicoContainer
import java.io.File


class PerformanceTests : LightFixtureCompletionTestCase() {
    private lateinit var container: MutablePicoContainer
    private lateinit var oldPathProvider: FilePathProvider
    private lateinit var path: String
    private lateinit var tmpPath: String
    
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
        container = ApplicationManager.getApplication().picoContainer as MutablePicoContainer
        path = createTempFile("x.txt").absolutePath
        tmpPath = createTempFile("xtmp.txt").absolutePath
        val mockPathProvider = mock(FilePathProvider::class.java)
        
        oldPathProvider = container.getComponentInstance(FilePathProvider::class.java.name) as FilePathProvider
        container.replaceComponent(FilePathProvider::class.java, mockPathProvider)
    }

    override fun tearDown() {
        container.replaceComponent(FilePathProvider::class.java, oldPathProvider)
        val file = File(path)
        if (file.exists()) {
            file.delete()
        }
        val tmpFile = File(tmpPath)
        if (tmpFile.exists()) {
            tmpFile.delete()
        }
        super.tearDown()
    }

    fun `test do not block EDT on logging`() {
        myFixture.configureByText("Test.java", text)
        myFixture.addClass(runnable)

        
        val requestService = mock(RequestService::class.java)
        val anyString = Matchers.anyString()
        val anyMap = Matchers.anyMapOf(String::class.java, String::class.java)
        `when`(requestService.post(anyString, anyMap)).then { 
            Thread.sleep(5000)
            ResponseData(200)
        }
        
        
        val file = File(path)
        file.writeText("Some existing data to send")

        val filePathProvider = mock(FilePathProvider::class.java)

        val sender = StatisticSender(requestService, filePathProvider)
        
        ApplicationManager.getApplication().executeOnPooledThread { 
            sender.sendStatsData("")
        }
        Thread.sleep(300)
        
        val start = System.currentTimeMillis()
        myFixture.type('.')
        myFixture.completeBasic()
        myFixture.type("xxxx")
        val end = System.currentTimeMillis()

        val delta = end - start
        UsefulTestCase.assertTrue("Time on typing: $delta", delta < 2000)
    }

}
