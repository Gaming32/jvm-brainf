package io.github.gaming32.jvmbrainf;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.InstructionAdapter;
import org.objectweb.asm.util.CheckClassAdapter;

import java.io.*;
import java.util.ArrayDeque;
import java.util.Deque;

public final class JVMBrainF {
    private static final int MEMORY_SIZE = 32768;

    private static final class LoopEntry {
        int line, column;
        Label start, end;

        public LoopEntry(int line, int column) {
            this.line = line;
            this.column = column;
            this.start = new Label();
            this.end = new Label();
        }
    }

    private enum RunType {
        POINTER {
            @Override
            void compile(InstructionAdapter meth, int delta) {
                if (delta == 0) return;
                meth.load(1, Type.INT_TYPE);
                meth.iconst(Math.abs(delta));
                meth.visitInsn(delta > 0 ? Opcodes.IADD : Opcodes.ISUB);
                meth.iconst(MEMORY_SIZE - 1);
                meth.and(Type.INT_TYPE);
                meth.store(1, Type.INT_TYPE);
            }
        },
        VALUE {
            @Override
            void compile(InstructionAdapter meth, int delta) {
                if (delta == 0) return;
                meth.load(2, InstructionAdapter.OBJECT_TYPE);
                meth.dup();
                meth.load(1, Type.INT_TYPE);
                meth.dupX1();
                meth.aload(Type.SHORT_TYPE);
                meth.iconst(Math.abs(delta));
                meth.visitInsn(delta > 0 ? Opcodes.IADD : Opcodes.ISUB);
                meth.iconst(255);
                meth.and(Type.SHORT_TYPE);
                meth.astore(Type.SHORT_TYPE);
            }
        };

        abstract void compile(InstructionAdapter meth, int delta);
    }

    private JVMBrainF() {
    }

