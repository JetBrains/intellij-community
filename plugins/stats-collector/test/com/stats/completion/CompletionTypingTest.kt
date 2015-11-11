package com.stats.completion

import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.application.ApplicationManager
import org.mockito.Mockito.*
import org.picocontainer.MutablePicoContainer

class CompletionTypingTest : LightFixtureCompletionTestCase() {
    lateinit var realStorage: EventsStorage
    lateinit var mockStorage: EventsStorage
    lateinit var container: MutablePicoContainer
    
    val text = """
class Test {
    public void run() {
        Runnable r = new Runnable() {
            public void run() {}
        };
        r.<caret>
    }
}
"""

    override fun setUp() {
        super.setUp()
        container = ApplicationManager.getApplication().picoContainer as MutablePicoContainer
        mockStorage = mock(EventsStorage::class.java)
        
        val name = EventsStorage::class.java.name
        realStorage = container.getComponentInstance(name) as EventsStorage
        
        container.unregisterComponent(name)
        container.registerComponentInstance(name, mockStorage)
    }

    override fun tearDown() {
        val name = EventsStorage::class.java.name
        container.unregisterComponent(name)
        container.registerComponentInstance(name, realStorage)
        super.tearDown()
    }
    
    fun `test item selected on just typing`() {
        myFixture.addClass("interface Runnable { void run();  void notify(); void wait(); void notifyAll(); }")
        myFixture.configureByText(JavaFileType.INSTANCE, text)
        myFixture.completeBasic()
        myFixture.type("run(")
        verify(mockStorage, times(1)).itemSelected()
    }
    
    
}