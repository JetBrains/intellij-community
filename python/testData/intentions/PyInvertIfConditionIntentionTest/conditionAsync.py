async def get_value():
  return "not-none"

<caret>if await get_value():
    print("Not none")
else:
    print("None")