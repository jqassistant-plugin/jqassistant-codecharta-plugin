package org.jqassistant.plugin.codecharta.it.set;

import java.util.function.Consumer;

@SuppressWarnings({ "java:S1612", "java:S1481", "java:S1854" })
public class TestClass implements TestInterface {

    void methodWithLoop() {
        for (int i = 0; i < 10; i++) {
            System.out.println(i);
        }
    }

    public static class InnerClass {
        // anonymous inner class
        Consumer<String> consumer = new Consumer<String>() {
            @Override
            public void accept(String s) {
                System.out.println(s);
            }
        };
    }

}
