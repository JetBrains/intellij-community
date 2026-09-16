import abc


class Abstract(abc.ABC):
    @abc.abstractmethod
    async def start(self):
        pass

    @abc.abstractmethod
    async def stop(self):
        pass


def test_class():
    class Conc<caret>rete(Abstract):<EOLError descr="Indent expected"></EOLError>