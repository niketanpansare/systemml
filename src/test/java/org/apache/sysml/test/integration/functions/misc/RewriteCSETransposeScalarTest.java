/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.sysml.test.integration.functions.misc;

import org.junit.Test;
import org.apache.sysml.hops.OptimizerUtils;
import org.apache.sysml.test.integration.AutomatedTestBase;
import org.apache.sysml.test.integration.TestConfiguration;
import org.apache.sysml.test.utils.TestUtils;
import org.apache.sysml.utils.Statistics;

/**
 * 
 * 
 */
public class RewriteCSETransposeScalarTest extends AutomatedTestBase 
{	
	private static final String TEST_NAME1 = "RewriteCSETransposeScalarPow"; //right scalar
	private static final String TEST_NAME2 = "RewriteCSETransposeScalarMult"; //left scalar
	
	private static final String TEST_DIR = "functions/misc/";
	private static final String TEST_CLASS_DIR = TEST_DIR + RewriteCSETransposeScalarTest.class.getSimpleName() + "/";
	
	private static final int rows = 1932;
	private static final int cols = 14;
	
	@Override
	public void setUp() {
		TestUtils.clearAssertionInformation();
		addTestConfiguration( TEST_NAME1, new TestConfiguration(TEST_CLASS_DIR, TEST_NAME1, new String[] { "R" }) );
		addTestConfiguration( TEST_NAME2, new TestConfiguration(TEST_CLASS_DIR, TEST_NAME2, new String[] { "R" }) );
	}

	@Test
	public void testRewriteCSETransposePow()  {
		testRewriteCSETransposeScalar( TEST_NAME1, true );
	}
	
	@Test
	public void testRewriteCSETransposePowNoRewrite()  {
		testRewriteCSETransposeScalar( TEST_NAME1, false );
	}
	
	@Test
	public void testRewriteCSETransposeMult()  {
		testRewriteCSETransposeScalar( TEST_NAME2, true );
	}
	
	@Test
	public void testRewriteCSETransposeMultNoRewrite()  {
		testRewriteCSETransposeScalar( TEST_NAME2, false );
	}
	
	/**
	 * 
	 * @param testname
	 * @param et
	 */
	private void testRewriteCSETransposeScalar( String testname, boolean rewrites )
	{	
		if(shouldSkipTest())
			return;
		
		boolean rewritesOld = OptimizerUtils.ALLOW_ALGEBRAIC_SIMPLIFICATION;
		OptimizerUtils.ALLOW_ALGEBRAIC_SIMPLIFICATION = rewrites;
		
		try {
			TestConfiguration config = getTestConfiguration(testname);
			loadTestConfiguration(config);
			
			String HOME = SCRIPT_DIR + TEST_DIR;
			fullDMLScriptName = HOME + testname + ".dml";
			programArgs = new String[]{ "-stats", "-args", String.valueOf(rows), 
					String.valueOf(cols), output("R") };
			OptimizerUtils.ALLOW_ALGEBRAIC_SIMPLIFICATION = rewrites;

			//run performance tests
			runTest(true, false, null, -1); 
			
			//compare output  
			double ret = TestUtils.readDMLScalar(output("R"));
			assertEquals("Wrong result, expected: "+(rows*cols), new Double(rows*cols), new Double(ret));
			assertEquals(new Long(rewrites?1:2), new Long(Statistics.getCPHeavyHitterCount("r'")));
		}
		finally {
			OptimizerUtils.ALLOW_ALGEBRAIC_SIMPLIFICATION = rewritesOld;
		}
	}	
}
