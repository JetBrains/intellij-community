package circlet.utils

import circlet.messages.*
import java.time.*
import java.time.format.*

fun Long.toLocalDateTime(): LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(this), ZoneId.systemDefault())

fun LocalDateTime.formatAbsolute(): String = "${formatOnDateMonth3DayYear4()} ${formatAtTimeHour2Minute2()}"

fun LocalDateTime.formatRelative(days: Long = 13): String {
    val now = LocalDateTime.now()
    val duration = Duration.between(this, now)

    return if (duration.toDays() > days) {
        if (year == now.year) {
            formatOnDateMonth3Day()
        }
        else {
            formatOnDateMonth3DayYear4()
        }
    }
    else {
        MomentJSLikeDuration(this, now, duration).format()
    }
}

fun formatDuration(start: LocalDateTime, end: LocalDateTime): String = MomentJSLikeDuration(start, end).format()

private val MONTH3_DAY_YEAR4_DATE_TIME_FORMATTER by lazy {
    DateTimeFormatter.ofPattern("MMM d, uuuu")
}

private val MONTH3_DAY_DATE_TIME_FORMATTER by lazy {
    DateTimeFormatter.ofPattern("MMM d")
}

private val HOUR2_MINUTE2_DATE_TIME_FORMATTER by lazy {
    DateTimeFormatter.ofPattern("HH:mm")
}

private class MomentJSLikeDuration(
    private val start: LocalDateTime,
    private val end: LocalDateTime,
    originalDuration: Duration
) {
    val positive: Boolean = !originalDuration.isNegative

    private val duration: Duration = originalDuration.abs()

    val seconds: Long get() = duration.seconds

    private val fullMinutes: Long by lazy { duration.toMinutes() }
    val minutes: Long by lazy { fullMinutes + if (seconds % 60 < 30) 0 else 1 }

    private val fullHours: Long by lazy { duration.toHours() }
    val hours: Long by lazy { fullHours + if (fullMinutes % 60 < 30) 0 else 1 }

    val days: Long by lazy { duration.toDays() + if (fullHours % 24 < 12) 0 else 1 }

    private val absoluteStartDate: LocalDate get() = (if (positive) start else end).toLocalDate()
    private val absoluteEndDate: LocalDate get() = (if (positive) end else start).toLocalDate()
    private val period: Period by lazy { Period.between(absoluteStartDate, absoluteEndDate) }

    val months: Long by lazy {
        val fullAdditionalDays = period.days
        val fullMonths = period.toTotalMonths()

        fullMonths +
            if (fullAdditionalDays < 14) {
                0
            }
            else {
                val nextFullMonthLackingPeriod = Period.between(
                    absoluteEndDate, absoluteStartDate.plusMonths(fullMonths + 1)
                )

                if (nextFullMonthLackingPeriod.days > fullAdditionalDays) 0 else 1
            }
    }

    val years: Int by lazy { period.years + if (period.months < 6) 0 else 1 }

    constructor(start: LocalDateTime, end: LocalDateTime) : this(start, end, Duration.between(start, end))
}

private fun LocalDateTime.formatOnDateMonth3DayYear4(): String = formatOnDate(MONTH3_DAY_YEAR4_DATE_TIME_FORMATTER)

private fun LocalDateTime.formatOnDateMonth3Day(): String = formatOnDate(MONTH3_DAY_DATE_TIME_FORMATTER)

private fun LocalDateTime.formatOnDate(formatter: DateTimeFormatter): String =
    CircletBundle.message("date-time.format.on-date", format(formatter))

private fun LocalDateTime.formatAtTimeHour2Minute2(): String = formatAtTime(HOUR2_MINUTE2_DATE_TIME_FORMATTER)

private fun LocalDateTime.formatAtTime(formatter: DateTimeFormatter): String =
    CircletBundle.message("date-time.format.at-time", format(formatter))

private fun MomentJSLikeDuration.format(): String {
    if (seconds < 45) {
        return if (positive) {
            CircletBundle.message("date-time.format.difference.few-seconds-ago")
        }
        else {
            CircletBundle.message("date-time.format.difference.in-few-seconds")
        }
    }

    if (minutes <= 1) {
        return if (positive) {
            CircletBundle.message("date-time.format.difference.minute-ago")
        }
        else {
            CircletBundle.message("date-time.format.difference.in-minute")
        }
    }

    if (minutes < 45) {
        return if (positive) {
            CircletBundle.message("date-time.format.difference.minutes-ago", minutes)
        }
        else {
            CircletBundle.message("date-time.format.difference.in-minutes", minutes)
        }
    }

    if (hours <= 1) {
        return if (positive) {
            CircletBundle.message("date-time.format.difference.hour-ago")
        }
        else {
            CircletBundle.message("date-time.format.difference.in-hour")
        }
    }

    if (hours < 22) {
        return if (positive) {
            CircletBundle.message("date-time.format.difference.hours-ago", hours)
        }
        else {
            CircletBundle.message("date-time.format.difference.in-hours", hours)
        }
    }

    if (days <= 1) {
        return if (positive) {
            CircletBundle.message("date-time.format.difference.day-ago")
        }
        else {
            CircletBundle.message("date-time.format.difference.in-day")
        }
    }

    if (days < 26) {
        return if (positive) {
            CircletBundle.message("date-time.format.difference.days-ago", days)
        }
        else {
            CircletBundle.message("date-time.format.difference.in-days", days)
        }
    }

    if (months <= 1) {
        return if (positive) {
            CircletBundle.message("date-time.format.difference.month-ago")
        }
        else {
            CircletBundle.message("date-time.format.difference.in-month")
        }
    }

    if (months < 11) {
        return if (positive) {
            CircletBundle.message("date-time.format.difference.months-ago", months)
        }
        else {
            CircletBundle.message("date-time.format.difference.in-months", months)
        }
    }

    if (years <= 1) {
        return if (positive) {
            CircletBundle.message("date-time.format.difference.year-ago")
        }
        else {
            CircletBundle.message("date-time.format.difference.in-year")
        }
    }

    return if (positive) {
        CircletBundle.message("date-time.format.difference.years-ago", years)
    }
    else {
        CircletBundle.message("date-time.format.difference.in-years", years)
    }
}
