from pydantic import BaseModel, ConfigDict, Field

class Model(BaseModel):
    model_config = ConfigDict(
        validate_by_alias=False,
    )

    my_field: str = Field(alias="my_alias")

Model(<arg1>)