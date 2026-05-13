from pydantic import BaseModel, ConfigDict, Field

class Model(BaseModel):
    my_field: str = Field(alias='my_alias')
    model_config = ConfigDict(
        validate_by_name=False,
        validate_by_alias=True,
    )

_ = Model(my_alias='foo')  # no error
_ = Model(<warning descr="Unexpected argument">my_field='foo'</warning><warning descr="Parameter 'my_alias' unfilled">)</warning>