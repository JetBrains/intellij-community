package com.intellij.stats.completion

import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.UsefulTestCase
import org.mockito.Matchers
import org.mockito.Mockito.*
import org.picocontainer.MutablePicoContainer
import java.io.File


class PerformanceTests : LightFixtureCompletionTestCase() {
    private lateinit var pathProvider: FilePathProvider
    
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
        val container = ApplicationManager.getApplication().picoContainer as MutablePicoContainer
        pathProvider = container.getComponentInstance(FilePathProvider::class.java.name) as FilePathProvider
    }

    override fun tearDown() {
        try {
            super.tearDown()
        } finally {
            CompletionLoggerProvider.getInstance().dispose()
        val statsDir = pathProvider.getStatsDataDirectory()
            statsDir.deleteRecursively()
        }
    }

    fun `test do not block EDT on logging`() {
        myFixture.configureByText("Test.java", text)
        myFixture.addClass(runnable)


        val requestService = mock(RequestService::class.java).apply {
            `when`(postZipped(anyString(), any() ?: File("."))).then {
                Thread.sleep(5000)
                ResponseData(200)
            }

            `when`(post(Matchers.anyString(), anyMapOf(String::class.java, String::class.java))).then {
                Thread.sleep(5000)
                ResponseData(200)
            }
        }

        val file = pathProvider.getUniqueFile()
        file.writeText("Some existing data to send")
        
        val sender = StatisticSender(requestService, pathProvider)
        
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
