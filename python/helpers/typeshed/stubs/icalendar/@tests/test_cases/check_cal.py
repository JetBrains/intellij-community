from icalendar.cal import Component

component = Component()
component.add("summary", "Test 1")
component.add("dtstart", "2022-01-01", encode=True)
component.add("dtend", "2022-01-02", encode=False)
component.add("location", "Test 3", {})
component.add("dtstamp", "2022-01-03", parameters={}, encode=True)
component.add("description", "Test 2", parameters={}, encode=False)  # type: ignore
