/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig.fixes.equality;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.bugs.ObjectEqualityInspection;

/**
 * @author Bas Leijdekkers
 */
public abstract class EqualityOperatorComparesObjectsInspectionTestBase extends IGQuickFixesTestCase {

  public void testEnumComparison() { assertQuickfixNotAvailable(); }
  public void testNullComparison() { assertQuickfixNotAvailable(); }
  public void testPrimitiveComparison() { assertQuickfixNotAvailable(); }
  public void testSimpleObjectComparison() { doTest(InspectionGadgetsBundle.message("equality.to.equals.quickfix")); }
  public void testNegatedObjectComparison() { doTest(InspectionGadgetsBundle.message("inequality.to.not.equals.quickfix")); }
  public void testCompareThisInEqualsMethod() { assertQuickfixNotAvailable(); }
  public void testCompareSameQualifiedThisInEqualsMethod() { assertQuickfixNotAvailable(); }
  public void testCompareOtherQualifiedThisInEqualsMethod() { doTest(InspectionGadgetsBundle.message("equality.to.equals.quickfix")); }
  public void testCompareFieldInEqualsMethod() { doTest(InspectionGadgetsBundle.message("equality.to.equals.quickfix")); }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new ObjectEqualityInspection());
    myDefaultHint = "Replace";
    myRelativePath = "equality/replace_equality_with_equals";
  }
}
