from typing import List

class Example:
    def method(self,
               lst,      # type: List[str]
               opt=0,    # type: int
               *args,    # type: str
               **kwargs  # type: bool
               ):
        # type: (...) -> int
        """Docstring comes after type comment."""
        pass
