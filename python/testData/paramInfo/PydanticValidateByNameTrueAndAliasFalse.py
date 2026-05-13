from pydantic import BaseModel, ConfigDict, Field

class Model(BaseModel):
    my_field: str = Field(alias='my_alias')
    model_config = ConfigDict(
        validate_by_name=True,
        validate_by_alias=False,
    )

Model(<arg1>)