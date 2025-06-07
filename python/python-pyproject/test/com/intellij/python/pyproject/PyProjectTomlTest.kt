package com.intellij.python.pyproject

import com.intellij.python.pyproject.PyProjectIssue.*
import com.intellij.python.pyproject.TomlTableSafeGetError.RequiredValueMissing
import com.intellij.python.pyproject.TomlTableSafeGetError.UnexpectedType
import com.jetbrains.python.Result
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.isFailure
import org.apache.tuweni.toml.TomlArray
import org.apache.tuweni.toml.TomlTable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.reflect.KClass

class PyProjectTomlTest {
  @Test
  fun parseProvidesErrorsOnFailure() {
    // GIVEN
    val configContents = "[proj"

    // WHEN
    val result = PyProjectToml.parse(configContents.byteInputStream())

    // THEN
    assert(result.isFailure)
    assert((result as Result.Failure).error.isNotEmpty())
  }

  @Test
  fun toolsCanBeCreated() {
    // GIVEN
    val configContents = """
      [project]
      name="Some project"
      version="1.2.3"
      
      [shared_category]
      foo="test foo"
      
      [tool.test]
      bar="test bar"
      baz="test baz"
    """.trimIndent()
    val pyproject = PyProjectToml.parse(configContents.byteInputStream()).orThrow()

    // WHEN
    val testTool = pyproject.getTool(TestPyProject)

    // THEN
    assertEquals(2, testTool.tables.size)
    assert(testTool.tables["tool.test"] is TomlTable)
    assert(testTool.tables["shared_category"] is TomlTable)
  }

  @Test
  fun toolsCanBeCreatedWithoutProject() {
    // GIVEN
    val configContents = """
      [shared_category]
      foo="test foo"
      
      [tool.test]
      bar="test bar"
      baz="test baz"
    """.trimIndent()

    // WHEN
    val pyproject = PyProjectToml.parse(configContents.byteInputStream()).orThrow()
    val testTool = pyproject.getTool(TestPyProject)

    // THEN
    assertEquals(pyproject.project, null)
    assertEquals(pyproject.issues.size, 0)
    assertEquals(2, testTool.tables.size)
    assert(testTool.tables["tool.test"] is TomlTable)
    assert(testTool.tables["shared_category"] is TomlTable)
  }

  @Test
  fun toolsCanBeCreatedWithProjectThatHasIssues() {
    // GIVEN
    val configContents = """
      [project]
      
      [shared_category]
      foo="test foo"
      
      [tool.test]
      bar="test bar"
      baz="test baz"
    """.trimIndent()

    // WHEN
    val pyproject = PyProjectToml.parse(configContents.byteInputStream()).orThrow()
    val testTool = pyproject.getTool(TestPyProject)

    // THEN
    assertEquals(pyproject.issues, listOf(MissingName, MissingVersion))
    assertEquals(pyproject.project, PyProjectTable())
    assertEquals(2, testTool.tables.size)
    assert(testTool.tables["tool.test"] is TomlTable)
    assert(testTool.tables["shared_category"] is TomlTable)
  }

