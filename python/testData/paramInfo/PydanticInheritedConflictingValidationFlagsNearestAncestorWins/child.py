from pydantic import Field
from base import Base

class Child(Base):
    child_field: str = Field(alias="child_alias")