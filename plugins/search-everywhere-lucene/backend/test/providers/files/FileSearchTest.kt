package com.intellij.searchEverywhereLucene.backend.providers.files

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.TestFactory

@TestApplication
class FileSearchTest : FileSearchTestBase() {


  @TestFactory
  fun `ensure each part must match`(): List<DynamicNode> {

    val foo = file("foo/Readme.md")
    val bar = file("bar/Readme.md")
    val baz = file("baz/Readme.md")


    return indexWith(listOf(foo, bar)) { index ->
      index.assertSearch("Readme.md") {
        findsAllOf(foo, bar)
      }

      index.assertSearch("Readme.md foo") {
        findsAllOf(foo)
        findsNoneOf(bar)
      }

      index.assertSearch("Readme.md bar") {
        findsAllOf(bar)
        findsNoneOf(foo)
      }

      index.assertSearch("md bar") {
        findsAllOf(bar)
        findsNoneOf(foo, baz)
      }

      index.assertSearch("bar baz") {
        findsNothing()
      }
    }
  }

  @TestFactory
  fun `test pet search`(): List<DynamicNode> {
    val pet = file("Pet.java")
    val petC = file("PetController.java")

    return indexWith(listOf(pet, petC)) { index ->
      index.assertSearch("Pet.java") {
        findsWithOrdering(listOf(pet, petC))
      }
    }
  }

  @TestFactory
  fun `test case insensitive search`(): List<DynamicNode> {
    val pet = file("Pet.java")
    val petC = file("PetController.java")

    return indexWith(listOf(pet, petC)) { index ->
      index.assertSearch("pet.java") {
        findsWithOrdering(listOf(pet, petC))
      }
    }
  }

}