  @Test
  fun absentToolSectionsResultInNull() {
    // GIVEN
    val configContents = """
      [project]
      name="Some project"
      version="1.2.3"
    """.trimIndent()
    val pyproject = PyProjectToml.parse(configContents.byteInputStream()).orThrow()

    // WHEN
    val testTool = pyproject.getTool(TestPyProject)

    // THEN
    assertEquals(testTool.tables["tool.test"], null)
    assertEquals(testTool.tables["shared_category"], null)
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("parseTestCases")
  fun parseTests(name: String, pyprojectToml: String, expectedProjectTable: PyProjectTable?, expectedIssues: List<PyProjectIssue>) {
    val result = PyProjectToml.parse(pyprojectToml.byteInputStream())
    val unwrapped = result.getOrThrow()

    assertEquals(expectedProjectTable, unwrapped.project)
    assertEquals(expectedIssues, unwrapped.issues)
  }

  companion object {
    @JvmStatic
    fun parseTestCases(): List<Arguments> = listOf(
      ParseTestCase(
        "empty config results with no table and empty issues",
        "",
        null,
        listOf()
      ),

      ParseTestCase(
        "empty project section results with table and name + version issues",
        "[project]",
        PyProjectTable(),
        listOf(MissingName, MissingVersion)
      ),

      ParseTestCase(
        "empty name results in an issue",
        """
          [project]
          version = "123"
        """.trimIndent(),
        PyProjectTable(version = "123"),
        listOf(MissingName)
      ),

      ParseTestCase(
        "empty name results in an issue, even when mentioned in dynamic",
        """
          [project]
          version = "123"
          dynamic = ["name"]
        """.trimIndent(),
        PyProjectTable(version = "123", dynamic = listOf("name")),
        listOf(MissingName)
      ),

      ParseTestCase(
        "empty version results in an issue",
        """
          [project]
          name = "some_name"
        """.trimIndent(),
        PyProjectTable(name = "some_name"),
        listOf(MissingVersion)
      ),

      ParseTestCase(
        "empty version doesn't result in an issue when it's present in dynamic",
        """
          [project]
          name = "some_name"
          dynamic = ["version"]
        """.trimIndent(),
        PyProjectTable(name = "some_name", dynamic = listOf("version")),
        listOf()
      ),

      ParseTestCase(
        "name of wrong type results in an issue",
        """
          [project]
          name = 12
          version = "123"
        """.trimIndent(),
        PyProjectTable(version = "123"),
        listOf(SafeGetError(UnexpectedType("name", String::class, Long::class)))
      ),

      ParseTestCase(
        "version of wrong type results in an issue",
        """
          [project]
          name = "name"
          version = 123
        """.trimIndent(),
        PyProjectTable(name = "name"),
        listOf(SafeGetError(UnexpectedType("version", String::class, Long::class)))
      ),

      ParseTestCase(
        "name and version resolve correctly when correctly specified",
        """
          [project]
          name = "name"
          version = "123"
        """.trimIndent(),
        PyProjectTable(name = "name", version = "123"),
        listOf()
      ),

      *listOf<Pair<String, KClass<*>>>(
        "requires-python" to String::class,
        "authors" to TomlArray::class,
        "maintainers" to TomlArray::class,
        "description" to String::class,
        "readme" to TomlTable::class,
        "license" to String::class,
        "license-files" to TomlArray::class,
        "keywords" to TomlArray::class,
        "classifiers" to TomlArray::class,
        "dependencies" to TomlArray::class,
        "optional-dependencies" to TomlTable::class,
        "scripts" to TomlTable::class,
        "gui-scripts" to TomlTable::class,
        "urls" to TomlTable::class,
      ).map {
        ParseTestCase(
          "${it.first} of wrong type results in an issue",
          """
            [project]
            name = "name"
            version = "123"
            ${it.first} = 123
          """.trimIndent(),
          PyProjectTable(name = "name", version = "123"),
          listOf(SafeGetError(UnexpectedType(it.first, it.second, Long::class)))
        )
      }.toTypedArray(),

      *listOf("authors", "maintainers").flatMap {
        listOf(
          ParseTestCase(
            "contacts of wrong type in $it result in an issue",
            """
              [project]
              name = "name"
              version = "123"
              $it = [
                123,
              ]
            """.trimIndent(),
            PyProjectTable(name = "name", version = "123"),
            listOf(
              SafeGetError(UnexpectedType("$it[0]", TomlTable::class, Long::class)),
            )
          ),

          ParseTestCase(
            "contacts without name and email in $it result in an issue",
            """
              [project]
              name = "name"
              version = "123"
              $it = [
                {foo = 123, bar = "qwf"}
              ]
            """.trimIndent(),
            PyProjectTable(
              name = "name",
              version = "123",
              authors = if (it == "authors") listOf() else null,
              maintainers = if (it == "maintainers") listOf() else null,
            ),
            listOf(
              InvalidContact("$it[0]"),
            )
          ),

          ParseTestCase(
            "contacts with only a name in $it resolve",
            """
              [project]
              name = "name"
              version = "123"
              $it = [
                {name = "name1"},
                {name = "name2"}
              ]
            """.trimIndent(),
            run {
              val contacts = listOf(PyProjectContact(name = "name1", email = null), PyProjectContact(name = "name2", email = null))
              PyProjectTable(
                name = "name",
                version = "123",
                authors = if (it == "authors") contacts else null,
                maintainers = if (it == "maintainers") contacts else null,
              )
            },
            listOf(),
          ),

          ParseTestCase(
            "contacts with only an email in $it resolve",
            """
              [project]
              name = "name"
              version = "123"
              $it = [
                {email = "email1"},
                {email = "email2"}
              ]
            """.trimIndent(),
            run {
              val contacts = listOf(PyProjectContact(name = null, email = "email1"), PyProjectContact(name = null, email = "email2"))
              PyProjectTable(
                name = "name",
                version = "123",
                authors = if (it == "authors") contacts else null,
                maintainers = if (it == "maintainers") contacts else null,
              )
            },
            listOf(),
          ),

          ParseTestCase(
            "contacts with both name and email in $it resolve",
            """
              [project]
              name = "name"
              version = "123"
              $it = [
                {name = "name1", email = "email1"},
                {name = "name2", email = "email2"}
              ]
            """.trimIndent(),
            run {
              val contacts = listOf(PyProjectContact(name = "name1", email = "email1"), PyProjectContact(name = "name2", email = "email2"))
              PyProjectTable(
                name = "name",
                version = "123",
                authors = if (it == "authors") contacts else null,
                maintainers = if (it == "maintainers") contacts else null,
              )
            },
            listOf(),
          )
        )
      }.toTypedArray(),

      *listOf("license-files", "keywords", "classifiers", "dependencies").map {
        ParseTestCase(
          "elements in $it that are of the wrong type resolve in an issue",
          """
            [project]
            name = "name"
            version = "123"
            $it = [123]
          """.trimIndent(),
          PyProjectTable(name = "name", version = "123"),
          listOf(SafeGetError(UnexpectedType("$it[0]", String::class, Long::class)))
        )
      }.toTypedArray(),

      ParseTestCase(
        "correctly defined dependencies resolve",
        """
          [project]
          name = "name"
          version = "123"
          dependencies = ["a", "b"]
        """.trimIndent(),
        PyProjectTable(
          name = "name",
          version = "123",
          dependencies = PyProjectDependencies(
            project = listOf("a", "b")
          )
        ),
        listOf()
      ),

      ParseTestCase(
        "dev with wrong type in dependency-groups results in an issue",
        """
          [project]
          name = "name"
          version = "123"
          
          [dependency-groups]
          dev = 123
        """.trimIndent(),
        PyProjectTable(
          name = "name",
          version = "123",
        ),
        listOf(SafeGetError(UnexpectedType("dev", TomlArray::class, Long::class)))
      ),

      ParseTestCase(
        "correctly defined dev dependencies resolve",
        """
          [project]
          name = "name"
          version = "123"
          
          [dependency-groups]
          dev = ["a", "b"]
        """.trimIndent(),
        PyProjectTable(
          name = "name",
          version = "123",
          dependencies = PyProjectDependencies(
            dev = listOf("a", "b")
          )
        ),
        listOf()
      ),

      ParseTestCase(
        "optional dependency entries with wrong type result in an issue",
        """
          [project]
          name = "name"
          version = "123"
          
          [project.optional-dependencies]
          a = 123
          b = [123]
        """.trimIndent(),
        PyProjectTable(
          name = "name",
          version = "123",
        ),
        listOf(
          SafeGetError(UnexpectedType("a", TomlArray::class, Long::class)),
          SafeGetError(UnexpectedType("b[0]", String::class, Long::class)),
        )
      ),

      ParseTestCase(
        "correctly defined optional dependencies resolve",
        """
          [project]
          name = "name"
          version = "123"
          
          [project.optional-dependencies]
          foo = ["a", "b"]
          bar = ["c", "d"]
        """.trimIndent(),
        PyProjectTable(
          name = "name",
          version = "123",
          dependencies = PyProjectDependencies(
            optional = mapOf(
              "foo" to listOf("a", "b"),
              "bar" to listOf("c", "d")
            )
          )
        ),
        listOf()
      ),

      *listOf("scripts", "gui-scripts", "urls").flatMap {
        listOf(
          ParseTestCase(
            "$it entries with wrong type result in an issue",
            """
              [project]
              name = "name"
              version = "123"
              
              [project.$it]
              a = 123
              b = 123
            """.trimIndent(),
            PyProjectTable(
              name = "name",
              version = "123",
              scripts = if (it == "scripts") mapOf() else null,
              guiScripts = if (it == "gui-scripts") mapOf() else null,
              urls = if (it == "urls") mapOf() else null,
            ),
            listOf(
              SafeGetError(UnexpectedType("a", String::class, Long::class)),
              SafeGetError(UnexpectedType("b", String::class, Long::class)),
            )
          ),

          ParseTestCase(
            "correctly defined entries in $it resolve",
            """
              [project]
              name = "name"
              version = "123"
              
              [project.$it]
              a = "item1"
              b = "item2"
            """.trimIndent(),
            run {
              val items = mapOf("a" to "item1", "b" to "item2")
              PyProjectTable(
                name = "name",
                version = "123",
                scripts = if (it == "scripts") items else null,
                guiScripts = if (it == "gui-scripts") items else null,
                urls = if (it == "urls") items else null,
              )
            },
            listOf()
          ),
        )
      }.toTypedArray(),

      ParseTestCase(
        "readme can be defined as a string",
        """
          [project]
          name = "name"
          version = "123"
          readme = "README.md"
        """.trimIndent(),
        PyProjectTable(
          name = "name",
          version = "123",
          readme = PyProjectFile("README.md"),
        ),
        listOf(),
      ),

      ParseTestCase(
        "readme can be defined as an object",
        """
          [project]
          name = "name"
          version = "123"
          readme = {name = "README.md", content-type = "text/markdown"}
        """.trimIndent(),
        PyProjectTable(
          name = "name",
          version = "123",
          readme = PyProjectFile("README.md", "text/markdown"),
        ),
        listOf(),
      ),

      ParseTestCase(
        "readme object with missing name results in an issue",
        """
          [project]
          name = "name"
          version = "123"
          readme = {content-type = "text/markdown"}
        """.trimIndent(),
        PyProjectTable(
          name = "name",
          version = "123",
        ),
        listOf(SafeGetError(RequiredValueMissing("name"))),
      ),

      ParseTestCase(
        "readme object with missing content-type results in an issue",
        """
          [project]
          name = "name"
          version = "123"
          readme = {name = "README.md"}
        """.trimIndent(),
        PyProjectTable(
          name = "name",
          version = "123",
        ),
        listOf(SafeGetError(RequiredValueMissing("content-type"))),
      ),

      ParseTestCase(
        "correctly parses full example",
        """
          [project]
          name = "spam-eggs"
          version = "2020.0.0"
          dependencies = [
            "httpx",
            "gidgethub[httpx]>4.0.0",
            "django>2.1; os_name != 'nt'",
            "django>2.0; os_name == 'nt'",
          ]
          requires-python = ">=3.8"
          authors = [
            {name = "Pradyun Gedam", email = "pradyun@example.com"},
            {name = "Tzu-Ping Chung", email = "tzu-ping@example.com"},
            {name = "Another person"},
            {email = "different.person@example.com"},
          ]
          maintainers = [
            {name = "Brett Cannon", email = "brett@example.com"}
          ]
          description = "Lovely Spam! Wonderful Spam!"
          readme = "README.rst"
          license = "MIT"
          license-files = ["LICEN[CS]E.*"]
          keywords = ["egg", "bacon", "sausage", "tomatoes", "Lobster Thermidor"]
          classifiers = [
            "Development Status :: 4 - Beta",
            "Programming Language :: Python"
          ]
    
          [project.optional-dependencies]
          gui = ["PyQt5"]
          cli = [
            "rich",
            "click",
          ]
    
          [project.urls]
          Homepage = "https://example.com"
          Documentation = "https://readthedocs.org"
          Repository = "https://github.com/me/spam.git"
          "Bug Tracker" = "https://github.com/me/spam/issues"
          Changelog = "https://github.com/me/spam/blob/master/CHANGELOG.md"
    
          [project.scripts]
          spam-cli = "spam:main_cli"
    
          [project.gui-scripts]
          spam-gui = "spam:main_gui"
    
          [dependency-groups]
          dev = ["foo", "bar"]
        """.trimIndent(),
        PyProjectTable(
          name = "spam-eggs",
          version = "2020.0.0",
          requiresPython = ">=3.8",
          authors = listOf(
            PyProjectContact(name = "Pradyun Gedam", email = "pradyun@example.com"),
            PyProjectContact(name = "Tzu-Ping Chung", email = "tzu-ping@example.com"),
            PyProjectContact(name = "Another person", email = null),
            PyProjectContact(name = null, email = "different.person@example.com"),
          ),
          maintainers = listOf(
            PyProjectContact(name = "Brett Cannon", email = "brett@example.com"),
          ),
          description = "Lovely Spam! Wonderful Spam!",
          readme = PyProjectFile("README.rst"),
          license = "MIT",
          licenseFiles = listOf("LICEN[CS]E.*"),
          keywords = listOf("egg", "bacon", "sausage", "tomatoes", "Lobster Thermidor"),
          classifiers = listOf(
            "Development Status :: 4 - Beta",
            "Programming Language :: Python",
          ),
          dependencies = PyProjectDependencies(
            project = listOf(
              "httpx",
              "gidgethub[httpx]>4.0.0",
              "django>2.1; os_name != 'nt'",
              "django>2.0; os_name == 'nt'",
            ),
            dev = listOf("foo", "bar"),
            optional = mapOf(
              "gui" to listOf("PyQt5"),
              "cli" to listOf("rich", "click"),
            ),
          ),
          urls = mapOf(
            "Homepage" to "https://example.com",
            "Documentation" to "https://readthedocs.org",
            "Repository" to "https://github.com/me/spam.git",
            "Bug Tracker" to "https://github.com/me/spam/issues",
            "Changelog" to "https://github.com/me/spam/blob/master/CHANGELOG.md",
          ),
          scripts = mapOf(
            "spam-cli" to "spam:main_cli",
          ),
          guiScripts = mapOf(
            "spam-gui" to "spam:main_gui",
          ),
        ),
        listOf(),
      ),
    ).map {
      Arguments.of(it.name, it.pyprojectToml, it.expectedProjectTable, it.expectedIssues)
    }

    data class ParseTestCase(
      val name: String,
      val pyprojectToml: String,
      val expectedProjectTable: PyProjectTable?,
      val expectedIssues: List<PyProjectIssue>,
    )

    data class TestPyProject(val tables: Map<String, TomlTable?>) {
      companion object : PyProjectToolFactory<TestPyProject> {
        override val tables: List<String> = listOf("tool.test", "shared_category")
        override fun createTool(tables: Map<String, TomlTable?>): TestPyProject = TestPyProject(tables)
      }
    }
  }
}
