annotation class Foo

class Bar(@field:Foo val first,    // annotate Java field
          @get:Foo val second,      // annotate Java getter
          @param:Foo val third) {
    @get:Foo
    val baz = "test"
}

// ANNOTATION: Foo
// SEARCH: method:getBaz
// SEARCH: method:getSecond
// SEARCH: param:third
// SEARCH: field:first

