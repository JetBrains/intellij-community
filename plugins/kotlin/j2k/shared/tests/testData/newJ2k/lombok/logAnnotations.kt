// !ADD_LOMBOK_ANNOTATIONS
// KTIJ-12602: the Kotlin Lombok compiler plugin adds the logger field, so J2K must keep each annotation
package test

import lombok.extern.apachecommons.CommonsLog
import lombok.extern.flogger.Flogger
import lombok.extern.java.Log
import lombok.extern.jbosslog.JBossLog
import lombok.extern.log4j.Log4j
import lombok.extern.log4j.Log4j2
import lombok.extern.slf4j.Slf4j
import lombok.extern.slf4j.XSlf4j

@CommonsLog
internal class WithCommonsLog

@Flogger
internal class WithFlogger

@Log
internal class WithJavaLog

@JBossLog
internal class WithJBossLog

@Log4j
internal class WithLog4j

@Log4j2
internal class WithLog4j2

@Slf4j
internal class WithSlf4j

@XSlf4j
internal class WithXSlf4j
