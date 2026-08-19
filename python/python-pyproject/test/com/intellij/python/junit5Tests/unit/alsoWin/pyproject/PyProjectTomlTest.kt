package com.intellij.python.junit5Tests.unit.alsoWin.pyproject

import com.intellij.python.pyproject.PyProjectContact
import com.intellij.python.pyproject.PyProjectDependencies
import com.intellij.python.pyproject.PyProjectFile
import com.intellij.python.pyproject.PyProjectIssue
import com.intellij.python.pyproject.PyProjectIssue.InvalidContact
import com.intellij.python.pyproject.PyProjectIssue.MissingVersion
import com.intellij.python.pyproject.PyProjectIssue.SafeGetError
import com.intellij.python.pyproject.PyProjectTable
import com.intellij.python.pyproject.PyProjectToml
import com.intellij.python.pyproject.PyProjectToolFactory
import com.intellij.python.pyproject.TomlTableSafeGetError.RequiredValueMissing
import com.intellij.python.pyproject.TomlTableSafeGetError.UnexpectedType
import org.apache.tuweni.toml.TomlArray
import org.apache.tuweni.toml.TomlTable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

internal class PyProjectTomlTest {
  @Test
  fun parseProvidesErrorsOnFailure() {
    // GIVEN
    val configContents = "[proj"

    // WHEN
    val result = PyProjectToml.parse(configContents)

    // THEN
    org.junit.jupiter.api.Assertions.assertNull(result)
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
    val pyproject = PyProjectToml.parse(configContents)!!

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
    val pyproject = PyProjectToml.parse(configContents)
    org.junit.jupiter.api.Assertions.assertNull(pyproject)
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
    val pyproject = PyProjectToml.parse(configContents)


    // THEN
    org.junit.jupiter.api.Assertions.assertNull(pyproject)
  }

