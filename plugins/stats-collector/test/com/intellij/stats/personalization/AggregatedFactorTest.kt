package com.intellij.stats.personalization

import com.intellij.stats.personalization.impl.*
import com.intellij.testFramework.UsefulTestCase
import junit.framework.TestCase
import org.junit.Assert
import org.junit.Test
import java.util.*

/**
 * @author Vitaliy.Bibaev
 */
class AggregatedFactorTest : UsefulTestCase() {
    private companion object {
        val DATE_1 = Calendar.Builder().setDate(2010, 1, 1).build().time
        val DATE_2 = Calendar.Builder().setDate(2010, 1, 2).build().time
        val DATE_3 = Calendar.Builder().setDate(2010, 1, 3).build().time
        val DATE_4 = Calendar.Builder().setDate(2010, 1, 4).build().time
        val DATE_5 = Calendar.Builder().setDate(2010, 1, 6).build().time
    }

    fun `test min is correct`() {
        val aggregateFactor: MutableDoubleFactor = UserFactorStorageBase.DailyAggregateFactor()

        aggregateFactor.setOnDate(DateUtil.byDate(DATE_1), "count", 10.0)
        aggregateFactor.setOnDate(DateUtil.byDate(DATE_3), "count", 20.0)
        aggregateFactor.setOnDate(DateUtil.byDate(DATE_5), "delay", 1000.0)

        val mins = createFactorForTests().aggregateMin()
        TestCase.assertEquals(2, mins.size)
        UsefulTestCase.assertEquals(10.0, mins["count"])
        UsefulTestCase.assertEquals(1000.0, mins["delay"])
    }

    fun `test max is correct`() {
        val maximums = createFactorForTests().aggregateMax()
        TestCase.assertEquals(2, maximums.size)
        UsefulTestCase.assertEquals(20.0, maximums["count"])
        UsefulTestCase.assertEquals(1000.0, maximums["delay"])
    }

    fun `test sum is correct`() {
        val maximums = createFactorForTests().aggregateSum()
        TestCase.assertEquals(2, maximums.size)
        UsefulTestCase.assertEquals(30.0, maximums["count"])
        UsefulTestCase.assertEquals(1000.0, maximums["delay"])
    }

    fun `test average is only on present`() {
        val maximums = createFactorForTests().aggregateAverage()
        TestCase.assertEquals(2, maximums.size)
        UsefulTestCase.assertEquals(15.0, maximums["count"]!!, 1e-10)
        UsefulTestCase.assertEquals(1000.0, maximums["delay"]!!, 1e-10)
    }

    @Test
    fun `test average does not lose precision`() {
        val factor: MutableDoubleFactor = UserFactorStorageBase.DailyAggregateFactor()
        factor.setOnDate(DateUtil.byDate(DATE_1), "key1", Double.MAX_VALUE)
        factor.setOnDate(DateUtil.byDate(DATE_2), "key1", Double.MAX_VALUE)

        val avg = factor.aggregateAverage()
        TestCase.assertEquals(1, avg.size)
        Assert.assertNotEquals(Double.POSITIVE_INFINITY, avg["key1"]!!)
    }

    private fun createFactorForTests(): DailyAggregatedDoubleFactor {
        val aggregateFactor: MutableDoubleFactor = UserFactorStorageBase.DailyAggregateFactor()

        aggregateFactor.setOnDate(DateUtil.byDate(DATE_1), "count", 10.0)
        aggregateFactor.setOnDate(DateUtil.byDate(DATE_3), "count", 20.0)
        aggregateFactor.setOnDate(DateUtil.byDate(DATE_5), "delay", 1000.0)

        return aggregateFactor
    }
}