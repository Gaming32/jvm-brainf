package io.github.gaming32.jvmbrainf;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BrainFTest {
    @BeforeAll
    public static void init() {
        System.setProperty("jvmbrainf.check", "true");
    }

    private void assertOutput(String source, String output) {
        final PrintStream sysout = System.out;
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        System.setOut(new PrintStream(baos));
        JVMBrainF.run(source);
        System.setOut(sysout);
        assertEquals(output, baos.toString());
    }

    @Test
    public void testEmpty() {
        assertOutput("", "");
    }

    @Test
    public void testHelloWorld() {
        assertOutput(
            ">++++++++[<+++++++++>-]<.\n" +
                ">++++[<+++++++>-]<+.\n" +
                "+++++++..\n" +
                "+++.\n" +
                ">>++++++[<+++++++>-]<++.\n" +
                "------------.\n" +
                ">++++++[<+++++++++>-]<+.\n" +
                "<.\n" +
                "+++.\n" +
                "------.\n" +
                "--------.\n" +
                ">>>++++[<++++++++>-]<+.",
            "Hello, World!"
        );
    }
}
