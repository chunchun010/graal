/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package org.graalvm.compiler.core.jdk9.test;

import static jdk.internal.misc.Unsafe.ARRAY_BOOLEAN_BASE_OFFSET;
import static jdk.internal.misc.Unsafe.ARRAY_BYTE_BASE_OFFSET;
import static jdk.internal.misc.Unsafe.ARRAY_CHAR_BASE_OFFSET;
import static jdk.internal.misc.Unsafe.ARRAY_DOUBLE_BASE_OFFSET;
import static jdk.internal.misc.Unsafe.ARRAY_FLOAT_BASE_OFFSET;
import static jdk.internal.misc.Unsafe.ARRAY_INT_BASE_OFFSET;
import static jdk.internal.misc.Unsafe.ARRAY_LONG_BASE_OFFSET;
import static jdk.internal.misc.Unsafe.ARRAY_OBJECT_BASE_OFFSET;
import static jdk.internal.misc.Unsafe.ARRAY_SHORT_BASE_OFFSET;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.StructuredGraph.Builder;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.test.AddExports;
import org.junit.Assert;

import jdk.internal.misc.Unsafe;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.InvalidInstalledCodeException;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * This is based on {@code test/hotspot/jtreg/compiler/unsafe/UnsafeGetStableArrayElement.java} and
 * differs in that it only asserts the behavior of Graal with respect to reading an element from a
 * {@code Stable} array via Unsafe.
 */
@AddExports("java.base/jdk.internal.misc")
public class UnsafeGetStableArrayElement extends GraalCompilerTest {
    // @formatter:off
    private static final List<Object> StableArrays = new ArrayList<>();
    private static <T> T register(T t) {  StableArrays.add(t); return t; }

    // These are treated as @Stable fields thanks to the applyStable method
    static final boolean[] STABLE_BOOLEAN_ARRAY = register(new boolean[16]);
    static final    byte[]    STABLE_BYTE_ARRAY = register(new    byte[16]);
    static final   short[]   STABLE_SHORT_ARRAY = register(new   short[8]);
    static final    char[]    STABLE_CHAR_ARRAY = register(new    char[8]);
    static final     int[]     STABLE_INT_ARRAY = register(new     int[4]);
    static final    long[]    STABLE_LONG_ARRAY = register(new    long[2]);
    static final   float[]   STABLE_FLOAT_ARRAY = register(new   float[4]);
    static final  double[]  STABLE_DOUBLE_ARRAY = register(new  double[2]);
    static final  Object[]  STABLE_OBJECT_ARRAY = register(new  Object[4]);

    static {
        Setter.reset();
    }
    static final Unsafe U = Unsafe.getUnsafe();

    static class Setter {
        private static void setZ(boolean defaultVal) { STABLE_BOOLEAN_ARRAY[0] = defaultVal ? false :                true; }
        private static void setB(boolean defaultVal) { STABLE_BYTE_ARRAY[0]    = defaultVal ?     0 :      Byte.MAX_VALUE; }
        private static void setS(boolean defaultVal) { STABLE_SHORT_ARRAY[0]   = defaultVal ?     0 :     Short.MAX_VALUE; }
        private static void setC(boolean defaultVal) { STABLE_CHAR_ARRAY[0]    = defaultVal ?     0 : Character.MAX_VALUE; }
        private static void setI(boolean defaultVal) { STABLE_INT_ARRAY[0]     = defaultVal ?     0 :   Integer.MAX_VALUE; }
        private static void setJ(boolean defaultVal) { STABLE_LONG_ARRAY[0]    = defaultVal ?     0 :      Long.MAX_VALUE; }
        private static void setF(boolean defaultVal) { STABLE_FLOAT_ARRAY[0]   = defaultVal ?     0 :     Float.MAX_VALUE; }
        private static void setD(boolean defaultVal) { STABLE_DOUBLE_ARRAY[0]  = defaultVal ?     0 :    Double.MAX_VALUE; }
        private static void setL(boolean defaultVal) { STABLE_OBJECT_ARRAY[0]  = defaultVal ?  null :        new Object(); }

        static void reset() {
            setZ(false);
            setB(false);
            setS(false);
            setC(false);
            setI(false);
            setJ(false);
            setF(false);
            setD(false);
            setL(false);
        }
    }

    static class Test {
        static void changeZ() { Setter.setZ(true); }
        static void changeB() { Setter.setB(true); }
        static void changeS() { Setter.setS(true); }
        static void changeC() { Setter.setC(true); }
        static void changeI() { Setter.setI(true); }
        static void changeJ() { Setter.setJ(true); }
        static void changeF() { Setter.setF(true); }
        static void changeD() { Setter.setD(true); }
        static void changeL() { Setter.setL(true); }

