package circlet.utils

import circlet.messages.*
import junit.framework.*
import java.time.*
import java.time.format.*
import kotlin.test.*

class FormatDurationTest : TestCase() {
    fun test000() {
        val start = "2018-07-09 00:00:00"
        val end = "2018-07-09 00:00:00"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.few-seconds-ago")
        )
    }

    fun test001() {
        val start = "2018-07-09 00:00:00"
        val end = "2018-07-09 00:00:01"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.few-seconds-ago")
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-few-seconds")
        )
    }

    fun test002() {
        val start = "2018-07-09 00:00:00"
        val end = "2018-07-09 00:00:30"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.few-seconds-ago")
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-few-seconds")
        )
    }

    fun test003() {
        val start = "2018-07-09 00:00:00"
        val end = "2018-07-09 00:00:43"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.few-seconds-ago")
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-few-seconds")
        )
    }

    fun test004() {
        val start = "2018-07-09 00:00:00"
        val end = "2018-07-09 00:00:44"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.few-seconds-ago")
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-few-seconds")
        )
    }

    fun test005() {
        val start = "2018-07-09 00:00:00"
        val end = "2018-07-09 00:00:45"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.minute-ago")
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-minute")
        )
    }

    fun test006() {
        val start = "2018-07-09 00:00:00"
        val end = "2018-07-09 00:00:46"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.minute-ago")
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-minute")
        )
    }

    fun test007() {
        val start = "2018-07-09 00:00:00"
        val end = "2018-07-09 00:01:00"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.minute-ago")
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-minute")
        )
    }

    fun test008() {
        val start = "2018-07-09 00:00:00"
        val end = "2018-07-09 00:01:29"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.minute-ago")
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-minute")
        )
    }

    fun test009() {
        val start = "2018-07-09 00:00:00"
        val end = "2018-07-09 00:01:30"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.minutes-ago", 2)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-minutes", 2)
        )
    }

    fun test010() {
        val start = "2018-07-09 00:00:00"
        val end = "2018-07-09 00:01:31"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.minutes-ago", 2)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-minutes", 2)
        )
    }

    fun test011() {
        val start = "2018-07-09 00:00:00"
        val end = "2018-07-09 00:02:29"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.minutes-ago", 2)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-minutes", 2)
        )
    }

    fun test012() {
        val start = "2018-07-09 00:00:00"
        val end = "2018-07-09 00:02:30"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.minutes-ago", 3)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-minutes", 3)
        )
    }

    fun test013() {
        val start = "2018-07-09 00:00:00"
        val end = "2018-07-09 00:02:31"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.minutes-ago", 3)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-minutes", 3)
        )
    }

    fun test014() {
        val start = "2018-07-09 00:00:00"
        val end = "2018-07-09 00:30:00"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.minutes-ago", 30)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-minutes", 30)
        )
    }

    fun test015() {
        val start = "2018-07-09 00:00:00"
        val end = "2018-07-09 00:44:29"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.minutes-ago", 44)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-minutes", 44)
        )
    }

    fun test016() {
        val start = "2018-07-09 00:00:00"
        val end = "2018-07-09 00:44:30"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.hour-ago")
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-hour")
        )
    }

    fun test017() {
        val start = "2018-07-09 00:00:00"
        val end = "2018-07-09 00:44:31"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.hour-ago")
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-hour")
        )
    }

    fun test018() {
        val start = "2018-07-09 00:00:00"
        val end = "2018-07-09 00:45:00"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.hour-ago")
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-hour")
        )
    }

    fun test019() {
        val start = "2018-07-09 00:00:00"
        val end = "2018-07-09 01:00:00"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.hour-ago")
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-hour")
        )
    }

    fun test020() {
        val start = "2018-07-09 00:00:00"
        val end = "2018-07-09 01:29:59"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.hour-ago")
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-hour")
        )
    }

    fun test021() {
        val start = "2018-07-09 00:00:00"
        val end = "2018-07-09 01:30:00"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.hours-ago", 2)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-hours", 2)
        )
    }

    fun test022() {
        val start = "2018-07-09 00:00:00"
        val end = "2018-07-09 02:29:59"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.hours-ago", 2)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-hours", 2)
        )
    }

    fun test023() {
        val start = "2018-07-09 00:00:00"
        val end = "2018-07-09 02:30:00"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.hours-ago", 3)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-hours", 3)
        )
    }

    fun test024() {
        val start = "2018-07-09 00:00:00"
        val end = "2018-07-09 12:00:00"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.hours-ago", 12)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-hours", 12)
        )
    }

    fun test025() {
        val start = "2018-07-09 00:00:00"
        val end = "2018-07-09 21:29:59"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.hours-ago", 21)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-hours", 21)
        )
    }

    fun test026() {
        val start = "2018-07-09 00:00:00"
        val end = "2018-07-09 21:30:00"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.day-ago")
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-day")
        )
    }

    fun test027() {
        val start = "2018-07-09 00:00:00"
        val end = "2018-07-09 21:59:59"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.day-ago")
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-day")
        )
    }

    fun test028() {
        val start = "2018-07-09 00:00:00"
        val end = "2018-07-09 22:00:00"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.day-ago")
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-day")
        )
    }

    fun test029() {
        val start = "2018-07-09 00:00:00"
        val end = "2018-07-09 23:59:59"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.day-ago")
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-day")
        )
    }

    fun test030() {
        val start = "2018-07-09 00:00:00"
        val end = "2018-07-10 00:00:00"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.day-ago")
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-day")
        )
    }

    fun test031() {
        val start = "2018-07-09 00:00:00"
        val end = "2018-07-10 11:59:59"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.day-ago")
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-day")
        )
    }

    fun test032() {
        val start = "2018-07-09 00:00:00"
        val end = "2018-07-10 12:00:00"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.days-ago", 2)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-days", 2)
        )
    }

    fun test033() {
        val start = "2018-07-09 00:00:00"
        val end = "2018-07-11 00:00:00"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.days-ago", 2)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-days", 2)
        )
    }

    fun test034() {
        val start = "2018-07-09 00:00:00"
        val end = "2018-07-11 11:59:59"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.days-ago", 2)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-days", 2)
        )
    }

    fun test035() {
        val start = "2018-07-09 00:00:00"
        val end = "2018-07-11 12:00:00"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.days-ago", 3)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-days", 3)
        )
    }

    fun test036() {
        val start = "2018-07-09 00:00:00"
        val end = "2018-08-01 00:00:00"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.days-ago", 23)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-days", 23)
        )
    }

    fun test037() {
        val start = "2018-07-09 00:00:00"
        val end = "2018-08-01 11:59:59"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.days-ago", 23)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-days", 23)
        )
    }

    fun test038() {
        val start = "2018-07-09 00:00:00"
        val end = "2018-08-01 12:00:00"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.days-ago", 24)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-days", 24)
        )
    }

    fun test039() {
        val start = "2018-07-09 00:00:00"
        val end = "2018-08-03 00:00:00"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.days-ago", 25)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-days", 25)
        )
    }

    fun test040() {
        val start = "2018-02-28 00:00:00"
        val end = "2018-03-25 00:00:00"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.days-ago", 25)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-days", 25)
        )
    }

    fun test041() {
        val start = "2018-07-09 00:00:00"
        val end = "2018-08-03 11:59:59"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.days-ago", 25)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-days", 25)
        )
    }

    fun test042() {
        val start = "2018-07-09 00:00:00"
        val end = "2018-08-03 12:00:00"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.month-ago")
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-month")
        )
    }

    fun test043() {
        val start = "2018-02-28 00:00:00"
        val end = "2018-03-25 11:59:59"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.days-ago", 25)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-days", 25)
        )
    }

    fun test044() {
        val start = "2018-02-28 00:00:00"
        val end = "2018-03-25 12:00:00"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.month-ago")
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-month")
        )
    }

    fun test045() {
        val start = "2018-07-09 00:00:00"
        val end = "2018-08-04 00:00:00"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.month-ago")
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-month")
        )
    }

    fun test046() {
        val start = "2018-07-09 00:00:00"
        val end = "2018-08-09 00:00:00"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.month-ago")
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-month")
        )
    }

    fun test047() {
        val start = "2018-07-09 00:00:00"
        val end = "2018-08-24 00:00:00"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.month-ago")
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-month")
        )
    }

    fun test048() {
        val start = "2018-06-09 00:00:00"
        val end = "2018-07-24 00:00:00"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.month-ago")
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-month")
        )
    }

    fun test049() {
        val start = "2018-02-28 00:00:00"
        val end = "2018-03-26 00:00:00"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.month-ago")
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-month")
        )
    }

    fun test050() {
        val start = "2018-02-28 00:00:00"
        val end = "2018-04-12 00:00:00"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.month-ago")
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-month")
        )
    }

    fun test051() {
        val start = "2016-02-28 00:00:00"
        val end = "2016-04-12 00:00:00"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.month-ago")
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-month")
        )
    }

    fun test052() {
        val start = "2018-07-09 00:00:00"
        val end = "2018-08-24 23:59:59" // moment.js: 2018-08-24 05:14:32

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.month-ago")
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-month")
        )
    }

    fun test053() {
        val start = "2018-07-09 00:00:00"
        val end = "2018-08-25 00:00:00" // moment.js: 2018-08-24 05:14:33

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.months-ago", 2)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-months", 2)
        )
    }

    fun test054() {
        val start = "2018-06-09 00:00:00"
        val end = "2018-07-24 23:59:59" // moment.js: 2018-07-24 05:14:32

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.month-ago")
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-month")
        )
    }

    fun test055() {
        val start = "2018-06-09 00:00:00"
        val end = "2018-07-25 00:00:00" // moment.js: 2018-07-24 05:14:33

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.months-ago", 2)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-months", 2)
        )
    }

    fun test056() {
        val start = "2018-02-28 00:00:00"
        val end = "2018-04-12 23:59:59" // moment.js: 2018-04-12 05:14:32

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.month-ago")
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-month")
        )
    }

    fun test057() {
        val start = "2018-02-28 00:00:00"
        val end = "2018-04-13 00:00:00" // moment.js: 2018-04-12 05:14:33

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.months-ago", 2)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-months", 2)
        )
    }

    fun test058() {
        val start = "2016-02-28 00:00:00"
        val end = "2016-04-12 23:59:59" // moment.js: 2016-04-12 05:14:32

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.month-ago")
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-month")
        )
    }

    fun test059() {
        val start = "2016-02-28 00:00:00"
        val end = "2016-04-13 00:00:00" // moment.js: 2016-04-12 05:14:33

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.months-ago", 2)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-months", 2)
        )
    }

    fun test060() {
        val start = "2018-07-09 00:00:00"
        val end = "2018-09-23 23:59:59" // moment.js: 2018-09-24 05:14:32

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.months-ago", 2)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-months", 2)
        )
    }

    fun test061() {
        val start = "2018-07-09 00:00:00"
        val end = "2018-09-24 00:00:00" // moment.js: 2018-09-24 05:14:33

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.months-ago", 3)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-months", 3)
        )
    }

    fun test062() {
        val start = "2018-06-09 00:00:00"
        val end = "2018-08-24 23:59:59" // moment.js: 2018-08-24 05:14:32

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.months-ago", 2)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-months", 2)
        )
    }

    fun test063() {
        val start = "2018-06-09 00:00:00"
        val end = "2018-08-25 00:00:00" // moment.js: 2018-08-24 05:14:33

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.months-ago", 3)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-months", 3)
        )
    }

    fun test064() {
        val start = "2018-02-28 00:00:00"
        val end = "2018-05-12 23:59:59" // moment.js: 2018-05-13 05:14:32

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.months-ago", 2)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-months", 2)
        )
    }

    fun test065() {
        val start = "2018-02-28 00:00:00"
        val end = "2018-05-13 00:00:00" // moment.js: 2018-05-13 05:14:33

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.months-ago", 3)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-months", 3)
        )
    }

    fun test066() {
        val start = "2016-02-28 00:00:00"
        val end = "2016-05-12 23:59:59" // moment.js: 2016-05-13 05:14:32

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.months-ago", 2)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-months", 2)
        )
    }

    fun test067() {
        val start = "2016-02-28 00:00:00"
        val end = "2016-05-13 00:00:00" // moment.js: 2016-05-13 05:14:33

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.months-ago", 3)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-months", 3)
        )
    }

    fun test068() {
        val start = "2018-07-09 00:00:00"
        val end = "2019-01-24 23:59:59" // moment.js: 2019-01-24 05:14:32

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.months-ago", 6)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-months", 6)
        )
    }

    fun test069() {
        val start = "2018-07-09 00:00:00"
        val end = "2019-01-25 00:00:00" // moment.js: 2019-01-24 05:14:33

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.months-ago", 7)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-months", 7)
        )
    }

    fun test070() {
        val start = "2018-06-09 00:00:00"
        val end = "2018-12-24 23:59:59" // moment.js: 2018-12-24 05:14:32

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.months-ago", 6)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-months", 6)
        )
    }

    fun test071() {
        val start = "2018-06-09 00:00:00"
        val end = "2018-12-25 00:00:00" // moment.js: 2018-12-24 05:14:33

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.months-ago", 7)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-months", 7)
        )
    }

    fun test072() {
        val start = "2018-02-28 00:00:00"
        val end = "2018-09-12 23:59:59" // moment.js: 2018-09-12 05:14:32

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.months-ago", 6)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-months", 6)
        )
    }

    fun test073() {
        val start = "2018-02-28 00:00:00"
        val end = "2018-09-13 00:00:00" // moment.js: 2018-09-12 05:14:33

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.months-ago", 7)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-months", 7)
        )
    }

    fun test074() {
        val start = "2016-02-28 00:00:00"
        val end = "2016-09-12 23:59:59" // moment.js: 2016-09-12 05:14:32

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.months-ago", 6)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-months", 6)
        )
    }

    fun test075() {
        val start = "2016-02-28 00:00:00"
        val end = "2016-09-13 00:00:00" // moment.js: 2016-09-12 05:14:33

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.months-ago", 7)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-months", 7)
        )
    }

    fun test076() {
        val start = "2018-01-01 00:00:00"
        val end = "2018-07-16 23:59:59" // moment.js: 2018-07-16 05:14:32

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.months-ago", 6)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-months", 6)
        )
    }

    fun test077() {
        val start = "2018-01-01 00:00:00"
        val end = "2018-07-17 00:00:00" // moment.js: 2018-07-16 05:14:33

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.months-ago", 7)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-months", 7)
        )
    }

    fun test078() {
        val start = "2016-01-01 00:00:00"
        val end = "2016-07-16 23:59:59" // moment.js: 2016-07-16 05:14:32

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.months-ago", 6)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-months", 6)
        )
    }

    fun test079() {
        val start = "2016-01-01 00:00:00"
        val end = "2016-07-17 00:00:00" // moment.js: 2016-07-16 05:14:33

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.months-ago", 7)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-months", 7)
        )
    }

    fun test080() {
        val start = "2018-07-09 00:00:00"
        val end = "2019-05-24 23:59:59" // moment.js: 2019-05-24 05:14:32

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.months-ago", 10)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-months", 10)
        )
    }

    fun test081() {
        val start = "2018-07-09 00:00:00"
        val end = "2019-05-25 00:00:00" // moment.js: 2019-05-24 05:14:33

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.year-ago")
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-year")
        )
    }

    fun test082() {
        val start = "2018-06-09 00:00:00"
        val end = "2019-04-23 23:59:59" // moment.js: 2019-04-24 05:14:32

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.months-ago", 10)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-months", 10)
        )
    }

    fun test083() {
        val start = "2018-06-09 00:00:00"
        val end = "2019-04-24 00:00:00" // moment.js: 2019-04-24 05:14:33

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.year-ago")
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-year")
        )
    }

    fun test084() {
        val start = "2018-02-28 00:00:00"
        val end = "2019-01-12 23:59:59" // moment.js: 2019-01-12 05:14:32

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.months-ago", 10)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-months", 10)
        )
    }

    fun test085() {
        val start = "2018-02-28 00:00:00"
        val end = "2019-01-13 00:00:00" // moment.js: 2019-01-12 05:14:33

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.year-ago")
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-year")
        )
    }

    fun test086() {
        val start = "2016-02-28 00:00:00"
        val end = "2017-01-12 23:59:59" // moment.js: 2017-01-12 05:14:32

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.months-ago", 10)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-months", 10)
        )
    }

    fun test087() {
        val start = "2016-02-28 00:00:00"
        val end = "2017-01-13 00:00:00" // moment.js: 2017-01-12 05:14:33

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.year-ago")
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-year")
        )
    }

    fun test088() {
        val start = "2018-01-01 00:00:00"
        val end = "2018-11-15 23:59:59" // moment.js: 2018-11-16 05:14:32

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.months-ago", 10)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-months", 10)
        )
    }

    fun test089() {
        val start = "2018-01-01 00:00:00"
        val end = "2018-11-16 00:00:00" // moment.js: 2018-11-16 05:14:33

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.year-ago")
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-year")
        )
    }

    fun test090() {
        val start = "2016-01-01 00:00:00"
        val end = "2016-11-15 23:59:59" // moment.js: 2016-11-16 05:14:32

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.months-ago", 10)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-months", 10)
        )
    }

    fun test091() {
        val start = "2016-01-01 00:00:00"
        val end = "2016-11-16 00:00:00" // moment.js: 2016-11-16 05:14:33

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.year-ago")
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-year")
        )
    }

    fun test092() {
        val start = "2018-07-09 00:00:00"
        val end = "2019-07-09 00:00:00"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.year-ago")
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-year")
        )
    }

    fun test093() {
        val start = "2018-06-09 00:00:00"
        val end = "2019-06-09 00:00:00"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.year-ago")
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-year")
        )
    }

    fun test094() {
        val start = "2018-02-28 00:00:00"
        val end = "2019-02-28 00:00:00"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.year-ago")
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-year")
        )
    }

    fun test095() {
        val start = "2016-02-28 00:00:00"
        val end = "2017-02-28 00:00:00"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.year-ago")
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-year")
        )
    }

    fun test096() {
        val start = "2018-01-01 00:00:00"
        val end = "2019-01-01 00:00:00"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.year-ago")
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-year")
        )
    }

    fun test097() {
        val start = "2016-01-01 00:00:00"
        val end = "2017-01-01 00:00:00"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.year-ago")
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-year")
        )
    }

    fun test098() {
        val start = "2018-07-09 00:00:00"
        val end = "2020-01-08 23:59:59" // moment.js: 2020-01-08 10:29:05

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.year-ago")
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-year")
        )
    }

    fun test099() {
        val start = "2018-07-09 00:00:00"
        val end = "2020-01-09 00:00:00" // moment.js: 2020-01-08 10:29:06

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 2)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 2)
        )
    }

    fun test100() {
        val start = "2018-06-09 00:00:00"
        val end = "2019-12-08 23:59:59"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.year-ago")
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-year")
        )
    }

    fun test101() {
        val start = "2018-06-09 00:00:00"
        val end = "2019-12-09 00:00:00"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 2)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 2)
        )
    }

    fun test102() {
        val start = "2018-02-28 00:00:00"
        val end = "2019-08-27 23:59:59" // moment.js: 2019-08-27 10:29:05

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.year-ago")
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-year")
        )
    }

    fun test103() {
        val start = "2018-02-28 00:00:00"
        val end = "2019-08-28 00:00:00" // moment.js: 2019-08-27 10:29:06

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 2)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 2)
        )
    }

    fun test104() {
        val start = "2016-02-28 00:00:00"
        val end = "2017-08-27 23:59:59" // moment.js: 2017-08-27 10:29:05

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.year-ago")
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-year")
        )
    }

    fun test105() {
        val start = "2016-02-28 00:00:00"
        val end = "2017-08-28 00:00:00" // moment.js: 2017-08-27 10:29:06

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 2)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 2)
        )
    }

    fun test106() {
        val start = "2018-01-01 00:00:00"
        val end = "2019-06-30 23:59:59"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.year-ago")
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-year")
        )
    }

    fun test107() {
        val start = "2018-01-01 00:00:00"
        val end = "2019-07-01 00:00:00"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 2)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 2)
        )
    }

    fun test108() {
        val start = "2016-01-01 00:00:00"
        val end = "2017-06-30 23:59:59"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.year-ago")
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-year")
        )
    }

    fun test109() {
        val start = "2016-01-01 00:00:00"
        val end = "2017-07-01 00:00:00"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 2)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 2)
        )
    }

    fun test110() {
        val start = "2017-09-09 00:00:00"
        val end = "2019-03-08 23:59:59"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.year-ago")
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-year")
        )
    }

    fun test111() {
        val start = "2017-09-09 00:00:00"
        val end = "2019-03-09 00:00:00"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 2)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 2)
        )
    }

    fun test112() {
        val start = "2016-09-09 00:00:00"
        val end = "2018-03-08 23:59:59"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.year-ago")
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-year")
        )
    }

    fun test113() {
        val start = "2016-09-09 00:00:00"
        val end = "2018-03-09 00:00:00"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 2)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 2)
        )
    }

    fun test114() {
        val start = "2014-09-09 00:00:00"
        val end = "2016-03-08 23:59:59"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.year-ago")
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-year")
        )
    }

    fun test115() {
        val start = "2014-09-09 00:00:00"
        val end = "2016-03-09 00:00:00"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 2)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 2)
        )
    }

    fun test116() {
        val start = "2018-07-25 00:00:00"
        val end = "2020-01-24 23:59:59" // moment.js: 2020-01-08 10:29:05

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.year-ago")
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-year")
        )
    }

    fun test117() {
        val start = "2018-07-25 00:00:00"
        val end = "2020-01-25 00:00:00" // moment.js: 2020-01-08 10:29:06

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 2)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 2)
        )
    }

    fun test118() {
        val start = "2018-07-09 00:00:00"
        val end = "2021-01-08 23:59:59" // moment.js: 2021-01-08 10:29:05

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 2)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 2)
        )
    }

    fun test119() {
        val start = "2018-07-09 00:00:00"
        val end = "2021-01-09 00:00:00" // moment.js: 2021-01-08 10:29:06

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 3)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 3)
        )
    }

    fun test120() {
        val start = "2018-06-09 00:00:00"
        val end = "2020-12-08 23:59:59"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 2)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 2)
        )
    }

    fun test121() {
        val start = "2018-06-09 00:00:00"
        val end = "2020-12-09 00:00:00"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 3)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 3)
        )
    }

    fun test122() {
        val start = "2018-02-28 00:00:00"
        val end = "2020-08-27 23:59:59" // moment.js: 2020-08-27 10:29:05

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 2)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 2)
        )
    }

    fun test123() {
        val start = "2018-02-28 00:00:00"
        val end = "2020-08-28 00:00:00" // moment.js: 2020-08-27 10:29:06

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 3)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 3)
        )
    }

    fun test124() {
        val start = "2016-02-28 00:00:00"
        val end = "2018-08-27 23:59:59" // moment.js: 2018-08-27 10:29:05

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 2)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 2)
        )
    }

    fun test125() {
        val start = "2016-02-28 00:00:00"
        val end = "2018-08-28 00:00:00" // moment.js: 2018-08-27 10:29:06

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 3)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 3)
        )
    }

    fun test126() {
        val start = "2018-01-01 00:00:00"
        val end = "2020-06-30 23:59:59"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 2)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 2)
        )
    }

    fun test127() {
        val start = "2018-01-01 00:00:00"
        val end = "2020-07-01 00:00:00"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 3)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 3)
        )
    }

    fun test128() {
        val start = "2016-01-01 00:00:00"
        val end = "2018-06-30 23:59:59"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 2)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 2)
        )
    }

    fun test129() {
        val start = "2016-01-01 00:00:00"
        val end = "2018-07-01 00:00:00"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 3)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 3)
        )
    }

    fun test130() {
        val start = "2017-09-09 00:00:00"
        val end = "2020-03-08 23:59:59"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 2)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 2)
        )
    }

    fun test131() {
        val start = "2017-09-09 00:00:00"
        val end = "2020-03-09 00:00:00"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 3)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 3)
        )
    }

    fun test132() {
        val start = "2016-09-09 00:00:00"
        val end = "2019-03-08 23:59:59"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 2)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 2)
        )
    }

    fun test133() {
        val start = "2016-09-09 00:00:00"
        val end = "2019-03-09 00:00:00"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 3)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 3)
        )
    }

    fun test134() {
        val start = "2014-09-09 00:00:00"
        val end = "2017-03-08 23:59:59"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 2)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 2)
        )
    }

    fun test135() {
        val start = "2014-09-09 00:00:00"
        val end = "2017-03-09 00:00:00"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 3)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 3)
        )
    }

    fun test136() {
        val start = "2018-07-25 00:00:00"
        val end = "2021-01-24 23:59:59" // moment.js: 2021-01-08 10:29:05

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 2)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 2)
        )
    }

    fun test137() {
        val start = "2018-07-25 00:00:00"
        val end = "2021-01-25 00:00:00" // moment.js: 2021-01-08 10:29:06

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 3)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 3)
        )
    }

    fun test138() {
        val start = "2018-07-09 00:00:00"
        val end = "2025-01-08 23:59:59" // moment.js: 2025-01-08 10:29:05

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 6)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 6)
        )
    }

    fun test139() {
        val start = "2018-07-09 00:00:00"
        val end = "2025-01-09 00:00:00" // moment.js: 2025-01-08 10:29:06

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 7)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 7)
        )
    }

    fun test140() {
        val start = "2018-06-09 00:00:00"
        val end = "2024-12-08 23:59:59"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 6)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 6)
        )
    }

    fun test141() {
        val start = "2018-06-09 00:00:00"
        val end = "2024-12-09 00:00:00"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 7)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 7)
        )
    }

    fun test142() {
        val start = "2018-02-28 00:00:00"
        val end = "2024-08-27 23:59:59" // moment.js: 2024-08-27 10:29:05

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 6)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 6)
        )
    }

    fun test143() {
        val start = "2018-02-28 00:00:00"
        val end = "2024-08-28 00:00:00" // moment.js: 2024-08-27 10:29:06

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 7)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 7)
        )
    }

    fun test144() {
        val start = "2016-02-28 00:00:00"
        val end = "2022-08-27 23:59:59" // moment.js: 2022-08-27 10:29:05

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 6)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 6)
        )
    }

    fun test145() {
        val start = "2016-02-28 00:00:00"
        val end = "2022-08-28 00:00:00" // moment.js: 2022-08-27 10:29:06

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 7)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 7)
        )
    }

    fun test146() {
        val start = "2018-01-01 00:00:00"
        val end = "2024-06-30 23:59:59"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 6)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 6)
        )
    }

    fun test147() {
        val start = "2018-01-01 00:00:00"
        val end = "2024-07-01 00:00:00"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 7)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 7)
        )
    }

    fun test148() {
        val start = "2016-01-01 00:00:00"
        val end = "2022-06-30 23:59:59"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 6)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 6)
        )
    }

    fun test149() {
        val start = "2016-01-01 00:00:00"
        val end = "2022-07-01 00:00:00"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 7)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 7)
        )
    }

    fun test150() {
        val start = "2017-09-09 00:00:00"
        val end = "2024-03-08 23:59:59"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 6)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 6)
        )
    }

    fun test151() {
        val start = "2017-09-09 00:00:00"
        val end = "2024-03-09 00:00:00"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 7)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 7)
        )
    }

    fun test152() {
        val start = "2016-09-09 00:00:00"
        val end = "2023-03-08 23:59:59"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 6)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 6)
        )
    }

    fun test153() {
        val start = "2016-09-09 00:00:00"
        val end = "2023-03-09 00:00:00"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 7)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 7)
        )
    }

    fun test154() {
        val start = "2014-09-09 00:00:00"
        val end = "2021-03-08 23:59:59"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 6)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 6)
        )
    }

    fun test155() {
        val start = "2014-09-09 00:00:00"
        val end = "2021-03-09 00:00:00"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 7)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 7)
        )
    }

    fun test156() {
        val start = "2018-07-25 00:00:00"
        val end = "2025-01-24 23:59:59" // moment.js: 2025-01-08 10:29:05

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 6)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 6)
        )
    }

    fun test157() {
        val start = "2018-07-25 00:00:00"
        val end = "2025-01-25 00:00:00" // moment.js: 2025-01-08 10:29:06

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 7)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 7)
        )
    }

    fun test158() {
        val start = "2018-07-09 00:00:00"
        val end = "2039-01-08 23:59:59" // moment.js: 2039-01-08 10:29:05

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 20)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 20)
        )
    }

    fun test159() {
        val start = "2018-07-09 00:00:00"
        val end = "2039-01-09 00:00:00" // moment.js: 2039-01-08 10:29:06

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 21)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 21)
        )
    }

    fun test160() {
        val start = "2018-06-09 00:00:00"
        val end = "2038-12-08 23:59:59"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 20)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 20)
        )
    }

    fun test161() {
        val start = "2018-06-09 00:00:00"
        val end = "2038-12-09 00:00:00"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 21)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 21)
        )
    }

    fun test162() {
        val start = "2018-02-28 00:00:00"
        val end = "2038-08-27 23:59:59" // moment.js: 2038-08-27 10:29:05

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 20)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 20)
        )
    }

    fun test163() {
        val start = "2018-02-28 00:00:00"
        val end = "2038-08-28 00:00:00" // moment.js: 2038-08-27 10:29:06

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 21)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 21)
        )
    }

    fun test164() {
        val start = "2016-02-28 00:00:00"
        val end = "2036-08-27 23:59:59" // moment.js: 2036-08-27 10:29:05

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 20)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 20)
        )
    }

    fun test165() {
        val start = "2016-02-28 00:00:00"
        val end = "2036-08-28 00:00:00" // moment.js: 2036-08-27 10:29:06

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 21)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 21)
        )
    }

    fun test166() {
        val start = "2018-01-01 00:00:00"
        val end = "2038-06-30 23:59:59"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 20)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 20)
        )
    }

    fun test167() {
        val start = "2018-01-01 00:00:00"
        val end = "2038-07-01 00:00:00"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 21)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 21)
        )
    }

    fun test168() {
        val start = "2016-01-01 00:00:00"
        val end = "2036-06-30 23:59:59"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 20)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 20)
        )
    }

    fun test169() {
        val start = "2016-01-01 00:00:00"
        val end = "2036-07-01 00:00:00"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 21)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 21)
        )
    }

    fun test170() {
        val start = "2017-09-09 00:00:00"
        val end = "2038-03-08 23:59:59"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 20)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 20)
        )
    }

    fun test171() {
        val start = "2017-09-09 00:00:00"
        val end = "2038-03-09 00:00:00"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 21)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 21)
        )
    }

    fun test172() {
        val start = "2016-09-09 00:00:00"
        val end = "2037-03-08 23:59:59"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 20)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 20)
        )
    }

    fun test173() {
        val start = "2016-09-09 00:00:00"
        val end = "2037-03-09 00:00:00"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 21)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 21)
        )
    }

    fun test174() {
        val start = "2014-09-09 00:00:00"
        val end = "2035-03-08 23:59:59"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 20)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 20)
        )
    }

    fun test175() {
        val start = "2014-09-09 00:00:00"
        val end = "2035-03-09 00:00:00"

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 21)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 21)
        )
    }

    fun test176() {
        val start = "2018-07-25 00:00:00"
        val end = "2039-01-24 23:59:59" // moment.js: 2039-01-08 10:29:05

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 20)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 20)
        )
    }

    fun test177() {
        val start = "2018-07-25 00:00:00"
        val end = "2039-01-25 00:00:00" // moment.js: 2039-01-08 10:29:06

        testFormatDuration(
            start, end, CircletBundle.message("date-time.format.difference.years-ago", 21)
        )

        testFormatDuration(
            end, start, CircletBundle.message("date-time.format.difference.in-years", 21)
        )
    }
}

private val DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")

private fun testFormatDuration(start: String, end: String, expected: String) {
    assertEquals(expected, formatDuration(start.toLocalDateTime(), end.toLocalDateTime()))
}

private fun String.toLocalDateTime(): LocalDateTime = LocalDateTime.parse(this, DATE_TIME_FORMATTER)
