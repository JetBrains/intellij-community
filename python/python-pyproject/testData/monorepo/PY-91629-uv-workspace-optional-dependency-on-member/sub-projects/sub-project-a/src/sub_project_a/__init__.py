import sub_project_b


def ping() -> str:
    return f"sub-project-a -> {sub_project_b.ping()}"
