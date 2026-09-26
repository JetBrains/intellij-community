import abc


class Abstract(abc.ABC):
    @abc.abstractmethod
    async def start(self):
        pass

    @abc.abstractmethod
    async def stop(self):
        pass


class Conc<caret>rete(Abstract):<EOLError descr="Indent expected"></EOLError>