// FILE: a/A.java
package a

//- FileB=vname("","test","plugins/kythe-indexer/testData/indexer/multifile","/B.kt","")
//-  .node/kind file

//- @A defines/binding ClassA
//- ClassA.node/kind record
//- ClassA.subkind class
//- ClassA childof FileA
class A {
    //- @"B" ref ClassB
    //- ClassB.node/kind record
    //- ClassB childof FileB
    val b: B? = null
}


// FILE: B.kt
package a

//- FileB=vname("","test","plugins/kythe-indexer/testData/indexer/multifile","/B.kt","")
//-  .node/kind file

//- @B defines/binding ClassB
//- ClassB.node/kind record
//- ClassB.subkind class
//- ClassB childof FileB
class B {
    //- @"A" ref ClassA=vname("CLASS:a.A", _, _, _, _)
    //- ClassA.node/kind record
    val a: A? = null
}