from pydantic import BaseModel, ConfigDict, Field

class Model(BaseModel):
    a: int = Field(alias='A')
    model_config = ConfigDict(
        validate_by_alias=False,
        validate_by_name=False,
    )

# when both are False, we fall back to aliases
_ = Model(<warning descr="Unexpected argument">a=1</warning><warning descr="Parameter 'A' unfilled">)</warning>
_ = Model(A=1)
_ = Model(<warning descr="Parameter 'A' unfilled">)</warning>  # no error