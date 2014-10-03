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
package com.intellij.platform;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsCheckoutProcessor;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectSetProcessor;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class ProjectSetTest extends LightPlatformTestCase {

  @SuppressWarnings("JUnitTestCaseWithNonTrivialConstructors")
  public ProjectSetTest() {
    PlatformTestCase.initPlatformLangPrefix();
  }

  private static String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath() + "/platform/platform-tests/testData/projectSet/";
  }

  public void testProjectSetReader() throws IOException {
    ProjectSetReader reader = new ProjectSetReader();

    final Ref<List<Pair<String, String>>> ref = Ref.create();
    PlatformTestUtil.registerExtension(ProjectSetProcessor.EXTENSION_POINT_NAME, new ProjectSetProcessor() {
      @Override
      public String getId() {
        return "test";
      }

      @Override
      public void processEntries(@NotNull List<Pair<String, String>> entries, @NotNull Context context, @NotNull Runnable runNext) {
        ref.set(entries);
      }
    }, myTestRootDisposable);

    @Language("JSON") String descriptor = FileUtil.loadFile(new File(getTestDataPath() + "descriptor.json"));
    reader.readDescriptor(descriptor, getSourceRoot());

    List<Pair<String, String>> entries = ref.get();
    assertEquals(2, entries.size());
    assertEquals("git://foo.bar", entries.get(0).getSecond());
    assertEquals("{\"foo\":\"bar\"}", entries.get(1).getSecond());
  }

  public void testVcsCheckoutProcessor() throws IOException {

    PlatformTestUtil.registerExtension(VcsCheckoutProcessor.EXTENSION_POINT_NAME, new VcsCheckoutProcessor() {
      @NotNull
      @Override
      public String getId() {
        return "schema";
      }

      @Override
      public boolean checkout(@NotNull String url,
                              @NotNull VirtualFile parentDirectory, @NotNull String directoryName) {
        assertEquals("schema://foo.bar/path", url);
        assertEquals("path", directoryName);
        return true;
      }
    }, myTestRootDisposable);

    @Language("JSON") String descriptor = FileUtil.loadFile(new File(getTestDataPath() + "vcs.json"));
    new ProjectSetReader().readDescriptor(descriptor, getSourceRoot());
  }

  public void testOpenProject() throws IOException {
    @Language("JSON") String descriptor = FileUtil.loadFile(new File(getTestDataPath() + "project.json"));
    VirtualFile directory = VfsUtil.findFileByIoFile(new File(getTestDataPath()), true);
    new ProjectSetReader().readDescriptor(descriptor, directory);
    Project[] projects = ProjectManager.getInstance().getOpenProjects();
    Project project = ContainerUtil.find(projects, new Condition<Project>() {
      @Override
      public boolean value(Project project) {
        return "untitled".equals(project.getName());
      }
    });
    assertNotNull(project);
    ((ProjectManagerEx)ProjectManager.getInstance()).closeAndDispose(project);
  }
}
