/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInspection.ex

import com.intellij.configurationStore.SchemeManagerFactoryBase
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.InMemoryFsRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.runInInitMode
import com.intellij.util.readText
import com.intellij.util.write
import org.assertj.core.api.Assertions.assertThat
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

internal class InspectionSchemeTest {
  companion object {
    @JvmField
    @ClassRule
    val projectRule = ProjectRule()
  }

  @JvmField
  @Rule
  val fsRule = InMemoryFsRule()

  @Test fun loadSchemes() {
    val schemeFile = fsRule.fs.getPath("inspection/Bar.xml")
    val schemeData = """
    <inspections profile_name="Bar" version="1.0">
      <option name="myName" value="Bar" />
      <inspection_tool class="Since15" enabled="true" level="ERROR" enabled_by_default="true" />
    "</inspections>""".trimIndent()
    schemeFile.write(schemeData)
    val schemeManagerFactory = SchemeManagerFactoryBase.TestSchemeManagerFactory(fsRule.fs.getPath(""))
    val profileManager = ApplicationInspectionProfileManager(InspectionToolRegistrar.getInstance(),
                                                                                            schemeManagerFactory,
                                                                                            ApplicationManager.getApplication().messageBus)
    profileManager.forceInitProfiles(true)
    profileManager.initProfiles()

    assertThat(profileManager.profiles).hasSize(1)
    val scheme = profileManager.profiles.first() as InspectionProfileImpl
    assertThat(scheme.name).isEqualTo("Bar")

    runInInitMode { scheme.initInspectionTools(null) }

    schemeManagerFactory.save()

    assertThat(schemeFile.readText()).isEqualTo(schemeData)
    profileManager.profiles
  }
}
