@lombok.extern.slf4j.Slf4j
interface LoggerSlf4jTypesInterface {
}
@lombok.extern.slf4j.Slf4j
@interface LoggerSlf4jTypesAnnotation {
}
@lombok.extern.slf4j.Slf4j
enum LoggerSlf4jTypesEnum {
}
@lombok.extern.slf4j.Slf4j
enum LoggerSlf4jTypesEnumWithElement {
	FOO;
}
interface LoggerSlf4jTypesInterfaceOuter {
	@lombok.extern.slf4j.Slf4j
	class Inner {
	}
}