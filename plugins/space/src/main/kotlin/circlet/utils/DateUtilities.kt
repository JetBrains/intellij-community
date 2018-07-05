package circlet.utils

import circlet.messages.*
import java.time.*
import java.time.format.*

fun Long.toLocalDateTime(): LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(this), ZoneId.systemDefault())

private val ABSOLUTE_DATE_TIME_FORMATTER =
    DateTimeFormatter.ofPattern(CircletBundle.message("date-time.format.absolute"))

fun LocalDateTime.formatAbsolute(): String = format(ABSOLUTE_DATE_TIME_FORMATTER)
