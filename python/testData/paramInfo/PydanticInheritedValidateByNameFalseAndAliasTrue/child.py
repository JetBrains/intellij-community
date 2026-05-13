from pydantic import Field
from base import Base

class Child(Base):
    own_field: str = Field(alias="own_alias")