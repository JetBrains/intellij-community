package com.intellij.stats.tracking

import org.junit.Test
import org.assertj.core.api.Assertions.assertThat

class IntervalCounterTrackingTest {

    @Test
    fun `log counter`() {
        val counter = IntervalCounter(0, 10, 2.0)

        counter.register(70)
        counter.register(100)

        counter.register(200)

        counter.register(300)
        counter.register(400)
        counter.register(500)

        counter.register(600)
        counter.register(700)
        counter.register(800)
        counter.register(900)
        counter.register(1000)
        counter.register(1100)
        counter.register(1200)


        val data: Map<Int, IntervalData> = counter.intervals().filter { it.count > 0 }.associate { it.intervalEnd.toInt() to it }

        assertThat(data[128]!!.count).isEqualTo(2)
        assertThat(data[256]!!.count).isEqualTo(1)
        assertThat(data[512]!!.count).isEqualTo(3)
        assertThat(data[1024]!!.count).isEqualTo(7)
    }

}