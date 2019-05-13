package com.intellij.tasks;

import com.intellij.tasks.impl.TaskUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * @author Mikhail Golubev
 */
public class DateParsingTest {
  private static final SimpleDateFormat FORMATTER = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
  static {
    FORMATTER.setTimeZone(TimeZone.getTimeZone("GMT"));
  }

  private static void compareDates(@NotNull Date expected, @NotNull String formattedDate) {
    Date parsed = TaskUtil.parseDate(formattedDate);
    assertEquals(expected, parsed);
  }

  /**
   * Test ISO8601 date parsing
   */
  @Test
  public void testDateParsings() throws Exception {
    final Date expected = FORMATTER.parse("2013-08-23 10:11:12.000");
    final Date expectedWithMillis = FORMATTER.parse("2013-08-23 10:11:12.100");
    final Date expectedDateOnly = FORMATTER.parse("2013-08-23 00:00:00.000");
    // JIRA, Redmine and Pivotal
    compareDates(expectedWithMillis, "2013-08-23T14:11:12.100+0400");
    // Trello
    compareDates(expectedWithMillis, "2013-08-23T10:11:12.100Z");
    // Assmbla
    compareDates(expectedWithMillis, "2013-08-23T14:11:12.100+04:00");

    // Formatting variations
    compareDates(expected, "2013/08/23 10:11:12");
    compareDates(expectedDateOnly, "2013-08-23");
    compareDates(expectedWithMillis, "2013-08-23 14:11:12.100123+04");
    // Possible Redmine date format, notice space before timezone
    compareDates(expected, "2013/08/23 14:11:12 +0400");


    // Malformed date
    assertNull(TaskUtil.parseDate("Fri Aug 23 14:11:12 MSK 2013"));
    assertNull(TaskUtil.parseDate("2013:00:23"));
    assertNull(TaskUtil.parseDate("2013/08/23 10:11:12 GMT+04:00"));
    assertNull(TaskUtil.parseDate("2013-08-23+0400"));
  }
}