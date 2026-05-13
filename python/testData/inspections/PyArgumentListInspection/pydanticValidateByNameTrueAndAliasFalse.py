from pydantic import BaseModel, ConfigDict, Field

class Model(BaseModel):
    my_field: str = Field(validation_alias='my_alias')
    model_config = ConfigDict(
        validate_by_name=True,
        validate_by_alias=False,
    )

_ = Model(my_field='foo')  # no error
_ = Model(<warning descr="Unexpected argument">my_alias='foo'</warning><warning descr="Parameter 'my_field' unfilled">)</warning>