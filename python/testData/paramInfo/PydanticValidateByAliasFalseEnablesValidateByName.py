from pydantic import BaseModel, Field


class A(
    BaseModel,
    validate_by_alias=False,
):
    a: int = Field(alias="my_alias")

A(<arg1>)