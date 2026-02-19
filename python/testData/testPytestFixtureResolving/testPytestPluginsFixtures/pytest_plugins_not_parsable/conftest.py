def calculate_fixtures():
    # Imitate custom logic around collecting folders.
    # For the real-world examples see PY-71370
    fixtures_folder = "fixtures"
    subdirectories = ["first", "second"]
    return [f"{fixtures_folder}.{subdir}" for subdir in subdirectories]


pytest_plugins = calculate_fixtures()
