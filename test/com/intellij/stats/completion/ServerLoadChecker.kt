package com.intellij.stats.completion

import com.intellij.testFramework.LightPlatformTestCase
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


class ServerLoadChecker : LightPlatformTestCase() {
    private lateinit var file: File
    private lateinit var bigText: String

    override fun setUp() {
        super.setUp()
        val builder = StringBuilder()
        for (i in 0..1024 * 1024) {
            builder.append('x')
        }
        bigText = builder.toString()
    }
    
    fun `not test server load`() {
        val executor = Executors.newFixedThreadPool(100)
        val requestService = RequestService.getInstance()
        val url = UrlProvider.getInstance().statsServerPostUrl
        
        for (i in 0..100) {
            val poster = Poster(i.toString(), url, bigText, requestService)
            executor.submit { 
                val start = System.currentTimeMillis()
                poster.send()
                val end = System.currentTimeMillis()
                println("Response time: ${end - start}")
            }
        }
        
        executor.shutdown()
        executor.awaitTermination(1, TimeUnit.MINUTES)
    }
    
    
    
}


class Poster(private val uid: String,
             private val url: String,
             private val content: String, 
             private val requestService: RequestService) {
    
    private val map = mapOf(Pair("uid", uid), Pair("content", content))
    
    fun send() {
        requestService.post(url, map)
    }
}