from typing import Literal, TypeVar

Code = TypeVar("Code", bound=int)
Response = TypeVar("Response", bound=list | str | None)
Error = TypeVar("Error", default=str, bound= str | None)

Http = tuple[Code, Response]
HttpOk = Http[Literal[200], Response]
Http400 = Http[Literal[400], Error]
Http401 = Http[Literal[401], Error]
Http403 = Http[Literal[403], Error]
Http404 = Http[Literal[404], Error]
Http422 = Http[Literal[422], ErrorResponse[list[ErrorDetails]]]
Http500 = Http[Literal[500], Literal["Internal Server Error"]]