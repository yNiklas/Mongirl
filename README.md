# Mongirl

## Important notes
### Interfaces implementations
 + Annotate the interfaces itself with the ```@Store(collection = COLLECTION_NAME_WHERE_IMPLEMENTATIONS_ARE_STORED)```
 + Annotate the implementation classes that implements the interface with the
 ```addClasspath = true``` parameter (``@Store(collection = COLLECTION_NAME_WHERE_IMPLEMENTATIONS_ARE_STORED, addClasspath = true)``)
 
### Dataclasses
 + Dataclasses (annotated by ```@Dataclass(collection = ...)```) have all attributes implicitly annotated as equality requirement fields.
 So, in the database, there wouldn't be multiple equal entries. Use ```mongirlInstance.setDataClassAttributesAllEqualRelevant(true)``` to change
