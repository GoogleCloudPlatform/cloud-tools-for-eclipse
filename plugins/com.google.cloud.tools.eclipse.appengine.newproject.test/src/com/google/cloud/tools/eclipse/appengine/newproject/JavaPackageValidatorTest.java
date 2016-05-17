package com.google.cloud.tools.eclipse.appengine.newproject;

import org.junit.Assert;
import org.junit.Test;

public class JavaPackageValidatorTest {

  @Test
  public void testUsualPackage() {
    Assert.assertTrue(JavaPackageValidator.validate("com.google.eclipse"));
  }
  
  @Test
  public void testEndsWithPeriod() {
    Assert.assertFalse(JavaPackageValidator.validate("com.google.eclipse."));
  }
  
  @Test
  public void testStartsWithPeriod() {
    Assert.assertFalse(JavaPackageValidator.validate(".com.google.eclipse."));
  }

  @Test
  public void testOneWord() {
    boolean validate = JavaPackageValidator.validate("word");
    Assert.assertTrue(validate);
  }
  
  @Test
  public void testContainsSpaceAroundPeriod() {
    Assert.assertFalse(JavaPackageValidator.validate("com. google.eclipse"));
  }
  
  @Test
  public void testContainsInternalSpace() {
    Assert.assertFalse(JavaPackageValidator.validate("com.goo gle.eclipse"));
  }
  
  @Test
  public void testDoublePeriod() {
    Assert.assertFalse(JavaPackageValidator.validate("com..google"));
  }


  @Test
  public void testJavaKeyword() {
    Assert.assertFalse(JavaPackageValidator.validate("com.new"));
  }
  
  @Test
  public void testEmptyString() {
    Assert.assertTrue(JavaPackageValidator.validate(""));
  }
  
  @Test
  public void testNull() {
    Assert.assertFalse(JavaPackageValidator.validate(null));
  }
  
}
