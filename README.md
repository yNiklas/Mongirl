# Mongirl💾- The Java-Object storing for MongoDB
Mongirl stores Java objects annotation-based to MongoDBs, so you never need to write codecs again.
> Take a look at this example:
```java
@Store(collection = "myMongoCollection")
class Example {
   @StoreWith(key = "privId")
   private int id = 42;
 
   @StoreWith
   public String username = "Panda";
 
   @StoreWith
   private SubExample subEx = new SubExample();
}

@Store(collection = "subobjects")
class SubExample {
   @StoreWith
   private String foo = "bar";
}
```
... will result in the database entries:
+ Collection "myMongoCollection"
```
{
 _id: ObjectId('...')
 privId: 42
 username: "Panda"
 subEx: ObjectId('ABC')
}
```
+ Collection "subobjects"
```
{
 _id: ObjectId('ABC')
 foo: "bar"
}
```

## Initialization / Connect Mongirl
### Initialize Mongirl (with credentials)
To connect Mongirl to a MongoDB secured by credentials, use:
```new Mongirl(host, port, dbName, username, authDB, password)```
while the parameters are:
+ host: String     | Host IP adress of the MongoDB. ``localhost`` is a valid value
+ port: int        | The Port of the MongoDB. Default value for MongoDBs is ``27017``
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
Enables storage operations based on the attributes annotations and the following specification:
| Parameter    | Optional   | Description|
|--------------|-----------|------------|
| collection | required | The name of the MongoDB collection where objects of this type should be stored |
| addClasspath | true (default false) | When true, Mongirl will insert a field named `classPath` to the MongoDB object of the encoded Java Object to determine the right class in the decode process. Only important for subclasses and interface implementations. Since v1.12, Mongirl adds the classpath for subclasses and interface implementations by itself. |

### `@Dataclass`
Stores all attributes of the class, no further `@StoreWith` annotations needed. If a field annotated with `@StoreWith` and non-default parameters, the non-default parameters will be used for the store process. Fields in Dataclasses annotated with `@DontStore` won't be stored.
| Parameter    | Optional   | Description|
|--------------|-----------|------------|
| collection | required | The name of the MongoDB collection where objects of this class should be stored |
| addClasspath | optional (default false) | With true, Mongirl will insert a field named `classPath` to the MongoDB object of the encoded Java Object to determine the right class in the decode process. Only important for subclasses and interface implementations. Since v1.12, Mongirl adds the classpath for subclasses and interface implementations by itself |
| allAttributesEqualRelevant | optional (default true) | To identify the objects database entry, Mongirl looks for equal attribute values from attributes annotated with `@Store(equalityRequirement = true, ...)`. With this option set to true, all attributes are implicitly relevant for the equality check. This will result in two objects with the sane values for all stored attributes won't stored seperately. They will be only stored one object for both as long as they have the same attribute values. By the moment when any attribute value changes and the object is stored via `store`, Mongirl will create a second object with the changed values for it |

## Annotations for attributes
### `@StoreWith`
Indicates an attribute to be stored if an object from the attributes class is being stored. The annotation provides the following parameters:
| Parameter    | Default   | Description|
|--------------|-----------|------------|
| key          | field name | Specifies the attributes name in the MongoDB objects |
| equalityRequirement      | false  | Specifies whether an attributes is part of an unique identification of its object. With set to true, this attribute will used to check, whether an database entry is the same as a given object. In most cases, attributes like `id`, `email`, `uniqueUsername` or `uuid` should be annotated with `equalityRequirement = true`. If no attribute is annotated with this parameter set to true, an object of this class will result in a new database entry on every `store` operation since Mongirl cannot determine whether an stored entry belongs to the given object based on values of `equalityRequirement` attributes. |

## API
### ```store```
| Parameter    | Description|
|--------------|------------|
| object | An object with annotated attributes to store. |

After annotating the objects attributes, the `store` operation will store the object with the annotated attributes as key/values in a MongoDB document to the MongoDB. If an object is already stored in the database (which is determined through the `@StoreWith` annotation with `equalityRequirement = true`), the `store` operation will update the regarding database entry to the current values. Otherwise, if the object wasn't stored before, this operation creates a new MongoDB document in the collection specified in the `@Store` annotation from the objects class.

Returns the MongoDB-`ObjectId` in the case when the object was updated or the `InsertedId` when the object was newly stored.

### `getObjectIdFrom`
| Parameter    | Description|
|--------------|------------|
| object | An java object stored in the database. |

Returnes the MongoDB-`ObjectId` from the given java object if it's stored. Otherwise returns null.

### `decodeFromFilters`
| Parameter    | Description|
|--------------|------------|
| targetClass | The java class of the object to decode. |
| ...filters | Array containing key/value`Pair`s with specify the target object. |

Decodes a database stored object based on given filters and the class of the to be decoded
object.

### `decodeAll`
| Parameter    | Description|
|--------------|------------|
| targetClass | The java class of the objects to decode. |

Decodes all objects stored in the MongoDB from the collection named in the annotation parameter `collection` of `@Store` or `@Dataclass`.
Returns them in a `List`.

## Important notes
### Constructors
 + Every class from which objects should be stored **must** have a public constructor. It does not matter whether it's a default constructor or some with parameters. Without, Mongirl cannot instantiate this class objects on decode operations.

### Arrays
 + Currently, only one-dimensional Arrays are supported

### Interfaces implementations
 + ```addClasspath``` is an optional attribute for ```@Store``` for classes implementing an Interface or subclasses. It avoids issues on decoding to these classes.
 + By v1.12 - Mongirl will automatically annotate interface implementations and subclasses
 
### Dataclasses
 + Dataclasses (annotated by ```@Dataclass(collection = ...)```) have all attributes implicitly annotated as equality requirement fields.
 So, in the database, there wouldn't be multiple equal entries. Use ```allAttributesEqualRelevant = false``` in the ```@DataClass``` annotation params to change.
