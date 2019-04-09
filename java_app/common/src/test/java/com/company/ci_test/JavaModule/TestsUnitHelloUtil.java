package com.company.ci_test.JavaModule;

import static org.junit.Assert.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;

import org.junit.*;
import com.company.ci_test.JavaModule.HelloUtil;

/**
 * Unit tests for HelloUtil.
 */
public class TestsUnitHelloUtil {

  /**
   * @throws java.lang.Exception
   */
  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
  }

  /**
   * @throws java.lang.Exception
   */
  @AfterClass
  public static void tearDownAfterClass() throws Exception {
  }

  /**
   * @throws java.lang.Exception
   */
  @Before
  public void setUp() throws Exception {
  }

  /**
   * @throws java.lang.Exception
   */
  @After
  public void tearDown() throws Exception {
  }

  @Test
	public void testGetHelloMessage() {
	  assertEquals("Hello World !!", HelloUtil.getHelloMessage());
	}
}
