[Sangria](http://sangria-graphql.org/) [Amazon Ion](http://amznlabs.github.io/ion-docs/index.html) marshalling.

[![Build Status](https://travis-ci.org/sangria-graphql/sangria-ion.svg?branch=master)](https://travis-ci.org/sangria-graphql/sangria-ion) [![Coverage Status](http://coveralls.io/repos/sangria-graphql/sangria-ion/badge.svg?branch=master&service=github)](http://coveralls.io/github/sangria-graphql/sangria-ion?branch=master) [![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.sangria-graphql/sangria-ion_2.11/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.sangria-graphql/sangria-ion_2.11) [![License](http://img.shields.io/:license-Apache%202-brightgreen.svg)](http://www.apache.org/licenses/LICENSE-2.0.txt) [![Join the chat at https://gitter.im/sangria-graphql/sangria](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/sangria-graphql/sangria?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

SBT Configuration:

```scala
libraryDependencies += "org.sangria-graphql" %% "sangria-ion" % "0.1.0"
```

## Example

Here is small example of how you can use it, which also demonstrates custom scalar values that are natively supported by Ion.
 
First let's define the scalar values:

```scala
val dateFormat = new SimpleDateFormat("yyyy-MM-dd")

case object DateCoercionViolation extends ValueCoercionViolation("Date value expected")
case object BinaryCoercionViolation extends ValueCoercionViolation("Binary data is not supported as input")

def parseDate(s: String) = Try(dateFormat.parse(s)) match {
  case Success(d) ⇒ Right(d)
  case Failure(error) ⇒ Left(DateCoercionViolation)
}

val DateType = ScalarType[Date]("Date",
  coerceOutput = (d, caps) ⇒
    if (caps.contains(DateSupport)) d
    else dateFormat.format(d),
  coerceUserInput = {
    case s: String ⇒ parseDate(s)
    case _ ⇒ Left(DateCoercionViolation)
  },
  coerceInput = {
    case ast.StringValue(s, _) ⇒ parseDate(s)
    case _ ⇒ Left(DateCoercionViolation)
  })

val BlobType = ScalarType[Array[Byte]]("Blob",
  coerceOutput = (d, _) ⇒ d,
  coerceUserInput = _ ⇒ Left(BinaryCoercionViolation),
  coerceInput = _ ⇒ Left(BinaryCoercionViolation))

val ClobType = ScalarType[Array[Byte]]("Clob",
  coerceOutput = (d, _) ⇒ d,
  coerceUserInput = _ ⇒ Left(BinaryCoercionViolation),
  coerceInput = _ ⇒ Left(BinaryCoercionViolation),
  scalarInfo = Set(IonClobScalar))
```

Please notice that  `Date` type produces `java.util.Date` only when this capability is supported by the marshaller. 
Otherwise it produces a `String` alternative. `Clob` type also instructs marshaller to to use Ion `clob` 
type instead of `blob` for a byte array.

In order to use Ion marshalling, you also need an implicit instance of `IonSystem` in scope:

```scala
import sangria.marshalling.ion._

implicit val ionSystem = IonSystemBuilder.standard().build()

val result: Future[IonValue] = Executor.execute(schema, query)
```

Now you should be able to write `IonValue` to a binary or a text format.  

## License

**sangria-ion** is licensed under [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).
