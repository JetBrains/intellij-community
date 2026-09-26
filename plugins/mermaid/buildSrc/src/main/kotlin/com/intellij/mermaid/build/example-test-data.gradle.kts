import org.gradle.kotlin.dsl.creating
import org.gradle.kotlin.dsl.getValue

val exampleTestData by configurations.creating {
  isCanBeConsumed = true
  isCanBeResolved = false
}

val exampleTestDataImplementation by configurations.creating {
  isCanBeConsumed = false
  isCanBeResolved = true
  extendsFrom(exampleTestData)
}