        static boolean testZ_Z() { return U.getBoolean(STABLE_BOOLEAN_ARRAY, ARRAY_BOOLEAN_BASE_OFFSET); }
        static byte    testZ_B() { return U.getByte(   STABLE_BOOLEAN_ARRAY, ARRAY_BOOLEAN_BASE_OFFSET); }
        static short   testZ_S() { return U.getShort(  STABLE_BOOLEAN_ARRAY, ARRAY_BOOLEAN_BASE_OFFSET); }
        static char    testZ_C() { return U.getChar(   STABLE_BOOLEAN_ARRAY, ARRAY_BOOLEAN_BASE_OFFSET); }
        static int     testZ_I() { return U.getInt(    STABLE_BOOLEAN_ARRAY, ARRAY_BOOLEAN_BASE_OFFSET); }
        static long    testZ_J() { return U.getLong(   STABLE_BOOLEAN_ARRAY, ARRAY_BOOLEAN_BASE_OFFSET); }
        static float   testZ_F() { return U.getFloat(  STABLE_BOOLEAN_ARRAY, ARRAY_BOOLEAN_BASE_OFFSET); }
        static double  testZ_D() { return U.getDouble( STABLE_BOOLEAN_ARRAY, ARRAY_BOOLEAN_BASE_OFFSET); }

        static boolean testB_Z() { return U.getBoolean(STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET); }
        static byte    testB_B() { return U.getByte(   STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET); }
        static short   testB_S() { return U.getShort(  STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET); }
        static char    testB_C() { return U.getChar(   STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET); }
        static int     testB_I() { return U.getInt(    STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET); }
        static long    testB_J() { return U.getLong(   STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET); }
        static float   testB_F() { return U.getFloat(  STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET); }
        static double  testB_D() { return U.getDouble( STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET); }

        static boolean testS_Z() { return U.getBoolean(STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET); }
        static byte    testS_B() { return U.getByte(   STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET); }
        static short   testS_S() { return U.getShort(  STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET); }
        static char    testS_C() { return U.getChar(   STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET); }
        static int     testS_I() { return U.getInt(    STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET); }
        static long    testS_J() { return U.getLong(   STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET); }
        static float   testS_F() { return U.getFloat(  STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET); }
        static double  testS_D() { return U.getDouble( STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET); }

        static boolean testC_Z() { return U.getBoolean(STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET); }
        static byte    testC_B() { return U.getByte(   STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET); }
        static short   testC_S() { return U.getShort(  STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET); }
        static char    testC_C() { return U.getChar(   STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET); }
        static int     testC_I() { return U.getInt(    STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET); }
        static long    testC_J() { return U.getLong(   STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET); }
        static float   testC_F() { return U.getFloat(  STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET); }
        static double  testC_D() { return U.getDouble( STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET); }

        static boolean testI_Z() { return U.getBoolean(STABLE_INT_ARRAY, ARRAY_INT_BASE_OFFSET); }
        static byte    testI_B() { return U.getByte(   STABLE_INT_ARRAY, ARRAY_INT_BASE_OFFSET); }
        static short   testI_S() { return U.getShort(  STABLE_INT_ARRAY, ARRAY_INT_BASE_OFFSET); }
        static char    testI_C() { return U.getChar(   STABLE_INT_ARRAY, ARRAY_INT_BASE_OFFSET); }
        static int     testI_I() { return U.getInt(    STABLE_INT_ARRAY, ARRAY_INT_BASE_OFFSET); }
        static long    testI_J() { return U.getLong(   STABLE_INT_ARRAY, ARRAY_INT_BASE_OFFSET); }
        static float   testI_F() { return U.getFloat(  STABLE_INT_ARRAY, ARRAY_INT_BASE_OFFSET); }
        static double  testI_D() { return U.getDouble( STABLE_INT_ARRAY, ARRAY_INT_BASE_OFFSET); }

