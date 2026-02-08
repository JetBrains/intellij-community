import abc


class Abstract(abc.ABC):
    @abc.abstractmethod
    async def run(self):
        pass


def test_class():
    class Concrete(Abstract):
        async def run(self):
            pass
