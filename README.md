# better-repeatable

Eliminates need of using container to group repeatable annotations together.  
Once the compiler plugin sees any annotation that is marked with `dev.xdark.betterrepeatable.Repetable`, any use of `java.lang.annotation.Repeatable` for the place where the annotation is used (class, field, method) will be ignored and annotations will be dumped as-is, in preserved order.

Currently only JDK 8 is supported, due to large amount of changes in javac internals on higher versions.

Primary purpose of change made better-repeatable is to be able to read annotations with ASM or any other bytecode framework in order they appear in source code, which is not possible with built-in `@Repeatable` annotation.

## Breaking behaviour

With better-repeatable changes, built-in Java API to read annotations in places, where there are multiple annotations, will no longer work.  
This is caused by code in `sun.reflect.AnnotationParser`:
```java
Class<? extends Annotation> klass = a.annotationType();
if (AnnotationType.getInstance(klass).retention() == RetentionPolicy.RUNTIME &&
    result.put(klass, a) != null) {
        throw new AnnotationFormatError(
            "Duplicate annotation for class: "+klass+": " + a);
    }
```