        static boolean testJ_Z() { return U.getBoolean(STABLE_LONG_ARRAY, ARRAY_LONG_BASE_OFFSET); }
        static byte    testJ_B() { return U.getByte(   STABLE_LONG_ARRAY, ARRAY_LONG_BASE_OFFSET); }
        static short   testJ_S() { return U.getShort(  STABLE_LONG_ARRAY, ARRAY_LONG_BASE_OFFSET); }
        static char    testJ_C() { return U.getChar(   STABLE_LONG_ARRAY, ARRAY_LONG_BASE_OFFSET); }
        static int     testJ_I() { return U.getInt(    STABLE_LONG_ARRAY, ARRAY_LONG_BASE_OFFSET); }
        static long    testJ_J() { return U.getLong(   STABLE_LONG_ARRAY, ARRAY_LONG_BASE_OFFSET); }
        static float   testJ_F() { return U.getFloat(  STABLE_LONG_ARRAY, ARRAY_LONG_BASE_OFFSET); }
        static double  testJ_D() { return U.getDouble( STABLE_LONG_ARRAY, ARRAY_LONG_BASE_OFFSET); }

        static boolean testF_Z() { return U.getBoolean(STABLE_FLOAT_ARRAY, ARRAY_FLOAT_BASE_OFFSET); }
        static byte    testF_B() { return U.getByte(   STABLE_FLOAT_ARRAY, ARRAY_FLOAT_BASE_OFFSET); }
        static short   testF_S() { return U.getShort(  STABLE_FLOAT_ARRAY, ARRAY_FLOAT_BASE_OFFSET); }
        static char    testF_C() { return U.getChar(   STABLE_FLOAT_ARRAY, ARRAY_FLOAT_BASE_OFFSET); }
        static int     testF_I() { return U.getInt(    STABLE_FLOAT_ARRAY, ARRAY_FLOAT_BASE_OFFSET); }
        static long    testF_J() { return U.getLong(   STABLE_FLOAT_ARRAY, ARRAY_FLOAT_BASE_OFFSET); }
        static float   testF_F() { return U.getFloat(  STABLE_FLOAT_ARRAY, ARRAY_FLOAT_BASE_OFFSET); }
        static double  testF_D() { return U.getDouble( STABLE_FLOAT_ARRAY, ARRAY_FLOAT_BASE_OFFSET); }

        static boolean testD_Z() { return U.getBoolean(STABLE_DOUBLE_ARRAY, ARRAY_DOUBLE_BASE_OFFSET); }
        static byte    testD_B() { return U.getByte(   STABLE_DOUBLE_ARRAY, ARRAY_DOUBLE_BASE_OFFSET); }
        static short   testD_S() { return U.getShort(  STABLE_DOUBLE_ARRAY, ARRAY_DOUBLE_BASE_OFFSET); }
        static char    testD_C() { return U.getChar(   STABLE_DOUBLE_ARRAY, ARRAY_DOUBLE_BASE_OFFSET); }
        static int     testD_I() { return U.getInt(    STABLE_DOUBLE_ARRAY, ARRAY_DOUBLE_BASE_OFFSET); }
        static long    testD_J() { return U.getLong(   STABLE_DOUBLE_ARRAY, ARRAY_DOUBLE_BASE_OFFSET); }
        static float   testD_F() { return U.getFloat(  STABLE_DOUBLE_ARRAY, ARRAY_DOUBLE_BASE_OFFSET); }
        static double  testD_D() { return U.getDouble( STABLE_DOUBLE_ARRAY, ARRAY_DOUBLE_BASE_OFFSET); }

        @SuppressWarnings("removal")
        static Object  testL_L() { return U.getObject( STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET); }
        static boolean testL_Z() { return U.getBoolean(STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET); }
        static byte    testL_B() { return U.getByte(   STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET); }
        static short   testL_S() { return U.getShort(  STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET); }
        static char    testL_C() { return U.getChar(   STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET); }
        static int     testL_I() { return U.getInt(    STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET); }
        static long    testL_J() { return U.getLong(   STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET); }
        static float   testL_F() { return U.getFloat(  STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET); }
        static double  testL_D() { return U.getDouble( STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET); }

        static short   testS_U() { return U.getShortUnaligned(STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET + 1); }
        static char    testC_U() { return U.getCharUnaligned(  STABLE_CHAR_ARRAY,  ARRAY_CHAR_BASE_OFFSET + 1); }
        static int     testI_U() { return U.getIntUnaligned(    STABLE_INT_ARRAY,   ARRAY_INT_BASE_OFFSET + 1); }
        static long    testJ_U() { return U.getLongUnaligned(  STABLE_LONG_ARRAY,  ARRAY_LONG_BASE_OFFSET + 1); }
    }
    // @formatter:on

    void run(Callable<?> c) throws Exception {
        run(c, null, null);
    }

