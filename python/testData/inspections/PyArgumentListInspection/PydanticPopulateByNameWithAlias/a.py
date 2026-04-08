from pydantic import BaseModel, Field

class Model(BaseModel):
    model_config = {"populate_by_name": True}

class Model2(Model):
    a1: str = Field(alias="a2")