annotation class Anno

class Foo(
    @get:Anno
    var annotatedWithGet: Int,
    @set:Anno
    var annotatedWithSet: Int,
    @property:Anno
    var annotatedWithProperty: Int,
    @param:Anno
    var annotatedWithParam: Int,
    @field:Anno
    var annotatedWithField: Int,
    @Anno
    var annotatedWithDefault: Int,
)

class Bar {
    @get:Anno
    var annotatedWithGet: Int = 0
    @set:Anno
    var annotatedWithSet: Int = 1
    @property:Anno
    var annotatedWithProperty: Int = 2
    @field:Anno
    var annotatedWithField: Int = 3
    @Anno
    var annotatedWithDefault: Int = 4
}