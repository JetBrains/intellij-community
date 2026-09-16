import abc


class Abstract(abc.ABC):
    @abc.abstractmethod
    async def start(self):
        pass

    @abc.abstractmethod
    async def stop(self):
        pass


def test_class():
    class Concrete(Abstract):
        async def start(self):
            pass

        async def stop(self):
            pass