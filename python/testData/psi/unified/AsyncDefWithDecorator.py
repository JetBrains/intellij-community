class AsyncIterator(AsyncIterable):
    @abstractmethod
    async def __anext__(self):
        raise StopAsyncIteration
