package org.jqassistant.plugin.codecharta.it.set;

import java.util.function.Consumer;

@SuppressWarnings({ "java:S1612", "java:S1481", "java:S1854" })
public class TestClass implements TestInterface {

    /**
     * used for coupling to {@link TestInterface}
     */
    private TestInterface delegate;

    /**
     * Provides loc and cc
     */
    void methodWithLoop() {
        for (int i = 0; i < 10; i++) {
            System.out.println(i);
        }
    }

    /**
     * Inner class to be aggregated to {@link TestClass}
     */
    public static class InnerClass {
        // anonymous inner class to be aggregated to {@link TestClass}
        Consumer<String> consumer = new Consumer<>() {

            /**
             * Provides loc and cc
             */
            @Override
            public void accept(String s) {
                System.out.println(s);
            }
        };
    }

}
