open class Foo(
    pathToTestDir: String, testGroupOutputDirPrefix: String,
)

open class Bar(pathToTestDir: String = "path") : Foo(
    pathToTestDir = pathToTestDir,
    testGroupOutputDirPrefix = "pa<caret>th1"
)