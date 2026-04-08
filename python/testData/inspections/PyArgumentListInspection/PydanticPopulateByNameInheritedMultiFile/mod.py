from pydantic import BaseModel


class BaseModel1(BaseModel, populate_by_name=True):
    pass