  @Test
  fun absentToolSectionsResultInNull() {
    // GIVEN
    val configContents = """
      [project]
      name="Some project"
      version="1.2.3"
    """.trimIndent()
    val pyproject = PyProjectToml.parse(configContents)!!

    // WHEN
    val testTool = pyproject.getTool(TestPyProject)

    // THEN
    assertNull(testTool.tables["tool.test"])
    assertNull(testTool.tables["shared_category"])
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("parseTestCases")
  fun parseTests(
    name: String,
    pyprojectToml: String,
    expectedProjectTable: PyProjectTable?,
    expectedIssues: List<PyProjectIssue>?,
    expectedDepGroups: Map<String, List<String>>,
  ) {
    val result = PyProjectToml.parse(pyprojectToml)

    assertEquals(expectedProjectTable, result?.project)
    if (result != null) {
      assertEquals(expectedIssues, result.issues)
      assertEquals(expectedDepGroups, result.depGroupsToDeps)
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("groupNamesTestCases")
  fun dependencyGroupNames(
    name: String,
    pyprojectToml: String,
    toolSpecificGroups: List<String>,
    expectedNames: List<String>,
  ) {
    val result = PyProjectToml.parse(pyprojectToml)!!

    // Order is part of the contract, so compare lists rather than sets.
    assertEquals(expectedNames, result.getDependencyGroupNames(toolSpecificGroups))
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

      *listOf(
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
        listOf(SafeGetError(UnexpectedType("dev", TomlArray::class, Long::class))),
        // The key exists, so the group exists; only its dependencies could not be read.
        expectedDepGroups = mapOf("dev" to emptyList()),
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
        ),
        listOf(),
        expectedDepGroups = mapOf("dev" to listOf("a", "b")),
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
        expectedDepGroups = mapOf("dev" to listOf("foo", "bar")),
      ),
      ParseTestCase(
        "dependency_groups",
        """
          [project]
          name = "name"
          version = "123"
          [dependency-groups]
          dev = [
              "sub-project-a",
              "sub-project-b",
          ]
          abc = [
            "spam"
          ]
        """.trimIndent(),
        PyProjectTable(
          name = "name",
          version = "123",
        ),
        expectedIssues = emptyList(),
        expectedDepGroups = mapOf("dev" to listOf("sub-project-a", "sub-project-b"), "abc" to listOf("spam")),
      ),
      ParseTestCase(
        "groups without string dependencies are kept as empty groups",
        """
          [project]
          name = "name"
          version = "123"
          [dependency-groups]
          dev = ["a"]
          empty = []
          # `include-group` is not supported yet, so the group holds no dependencies but still exists
          includes = [{include-group = "dev"}]
        """.trimIndent(),
        PyProjectTable(
          name = "name",
          version = "123",
        ),
        expectedIssues = emptyList(),
        expectedDepGroups = mapOf(
          "dev" to listOf("a"),
          "empty" to emptyList(),
          "includes" to emptyList(),
        ),
      ),
    ).map {
      Arguments.of(it.name, it.pyprojectToml, it.expectedProjectTable, it.expectedIssues, it.expectedDepGroups)
    }

    @JvmStatic
    fun groupNamesTestCases(): List<Arguments> = listOf(
      GroupNamesTestCase(
        "a project without groups only has main",
        """
          [project]
          name = "name"
          version = "123"
          dependencies = ["a"]
        """.trimIndent(),
        expectedNames = listOf("main"),
      ),

      GroupNamesTestCase(
        "PEP 735 groups follow main",
        """
          [project]
          name = "name"
          version = "123"

          [dependency-groups]
          dev = ["a"]
          docs = ["b"]
        """.trimIndent(),
        expectedNames = listOf("main", "dev", "docs"),
      ),

      GroupNamesTestCase(
        "PEP 621 extras follow PEP 735 groups",
        """
          [project]
          name = "name"
          version = "123"

          [project.optional-dependencies]
          gui = ["PyQt5"]

          [dependency-groups]
          dev = ["a"]
        """.trimIndent(),
        expectedNames = listOf("main", "dev", "gui"),
      ),

      // The three cases below guard the behaviour this class' `depGroupsToDeps` cases describe: a group is
      // user-visible as soon as its key exists, even when no dependency string could be read from it.
      GroupNamesTestCase(
        "a PEP 735 group with an empty array is still a group",
        """
          [project]
          name = "name"
          version = "123"

          [dependency-groups]
          dev = ["a"]
          empty = []
        """.trimIndent(),
        expectedNames = listOf("main", "dev", "empty"),
      ),

      GroupNamesTestCase(
        "a PEP 735 group holding only include-group is still a group",
        """
          [project]
          name = "name"
          version = "123"

          [dependency-groups]
          dev = ["a"]
          includes = [{include-group = "dev"}]
        """.trimIndent(),
        expectedNames = listOf("main", "dev", "includes"),
      ),

      GroupNamesTestCase(
        "a PEP 735 group with a wrongly typed value is still a group",
        """
          [project]
          name = "name"
          version = "123"

          [dependency-groups]
          dev = 123
        """.trimIndent(),
        expectedNames = listOf("main", "dev"),
      ),

      GroupNamesTestCase(
        "toolSpecificGroups sit between main and the PEP 735 groups",
        """
          [project]
          name = "name"
          version = "123"

          [project.optional-dependencies]
          gui = ["PyQt5"]

          [dependency-groups]
          dev = ["a"]
        """.trimIndent(),
        expectedNames = listOf("main", "legacy-dev", "poetry-docs", "dev", "gui"),
        toolSpecificGroups = listOf("legacy-dev", "poetry-docs"),
      ),

      GroupNamesTestCase(
        "a name declared in several places is reported once, at its earliest position",
        """
          [project]
          name = "name"
          version = "123"

          [project.optional-dependencies]
          dev = ["PyQt5"]

          [dependency-groups]
          dev = ["a"]
        """.trimIndent(),
        expectedNames = listOf("main", "dev"),
      ),

      GroupNamesTestCase(
        "a tool-specific name that a PEP table repeats is reported once",
        """
          [project]
          name = "name"
          version = "123"

          [dependency-groups]
          dev = ["a"]
        """.trimIndent(),
        expectedNames = listOf("main", "dev"),
        toolSpecificGroups = listOf("dev"),
      ),
    ).map {
      Arguments.of(it.name, it.pyprojectToml, it.toolSpecificGroups, it.expectedNames)
    }

    data class GroupNamesTestCase(
      val name: String,
      val pyprojectToml: String,
      val expectedNames: List<String>,
      val toolSpecificGroups: List<String> = emptyList(),
    )

    data class ParseTestCase(
      val name: String,
      val pyprojectToml: String,
      val expectedProjectTable: PyProjectTable?,
      val expectedIssues: List<PyProjectIssue>,
      val expectedDepGroups: Map<String, List<String>> = emptyMap(),
    )

    data class TestPyProject(val tables: Map<String, TomlTable?>) {
      companion object : PyProjectToolFactory<TestPyProject> {
        override val tables: List<String> = listOf("tool.test", "shared_category")
        override fun createTool(tables: Map<String, TomlTable?>): TestPyProject = TestPyProject(tables)
      }
    }
  }
}