    @Override
    protected StructuredGraph parse(Builder builder, PhaseSuite<HighTierContext> graphBuilderSuite) {
        StructuredGraph graph = super.parse(builder, graphBuilderSuite);
        applyStable(graph);
        return graph;
    }

    /**
     * Finds each {@link ConstantNode} in {@code graph} that wraps one of the {@link #StableArrays}
     * and replaces it with {@link ConstantNode} with a
     * {@linkplain ConstantNode#getStableDimension() stable dimension} of 1.
     */
    private void applyStable(StructuredGraph graph) {
        if (graph.method().getDeclaringClass().toJavaName().equals(Test.class.getName())) {
            SnippetReflectionProvider snippetReflection = getSnippetReflection();
            for (ConstantNode cn : graph.getNodes().filter(ConstantNode.class)) {
                JavaConstant javaConstant = (JavaConstant) cn.getValue();
                Object obj = snippetReflection.asObject(Object.class, javaConstant);
                if (StableArrays.contains(obj)) {
                    ConstantNode stableConstant = ConstantNode.forConstant(javaConstant, 1, cn.isDefaultStable(), getMetaAccess());
                    cn.replace(graph, stableConstant);
                    break;
                }
            }
        }
    }

    private static void assertEQ(CompiledMethod compiledMethod, Object left, Object right) {
        Assert.assertEquals(String.valueOf(compiledMethod.method), left, right);
    }

    private static void assertNE(CompiledMethod compiledMethod, Object left, Object right) {
        Assert.assertNotEquals(String.valueOf(compiledMethod.method), left, right);
    }

    static class CompiledMethod {
        final ResolvedJavaMethod method;
        final InstalledCode code;

        CompiledMethod(ResolvedJavaMethod method, InstalledCode code) {
            this.method = method;
            this.code = code;
        }

        Object call() throws InvalidInstalledCodeException {
            return this.code.executeVarargs();
        }
    }

    /**
     * Compile the method called by the lambda wrapped by {@code c}.
     */
    private CompiledMethod compile(Callable<?> c) {
        ResolvedJavaMethod m = getResolvedJavaMethod(c.getClass(), "call");
        StructuredGraph graph = parseEager(m, AllowAssumptions.NO);
        List<ResolvedJavaMethod> invokedMethods = StreamSupport.stream(graph.getInvokes().spliterator(), false).map((inv) -> inv.getTargetMethod()).collect(Collectors.toList());
        Assert.assertEquals(String.valueOf(invokedMethods), 1, invokedMethods.size());
        ResolvedJavaMethod invokedMethod = invokedMethods.get(0);
        return new CompiledMethod(invokedMethod, getCode(invokedMethods.get(0)));
    }

    void run(Callable<?> c, Runnable sameResultAction, Runnable changeResultAction) throws Exception {
        Object first = c.call();
        CompiledMethod cm = compile(c);

        if (sameResultAction != null) {
            sameResultAction.run();
            assertEQ(cm, first, cm.call());
        }

        if (changeResultAction != null) {
            changeResultAction.run();
            assertNE(cm, first, cm.call());
            assertEQ(cm, cm.call(), cm.call());
        }
    }

    /**
     * Tests this sequence:
     *
     * <pre>
     * 1. {@code res1 = c()}
     * 2. Compile c.
     * 3. Change stable array element read by c.
     * 4. {@code res2 = c()}
     * 5. {@code assert Objects.equals(res1, res2)}
     * </pre>
     *
     * That is, tests that compiling a method with an unsafe read of a stable array element folds
     * the element value into the compiled code.
     *
     * @param c a handle to one of the methods in {@link Test}
     * @param setDefaultAction a method that when invoked will change the value of the array element
     *            read by {@code c}
     */
    void testMatched(Callable<?> c, Runnable setDefaultAction) throws Exception {
        run(c, setDefaultAction, null);
        Setter.reset();
    }

    /**
     * Tests this sequence:
     *
     * <pre>
     * 1. {@code res1 = c()}
     * 2. Compile c.
     * 3. Change stable array element read by c.
     * 4. {@code res2 = c()}
     * 5. {@code assert !Objects.equals(res1, res2)}
     * </pre>
     *
     * That is, tests that compiling a method with an unsafe read of a stable array element does not
     * fold the element value into the compiled code.
     *
     * @param c a handle to one of the methods in {@link Test}
     * @param setDefaultAction a method that when invoked will change the value of the array element
     *            read by {@code c}
     */
    void testMismatched(Callable<?> c, Runnable setDefaultAction) throws Exception {
        run(c, null, setDefaultAction);
        Setter.reset();
    }

