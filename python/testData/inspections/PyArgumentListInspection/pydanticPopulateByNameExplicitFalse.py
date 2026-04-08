from pydantic import BaseModel, Field


class BaseModel1(BaseModel, populate_by_name=True):
    pass


class Model(BaseModel1, populate_by_name=False):
    a1: str = Field(alias="a2")

_ = Model(<warning descr="Unexpected argument">a1="value"</warning><warning descr="Parameter 'a2' unfilled">)</warning>
_ = Model(a2="value")  # No error - using alias