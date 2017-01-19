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

package com.intellij.execution.filters;

import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class CompositeTextInputFilter implements TextInputFilter {
  private static final Logger LOG = Logger.getInstance(CompositeTextInputFilter.class);

  private final List<Pair<TextInputFilter, Boolean /* is dumb aware */>> myFilters = ContainerUtilRt.newArrayList();
  private final DumbService myDumbService;

  public CompositeTextInputFilter(@NotNull Project project) {
    myDumbService = DumbService.getInstance(project);
  }

  @Override
  @Nullable
  public String applyFilter(@NotNull final String text, @NotNull final ConsoleViewContentType contentType) {
    boolean dumb = myDumbService.isDumb();
    String filteredText = text;
    for (int i = 0; i < myFilters.size(); i++) {
      Pair<TextInputFilter, Boolean> pair = myFilters.get(i);
      if (!dumb || pair.second == Boolean.TRUE) {
        long t0 = System.currentTimeMillis();
        TextInputFilter filter = pair.first;
        filteredText = filter.applyFilter(filteredText, contentType);
        t0 = System.currentTimeMillis() - t0;
        if (t0 > 100) {
          LOG.warn(filter + ".applyFilter() took " + t0 + " ms on '''" + text + "'''");
        }
        if (filteredText == null) {
          return null;
        }
      }
    }
    return filteredText;
  }

  public void addFilter(@NotNull final TextInputFilter filter) {
    myFilters.add(Pair.create(new MyTextInputFilterWrapper(filter), DumbService.isDumbAware(filter)));
  }

  private static class MyTextInputFilterWrapper implements TextInputFilter {
    private final TextInputFilter myFilter;
    boolean isBroken;

    public MyTextInputFilterWrapper(TextInputFilter filter) {
      myFilter = filter;
    }

    @Nullable
    @Override
    public String applyFilter(@NotNull String text, @NotNull ConsoleViewContentType contentType) {
      if (!isBroken) {
        try {
          return myFilter.applyFilter(text, contentType);
        }
        catch (Throwable e) {
          isBroken = true;
          LOG.error(e);
        }
      }
      return text;
    }


    @Override
    public String toString() {
      return myFilter.getClass().getSimpleName();
    }
  }
}
