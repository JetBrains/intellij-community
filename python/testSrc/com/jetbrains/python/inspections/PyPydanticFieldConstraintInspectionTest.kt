package com.jetbrains.python.inspections

import com.intellij.idea.TestFor
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import org.junit.jupiter.api.Test

class PyPydanticFieldConstraintInspectionTest : PyCodeInsightTestCase() {

  @Test
  @TestFor(issues = ["PY-90015"])
  fun `test gt and lt`() = test("""
    from pydantic import BaseModel, Field

    class M(BaseModel):
        x: int = Field(gt=0, lt=100)

    M(x=-1)  # WARNING Value for 'x' must be greater than 0
    M(x=0)   # WARNING Value for 'x' must be greater than 0
    M(x=30)
    M(x=100) # WARNING Value for 'x' must be less than 100
    M(x=120) # WARNING Value for 'x' must be less than 100
  """)

  @Test
  @TestFor(issues = ["PY-90015"])
  fun `test ge and le`() = test("""
    from pydantic import BaseModel, Field

    class M(BaseModel):
        x: int = Field(ge=10, le=20)

    M(x=5)  # WARNING Value for 'x' must be greater than or equal to 10
    M(x=10)
    M(x=15)
    M(x=20)
    M(x=25) # WARNING Value for 'x' must be less than or equal to 20
  """)

  @Test
  @TestFor(issues = ["PY-90015"])
  fun `test min and max length`() = test("""
    from pydantic import BaseModel, Field

    class M(BaseModel):
        name: str = Field(min_length=2, max_length=5)

    M(name="J")            # WARNING String for 'name' must be at least 2 characters long
    M(name=(("J")))        # WARNING String for 'name' must be at least 2 characters long
    M(name="John")
    M(name="VeryLongName") # WARNING String for 'name' must be at most 5 characters long
  """)

  @Test
  @TestFor(issues = ["PY-90015"])
  fun `test float bounds`() = test("""
    from pydantic import BaseModel, Field

    class M(BaseModel):
        price: float = Field(gt=0)

    M(price=-0.5)       # WARNING Value for 'price' must be greater than 0
    M(price=0)          # WARNING Value for 'price' must be greater than 0
    M(price=0.00000001)
    M(price=9.99)
  """)

  @Test
  @TestFor(issues = ["PY-90015"])
  fun `test negation and nested parentheses`() = test("""
    from pydantic import BaseModel, Field

    class M(BaseModel):
        x: int = Field(gt=((0)))

    M(x=-((-5)))
    M(x=-(((3)))) # WARNING Value for 'x' must be greater than 0
    M(x=-((0)))   # WARNING Value for 'x' must be greater than 0
  """)

  @Test
  @TestFor(issues = ["PY-90015"])
  fun `test alias`() = test("""
    from pydantic import BaseModel, Field

    class M(BaseModel):
        name: str = Field(alias="user_name", min_length=2)
        age: int = Field(validation_alias="user_age", gt=0)

    M(user_name="John", user_age=30)
    M(user_name="J", # WARNING String for 'user_name' must be at least 2 characters long
      user_age=-1)   # WARNING Value for 'user_age' must be greater than 0
  """)

  @Test
  @TestFor(issues = ["PY-90015"])
  fun `test alias choices`() = test("""
      from pydantic import BaseModel, Field, AliasChoices

      class M(BaseModel):
          name: str = Field(validation_alias=AliasChoices("user_name"), min_length=2)
          age: int = Field(validation_alias=AliasChoices("user_age"), gt=0)

      M(user_name="John", user_age=30)
      M(user_name="J", # WARNING String for 'user_name' must be at least 2 characters long
        user_age=-1)   # WARNING Value for 'user_age' must be greater than 0
    """)

  @Test
  @TestFor(issues = ["PY-90015"])
  fun `test field in annotated`() = test("""
    from pydantic import BaseModel, Field
    from typing import Annotated

    class M(BaseModel):
        name: Annotated[str, Field(min_length=2)]

    M(name="John")
    M(name="J") # WARNING String for 'name' must be at least 2 characters long
  """)

  @Test
  @TestFor(issues = ["PY-90015"])
  fun `test multiple field calls in annotated`() = test("""
    from pydantic import BaseModel, Field
    from typing import Annotated

    class M(BaseModel):
        name: Annotated[str, Field(min_length=2), Field(max_length=5)]
        age: Annotated[int, Field(gt=0), Field(lt=200)]

    M(name="John", age=30)
    M(name="J",                    # WARNING String for 'name' must be at least 2 characters long
      age=-1)                      # WARNING Value for 'age' must be greater than 0
    M(name="VeryLongName",         # WARNING String for 'name' must be at most 5 characters long
      age=250)                     # WARNING Value for 'age' must be less than 200
  """)

  @Test
  @TestFor(issues = ["PY-90015"])
  fun `test reference to literal is checked`() = test("""
    from pydantic import BaseModel, Field

    class M(BaseModel):
        age: int = Field(gt=0)
        name: str = Field(min_length=2)
        
    good_age = 30
    good_name = "John"
    M(age=good_age, name=good_name)

    bad_age = -1
    bad_name = "J"
    M(age=bad_age,   # WARNING Value for 'age' must be greater than 0
      name=bad_name) # WARNING String for 'name' must be at least 2 characters long
  """)

  @Test
  @TestFor(issues = ["PY-90015"])
  fun `test conditionally assigned variable is ignored`() = test("""
    from pydantic import BaseModel, Field

    class M(BaseModel):
        age: int = Field(gt=0)

    if input():
        x = -1
    else:
        x = 5
    M(age=x)
  """)

  @Test
  @TestFor(issues = ["PY-90015"])
  // 2^53   = 9,007,199,254,740,992 — last integer representable in Double
  // 2^53+1 = 9,007,199,254,740,993
  // 2^53+1 rounds to 2^53 in Double -> false warning, BigDecimal doesn't round -> no warning
  fun `test large numbers beyond double precision`() = test("""
    from pydantic import BaseModel, Field

    class M(BaseModel):
        x: int = Field(gt=9007199254740992)

    M(x=9007199254740993)
  """)

  @Test
  @TestFor(issues = ["PY-90015"])
  // 2^63-1 = 9,223,372,036,854,775,807 — last integer representable in Long
  // 2^63   = 9,223,372,036,854,775,808
  // 2^63 overflows Long -> false warning, BigDecimal doesn't overflow -> no warning
  fun `test large numbers beyond long range`() = test("""
    from pydantic import BaseModel, Field

    class M(BaseModel):
        x: int = Field(gt=9223372036854775807)

    M(x=9223372036854775808)
  """)

  override val defaultTestOptions: TestOptions = TestOptions(
    enableInspections = setOf(PyPydanticFieldConstraintInspection::class.java),
    copyDirectoryToProject = listOf("stubs/pydantic" to "pydantic"),
  )
}
