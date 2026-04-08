from pydantic import BaseModel, ConfigDict, Field


class Model(BaseModel):
    model_config = ConfigDict(populate_by_name=True)
    a1: str = Field(alias="a2")


_ = Model(a1="value")  # No error - populate_by_name in model_config
_ = Model(a2="value")  # No error - using alias