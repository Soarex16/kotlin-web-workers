// support lib

// meta-annotation to create color markers
// we use integers as colors because they form a lattice
@Target(AnnotationTarget.ANNOTATION_CLASS)
annotation class CodeColor(val color: Int)

// user lib

@CodeColor(42) // some unique color id
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
annotation class UnitTestCode

// user code
abstract class Test {
    @UnitTestCode
    abstract fun run()
}

fun runTest(test: @UnitTestCode () -> Unit) {
    try {
        test()
    } catch (e : Throwable) {
        // report fail
    } finally {
        // clean up
    }
}

abstract class Test2 {
    abstract fun run()
}

// in case of libraries
fun runTest(test1: Test, test2: @UnitTestCode Test2) {
    try {
        test1.run()
        test2.run() // use case: library code
    } catch (e : Throwable) {
        // report fail
    } finally {
        // clean up
    }
}

/*
root candidates:
- Function marked with @Annotation
    - if function implements something 
*/
