package org.jqassistant.plugin.codecharta.it.set;

import java.util.function.Consumer;

@SuppressWarnings({ "java:S1612", "java:S1481", "java:S1854" })
public class TestClass implements TestInterface {

    void methodWithAnoynmousInnerClass() {
        Consumer<String> consumer = s -> System.out.println(s);
    }

    static class InnerClass {
    }

}
