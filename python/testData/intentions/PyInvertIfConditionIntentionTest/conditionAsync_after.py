async def get_value():
  return "not-none"

if not await get_value():
    print("None")
else:
    print("Not none")