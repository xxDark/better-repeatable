# better-repeatable

Eliminates need of using container to group repeatable annotations together.  
Once the compiler plugin sees any annotation that is marked with `dev.xdark.betterrepeatable.Repetable`, any use of `java.lang.annotation.Repeatable` for the place where the annotation is used (class, field, method) will be ignored and annotations will be dumped as-is, in preserved order.

Currently only JDK 8 is supported, due to large amount of changes in javac internals on higher versions.