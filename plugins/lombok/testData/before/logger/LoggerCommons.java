import lombok.extern.apachecommons.CommonsLog;

@CommonsLog
class LoggerCommons {
}

@CommonsLog
class LoggerCommonsWithImport {
}

@CommonsLog(topic="DifferentName")
class LoggerCommonsWithDifferentName {
}


@lombok.extern.apachecommons.CommonsLog(access = lombok.AccessLevel.PUBLIC)
class LoggerCommonsAccessPublic {
}

@lombok.extern.apachecommons.CommonsLog(access = lombok.AccessLevel.MODULE)
class LoggerCommonsAccessModule {
}

@lombok.extern.apachecommons.CommonsLog(access = lombok.AccessLevel.PROTECTED)
class LoggerCommonsAccessProtected {
}

@lombok.extern.apachecommons.CommonsLog(access = lombok.AccessLevel.PACKAGE)
class LoggerCommonsAccessPackage {
}

@lombok.extern.apachecommons.CommonsLog(access = lombok.AccessLevel.PRIVATE)
class LoggerCommonsAccessPrivate {
}

@lombok.extern.apachecommons.CommonsLog(access = lombok.AccessLevel.NONE)
class LoggerCommonsAccessNone {
}
