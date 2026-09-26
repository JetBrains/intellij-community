import abc


class Abstract(abc.ABC):
    @abc.abstractmethod
    async def start(self):
        pass

    @abc.abstractmethod
    async def stop(self):
        pass


class Concrete(Abstract):
    async def start(self):
        pass

    async def stop(self):
        pass