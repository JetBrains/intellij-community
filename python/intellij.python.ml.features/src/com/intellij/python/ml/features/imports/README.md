# Ranking in fixing missing import quick fix

## Registry

* `quickfix.ranking.ml` - Status of the system
  * `IN_EXPERIMENT` - ML is enabled depending on machine id.
  * `ENABLED` - ML is enabled
  * `DISABLED` - ML is disabled. ML features aren't computed

## ML Model

ML model loaded from resources of library `jetbrains.ml.models.python.imports.ranking.model`
(the dependency is located in `community/python/python-psi-impl/intellij.python.psi.impl.iml`).


Note, that right now a regression model is used.
You may change that by tweaking `com.intellij.python.ml.features.imports.MLModelService`.

## Contact

Initial implementation: Gleb Marin
ML Model, A/B Experiment Pipelines: Nikita Ermolenko
PyCharm Support: Andrey Matveev
