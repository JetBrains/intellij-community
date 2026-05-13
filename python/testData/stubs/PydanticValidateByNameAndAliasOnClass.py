from pydantic import BaseModel, ConfigDict, Field

class Model(BaseModel):
    a: int = Field(alias='A')
    model_config = ConfigDict(
        validate_by_name=True,
        validate_by_alias=False,
    )