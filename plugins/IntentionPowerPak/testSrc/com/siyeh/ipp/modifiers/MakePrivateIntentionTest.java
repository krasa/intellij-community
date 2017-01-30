/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.siyeh.ipp.modifiers;

import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPTestCase;

/**
 * @author Bas Leijdekkers
 */
public class MakePrivateIntentionTest extends IPPTestCase {

  public void testMyEnum() { assertIntentionNotAvailable(); }
  public void testMyClass() { assertIntentionNotAvailable(); }
  public void testMyInterface() { assertIntentionNotAvailable(); }
  public void testEnumConstructor() { assertIntentionNotAvailable(); }
  public void testLocalClass() { assertIntentionNotAvailable(IntentionPowerPackBundle.message("make.public.intention.name")); }
  public void testMethod() { doTest(); }
  public void testAnnotatedMember() { doTest(); }

  @Override
  protected String getRelativePath() {
    return "modifiers/make_public";
  }

  @Override
  protected String getIntentionName() {
    return IntentionPowerPackBundle.message("make.private.intention.name");
  }
}
