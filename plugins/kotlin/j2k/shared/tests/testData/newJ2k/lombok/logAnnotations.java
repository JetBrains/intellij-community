// !ADD_LOMBOK_ANNOTATIONS
// KTIJ-12602: the Kotlin Lombok compiler plugin adds the logger field, so J2K must keep each annotation
package test;

import lombok.extern.apachecommons.CommonsLog;
import lombok.extern.flogger.Flogger;
import lombok.extern.java.Log;
import lombok.extern.jbosslog.JBossLog;
import lombok.extern.log4j.Log4j;
import lombok.extern.log4j.Log4j2;
import lombok.extern.slf4j.Slf4j;
import lombok.extern.slf4j.XSlf4j;

@CommonsLog
class WithCommonsLog {
}

@Flogger
class WithFlogger {
}

@Log
class WithJavaLog {
}

@JBossLog
class WithJBossLog {
}

@Log4j
class WithLog4j {
}

@Log4j2
class WithLog4j2 {
}

@Slf4j
class WithSlf4j {
}

@XSlf4j
class WithXSlf4j {
}
