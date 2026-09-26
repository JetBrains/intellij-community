import lombok.extern.log4j.Log4j;
import lombok.AccessLevel;

@Log4j
class LoggerLog4j {
}

@Log4j
class LoggerLog4jWithImport {
}

@Log4j(topic="DifferentName")
class LoggerLog4jWithDifferentName {
}

@Log4j(access = AccessLevel.PUBLIC)
class LoggerLog4jAccessPublic {
}

@Log4j(access = AccessLevel.MODULE)
class LoggerLog4jAccessModule {
}

@Log4j(access = AccessLevel.PROTECTED)
class LoggerLog4jAccessProtected {
}

@Log4j(access = AccessLevel.PACKAGE)
class LoggerLog4jAccessPackage {
}

@Log4j(access = AccessLevel.PRIVATE)
class LoggerLog4jAccessPrivate {
}

@Log4j(access = AccessLevel.NONE)
class LoggerLog4jAccessNone {
}