    void testUnsafeAccess() throws Exception {
        // boolean[], aligned accesses
        testMatched(Test::testZ_Z, Test::changeZ);
        testMatched(Test::testZ_B, Test::changeZ);
        testMatched(Test::testZ_S, Test::changeZ);
        testMatched(Test::testZ_C, Test::changeZ);
        testMatched(Test::testZ_I, Test::changeZ);
        testMatched(Test::testZ_J, Test::changeZ);
        testMatched(Test::testZ_F, Test::changeZ);
        testMatched(Test::testZ_D, Test::changeZ);

        // byte[], aligned accesses
        testMatched(Test::testB_Z, Test::changeB);
        testMatched(Test::testB_B, Test::changeB);
        testMatched(Test::testB_S, Test::changeB);
        testMatched(Test::testB_C, Test::changeB);
        testMatched(Test::testB_I, Test::changeB);
        testMatched(Test::testB_J, Test::changeB);
        testMatched(Test::testB_F, Test::changeB);
        testMatched(Test::testB_D, Test::changeB);

        // short[], aligned accesses
        testMatched(Test::testS_Z, Test::changeS);
        testMatched(Test::testS_B, Test::changeS);
        testMatched(Test::testS_S, Test::changeS);
        testMatched(Test::testS_C, Test::changeS);
        testMatched(Test::testS_I, Test::changeS);
        testMatched(Test::testS_J, Test::changeS);
        testMatched(Test::testS_F, Test::changeS);
        testMatched(Test::testS_D, Test::changeS);

        // char[], aligned accesses
        testMatched(Test::testC_Z, Test::changeC);
        testMatched(Test::testC_B, Test::changeC);
        testMatched(Test::testC_S, Test::changeC);
        testMatched(Test::testC_C, Test::changeC);
        testMatched(Test::testC_I, Test::changeC);
        testMatched(Test::testC_J, Test::changeC);
        testMatched(Test::testC_F, Test::changeC);
        testMatched(Test::testC_D, Test::changeC);

        // int[], aligned accesses
        testMatched(Test::testI_Z, Test::changeI);
        testMatched(Test::testI_B, Test::changeI);
        testMatched(Test::testI_S, Test::changeI);
        testMatched(Test::testI_C, Test::changeI);
        testMatched(Test::testI_I, Test::changeI);
        testMatched(Test::testI_J, Test::changeI);
        testMatched(Test::testI_F, Test::changeI);
        testMatched(Test::testI_D, Test::changeI);

        // long[], aligned accesses
        testMatched(Test::testJ_Z, Test::changeJ);
        testMatched(Test::testJ_B, Test::changeJ);
        testMatched(Test::testJ_S, Test::changeJ);
        testMatched(Test::testJ_C, Test::changeJ);
        testMatched(Test::testJ_I, Test::changeJ);
        testMatched(Test::testJ_J, Test::changeJ);
        testMatched(Test::testJ_F, Test::changeJ);
        testMatched(Test::testJ_D, Test::changeJ);

        // float[], aligned accesses
        testMatched(Test::testF_Z, Test::changeF);
        testMatched(Test::testF_B, Test::changeF);
        testMatched(Test::testF_S, Test::changeF);
        testMatched(Test::testF_C, Test::changeF);
        testMatched(Test::testF_I, Test::changeF);
        testMatched(Test::testF_J, Test::changeF);
        testMatched(Test::testF_F, Test::changeF);
        testMatched(Test::testF_D, Test::changeF);

        // double[], aligned accesses
        testMatched(Test::testD_Z, Test::changeD);
        testMatched(Test::testD_B, Test::changeD);
        testMatched(Test::testD_S, Test::changeD);
        testMatched(Test::testD_C, Test::changeD);
        testMatched(Test::testD_I, Test::changeD);
        testMatched(Test::testD_J, Test::changeD);
        testMatched(Test::testD_F, Test::changeD);
        testMatched(Test::testD_D, Test::changeD);

        // Object[], aligned accesses
        testMatched(Test::testL_L, Test::changeL);
        testMismatched(Test::testL_J, Test::changeL); // long & double are always as large as an OOP
        testMismatched(Test::testL_D, Test::changeL);

        // Unaligned accesses
        testMismatched(Test::testS_U, Test::changeS);
        testMismatched(Test::testC_U, Test::changeC);
        testMismatched(Test::testI_U, Test::changeI);
        testMismatched(Test::testJ_U, Test::changeJ);
    }

    @org.junit.Test
    public void main() throws Exception {
        testUnsafeAccess();
    }
}
