from pydantic import Field
from mod import BaseModel1


class Model2(BaseModel1):
    a1: str = Field(alias="a2")


_ = Model2(a1="value")  # No error - populate_by_name inherited
_ = Model2(a2="value")  # No error - using alias