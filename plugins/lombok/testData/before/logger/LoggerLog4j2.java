import lombok.extern.log4j.Log4j2;
import lombok.AccessLevel;

@Log4j2
class LoggerLog4j2 {
}

@Log4j2
class LoggerLog4j2WithImport {
}

@Log4j2(topic="DifferentName")
class LoggerLog4j2WithDifferentName {
}

@Log4j2(access = AccessLevel.PUBLIC)
class LoggerLog4j2AccessPublic {
}

@Log4j2(access = AccessLevel.MODULE)
class LoggerLog4j2AccessModule {
}

@Log4j2(access = AccessLevel.PROTECTED)
class LoggerLog4j2AccessProtected {
}

@Log4j2(access = AccessLevel.PACKAGE)
class LoggerLog4j2AccessPackage {
}

@Log4j2(access = AccessLevel.PRIVATE)
class LoggerLog4j2AccessPrivate {
}

@Log4j2(access = AccessLevel.NONE)
class LoggerLog4j2AccessNone {
}