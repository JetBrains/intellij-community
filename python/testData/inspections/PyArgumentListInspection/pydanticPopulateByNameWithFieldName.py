from pydantic import BaseModel, Field

class Model(BaseModel, populate_by_name=True):
    a1: str = Field(alias="a2")

_ = Model(a1="value")  # No error - using field name (populate_by_name=True)