    public static void main(@NotNull String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java -jar jvm-brainf.jar <script-path>");
            System.exit(1);
            throw new AssertionError();
        }
        final StringBuilder code = new StringBuilder();
        try (final Reader reader = new FileReader(args[0])) {
            final char[] buf = new char[8192];
            int n;
            while ((n = reader.read(buf)) != -1) {
                code.append(buf, 0, n);
            }
        }
        final String fileBaseName = new File(args[0]).getName();
        final String className = removeExtension(fileBaseName);
        final byte[] result;
        try {
            result = maybeCheck(compile(code.toString(), className, fileBaseName).toByteArray());
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.exit(1);
            throw new AssertionError();
        }
        try (final OutputStream out = new FileOutputStream(className + ".class")) {
            out.write(result);
        }
    }

    public static void run(@NotNull String source) {
        try {
            new ClassLoader() {
                public Class<?> loadClassFromBytecode(byte[] bytecode, String className) {
                    return super.defineClass(className, bytecode, 0, bytecode.length);
                }
            }.loadClassFromBytecode(maybeCheck(compile(source, "BrainF", null).toByteArray()), "BrainF")
                .getDeclaredMethod("main", String[].class)
                .invoke(null, new Object[] { new String[0] });
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] maybeCheck(byte[] bytecode) {
        if (Boolean.getBoolean("jvmbrainf.check")) {
            CheckClassAdapter.verify(new ClassReader(bytecode), true, new PrintWriter(System.err));
        }
        return bytecode;
    }

    @NotNull
    public static ClassWriter compile(@NotNull String source, @NotNull String className, @Nullable String sourceFileName) {
        final ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(
            Opcodes.V1_8,
            Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
            className,
            null,
            "java/lang/Object",
            null
        );
        {
            final MethodVisitor mv = cw.visitMethod(
                Opcodes.ACC_PRIVATE,
                "<init>",
                "()V",
                null,
                null
            );
            mv.visitCode();
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                "java/lang/Object",
                "<init>",
                "()V",
                false
            );
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(-1, -1);
            mv.visitEnd();
        }
        {
            final MethodVisitor mv = cw.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "main",
                "([Ljava/lang/String;)V",
                null,
                null
            );
            mv.visitCode();
            compile(new InstructionAdapter(mv), source);
            mv.visitEnd();
        }
        if (sourceFileName != null) {
            cw.visitSource(sourceFileName, null);
        }
        cw.visitEnd();
        return cw;
    }

    private static void compile(InstructionAdapter meth, String source) {
        final Label[] startLabels = { new Label(), new Label(), new Label() };
        meth.mark(startLabels[0]);
        meth.visitLineNumber(1, startLabels[0]);
        meth.iconst(0);
        meth.store(1, Type.INT_TYPE);
        meth.mark(startLabels[1]);
        meth.iconst(MEMORY_SIZE);
        meth.newarray(Type.SHORT_TYPE);
        meth.store(2, InstructionAdapter.OBJECT_TYPE);
        meth.mark(startLabels[2]);
        final Deque<LoopEntry> loopStack = new ArrayDeque<>();
        int line = 1, column = 0;
        RunType runType = null;
        int runDelta = 0;
        for (int i = 0, limit = source.length(); i < limit; i++) {
            column++;
            final char c = source.charAt(i);
            switch (c) {
                case '\n':
                    line++;
                    column = 0;
                    final Label lineLabel = new Label();
                    meth.mark(lineLabel);
                    meth.visitLineNumber(line, lineLabel);
                    break;
                case '>':
                case '<':
                    if (runType == RunType.POINTER) {
                        runDelta += c == '>' ? 1 : -1;
                    } else {
                        if (runType != null) {
                            runType.compile(meth, runDelta);
                        }
                        runDelta = c == '>' ? 1 : -1;
                        runType = RunType.POINTER;
                    }
                    break;
                case '+':
                case '-':
                    if (runType == RunType.VALUE) {
                        runDelta += c == '+' ? 1 : -1;
                    } else {
                        if (runType != null) {
                            runType.compile(meth, runDelta);
                        }
                        runDelta = c == '+' ? 1 : -1;
                        runType = RunType.VALUE;
                    }
                    break;
                case '.':
                    if (runType != null) {
                        runType.compile(meth, runDelta);
                        runType = null;
                    }
                    meth.getstatic(
                        "java/lang/System",
                        "out",
                        "Ljava/io/PrintStream;"
                    );
                    meth.load(2, InstructionAdapter.OBJECT_TYPE);
                    meth.load(1, Type.INT_TYPE);
                    meth.aload(Type.SHORT_TYPE);
                    meth.cast(Type.SHORT_TYPE, Type.CHAR_TYPE);
                    meth.invokevirtual(
                        "java/io/PrintStream",
                        "print",
                        "(C)V",
                        false
                    );
                    break;
                case ',':
                    if (runType != null) {
                        runType.compile(meth, runDelta);
                        runType = null;
                    }
                    meth.load(2, InstructionAdapter.OBJECT_TYPE);
                    meth.load(1, Type.INT_TYPE);
                    meth.getstatic(
                        "java/lang/System",
                        "in",
                        "Ljava/io/InputStream;"
                    );
                    meth.invokevirtual(
                        "java/io/InputStream",
                        "read",
                        "()I",
                        false
                    );
                    meth.iconst(255);
                    meth.and(Type.SHORT_TYPE);
                    meth.astore(Type.SHORT_TYPE);
                    break;
                case '[': {
                    if (runType != null) {
                        runType.compile(meth, runDelta);
                        runType = null;
                    }
                    final LoopEntry labels = new LoopEntry(line, column);
                    meth.mark(labels.start);
                    meth.load(2, InstructionAdapter.OBJECT_TYPE);
                    meth.load(1, Type.INT_TYPE);
                    meth.aload(Type.SHORT_TYPE);
                    meth.ifeq(labels.end);
                    loopStack.push(labels);
                    break;
                }
                case ']': {
                    if (runType != null) {
                        runType.compile(meth, runDelta);
                        runType = null;
                    }
                    if (loopStack.isEmpty()) {
                        throw new IllegalArgumentException("Mismatched ] at line " + line + ", column " + column);
                    }
                    final LoopEntry labels = loopStack.pop();
                    meth.goTo(labels.start);
                    meth.mark(labels.end);
                }
            }
        }
        if (runType != null) {
            runType.compile(meth, runDelta);
        }
        if (!loopStack.isEmpty()) {
            final LoopEntry labels = loopStack.pop();
            throw new IllegalArgumentException("Mismatched [ at line " + labels.line + ", column " + labels.column);
        }
        final Label endLabel = new Label();
        meth.mark(endLabel);
        meth.areturn(Type.VOID_TYPE);
        meth.visitMaxs(-1, -1);
        meth.visitParameter("args", 0);
        meth.visitLocalVariable("args", "[Ljava/lang/String;", null, startLabels[0], startLabels[0], 0);
        meth.visitLocalVariable("i", "I", null, startLabels[1], endLabel, 1);
        meth.visitLocalVariable("memory", "[S", null, startLabels[2], endLabel, 2);
    }

    private static String removeExtension(String filename) {
        final int dotIndex = filename.lastIndexOf('.');
        return dotIndex == -1 ? filename : filename.substring(0, dotIndex);
    }
}