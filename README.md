# Mongirl💾- The Java-Object storing for MongoDB
Mongirl stores your Java objects to MongoDBs.
> Take a look at this example:
```java
@Store(collection = "myMongoCollection")
class Example {
 @StoreWith(key = "privId") private int id = 6;
 @StoreWith public String username = "Panda";
 @StoreWith private SubExample subEx = new SubExample();
}

@Dataclass(collection = "subobjects")
class SubExample {
 private String foo = "bar";
}
```
... will result in the database entries:
+ Collection "myMongoCollection"
```
{
 _id: ObjectId('...')
 pridId: 6
 username: "Panda"
 subEx: ObjectId('gf8568ffgfdh675')
}
```
+ Collection "subobjects"
```
{
 _id: ObjectId('gf8568ffgfdh675')
 foo: "bar"
}
```
(ObjectIds aren't real ones here, just for the example)

## Initialization / Connect Mongirl
### Initialize Mongirl (with credentials)
To connect Mongirl to a MongoDB secured by credentials, use:
```new Mongirl(host, port, dbName, username, authDB, password)```
while the parameters are:
+ host: String     | Host IP adress of the MongoDB. ``localhost`` is a valid value
+ port: int        | The Port of the MongoDB. Default value for MongoDBs are ``27017``
+ dbName: String   | The name of the database in the MongoDB
+ username: String | The username of the MongoDB login
+ authDB: String   | The database auth. May be equal to the database name
+ password: String | The password according to the username and auth

### Initialize Mongirl without credentials
Use the simplified constructor:
```new Mongirl(host, port, dbName)```
while the parameters are:
+ host: String     | Host IP adress of the MongoDB. ``localhost`` is a valid value
+ port: int        | The Port of the MongoDB. Default value for MongoDBs are ``27017``
+ dbName: String   | The name of the database in the MongoDB

## Important notes
### Interfaces implementations
 + Annotate the interfaces itself with the ```@Store(collection = COLLECTION_NAME_WHERE_IMPLEMENTATIONS_ARE_STORED)```
 + !! Optional from v1.12 - Mongirl will now automatically annotate interface implementations and subclasses !! (Annotate the implementation classes that implements the interface with the
 ```addClasspath = true``` parameter (``@Store(collection = COLLECTION_NAME_WHERE_IMPLEMENTATIONS_ARE_STORED, addClasspath = true)``))
 
### Dataclasses
 + Dataclasses (annotated by ```@Dataclass(collection = ...)```) have all attributes implicitly annotated as equality requirement fields.
 So, in the database, there wouldn't be multiple equal entries. Use ```allAttributesEqualRelevant = false``` in the ```@DataClass``` annotation params to change.
