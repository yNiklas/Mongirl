# Mongirl💾- The Java-Object storing for MongoDB
Mongirl stores Java objects to MongoDBs.
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
 privId: 6
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

## Annotations for classes
### `@Store`
Stores a given Object based on the objects class annotations.
 + collection: String | The name of the MongoDB collection where objects of this type should be stored
 + (optional, default false) addClasspath: boolean | By true, Mongirl will insert a field named `classPath` to the MongoDB object of the encoded Java Object to determine the right class in the decode process. Only important for subclasses and interface implementations. Since v1.12, Mongirl adds the classpath for subclasses and interface implementations by itself.

### `@Dataclass`
Stores all attributes of the class, no further `@StoreWith` annotations needed. If a field annotated with `@StoreWith` and non-default parameters, the non-default parameters will be used for the store process. Fields in Dataclasses annotated with `@DontStore` won't be stored.
 + collection: String | The name of the MongoDB collection where objects of this type should be stored
 + (optional, default false) addClasspath: boolean | With true, Mongirl will insert a field named `classPath` to the MongoDB object of the encoded Java Object to determine the right class in the decode process. Only important for subclasses and interface implementations. Since v1.12, Mongirl adds the classpath for subclasses and interface implementations by itself.
 + (optional, default true) allAttributesEqualRelevant: boolean | To identify the objects MongoDB entry, Mongirl looks for equal attribute values from attributes annotated with `@Store(equalityRequirement = true, ...)`. With this option set to true, all attributes are implicitly relevant for the equality check. This will result in two objects with the sane values for all stored attributes won't stored seperately. They will be only stored one object for both as long as they have the same attribute values. By the moment when any attribute value changes and the object is stored via `store`, Mongirl will create a second object with the changed values for it.

## API
### ```store```


## Important notes
### Interfaces implementations
 + ```addClasspath``` is an optional attribute for ```@Store``` for classes implementing an Interface or subclasses. It avoids issues on decoding to these classes.
 + By v1.12 - Mongirl will automatically annotate interface implementations and subclasses
 
### Dataclasses
 + Dataclasses (annotated by ```@Dataclass(collection = ...)```) have all attributes implicitly annotated as equality requirement fields.
 So, in the database, there wouldn't be multiple equal entries. Use ```allAttributesEqualRelevant = false``` in the ```@DataClass``` annotation params to change.
