import lombok.extern.flogger.Flogger;
import lombok.AccessLevel;

@Flogger
class LoggerFlogger {
}

@Flogger(access = AccessLevel.PUBLIC)
class LoggerFloggerAccessPublic {
}

@Flogger(access = AccessLevel.MODULE)
class LoggerFloggerAccessModule {
}

@Flogger(access = AccessLevel.PROTECTED)
class LoggerFloggerAccessProtected {
}

@Flogger(access = AccessLevel.PACKAGE)
class LoggerFloggerAccessPackage {
}

@Flogger(access = AccessLevel.PRIVATE)
class LoggerFloggerAccessPrivate {
}

@Flogger(access = AccessLevel.NONE)
class LoggerFloggerAccessNone {
}
