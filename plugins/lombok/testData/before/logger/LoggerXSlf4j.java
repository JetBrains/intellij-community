import lombok.extern.slf4j.XSlf4j;
import lombok.AccessLevel;

@XSlf4j
class LoggerXSlf4j {
}

@XSlf4j
class LoggerXSlf4jWithImport {
}

@XSlf4j(topic="DifferentName")
class LoggerXSlf4jWithDifferentName {
}

@XSlf4j(access = AccessLevel.PUBLIC)
class LoggerXSlf4jAccessPublic {
}

@XSlf4j(access = AccessLevel.MODULE)
class LoggerXSlf4jAccessModule {
}

@XSlf4j(access = AccessLevel.PROTECTED)
class LoggerXSlf4jAccessProtected {
}

@XSlf4j(access = AccessLevel.PACKAGE)
class LoggerXSlf4jAccessPackage {
}

@XSlf4j(access = AccessLevel.PRIVATE)
class LoggerXSlf4jAccessPrivate {
}

@XSlf4j(access = AccessLevel.NONE)
class LoggerXSlf4jAccessNone {
}