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

import java.util.ArrayList;
import java.util.List;

public class CompositeHighlightingInputFilter implements HighlightingInputFilter {
  private static final Logger LOG = Logger.getInstance(CompositeHighlightingInputFilter.class);

  private final List<Pair<HighlightingInputFilter, Boolean /* is dumb aware */>> myFilters = ContainerUtilRt.newArrayList();
  private final DumbService myDumbService;

  public CompositeHighlightingInputFilter(@NotNull Project project) {
    myDumbService = DumbService.getInstance(project);
  }

  @Override
  @Nullable
  public Result applyFilter(@NotNull final String text,
                            @Nullable final ConsoleViewContentType contentType) {
    boolean dumb = myDumbService.isDumb();
    Result mergedResult = null;
    List<ResultItem> items = null;
    
    for (Pair<HighlightingInputFilter, Boolean> pair : myFilters) {
      if (!dumb || pair.second == Boolean.TRUE) {
        long t0 = System.currentTimeMillis();
        HighlightingInputFilter filter = pair.first;
        Result result = filter.applyFilter(text, contentType);
        if (result != null) {
          if (mergedResult == null) {
            items = new ArrayList<>();
            mergedResult = new Result(items);
          }
          items.addAll(result.getResultItems());
        }
        t0 = System.currentTimeMillis() - t0;
        if (t0 > 100) {
          LOG.warn(filter + ".applyFilter() took " + t0 + " ms on '''" + text + "'''");
        }
      }
    }
    return mergedResult;
  }

  public void addFilter(@NotNull final HighlightingInputFilter filter) {
    myFilters.add(Pair.create(new MyHighlightingInputFilter(filter), DumbService.isDumbAware(filter)));
  }


  private static class MyHighlightingInputFilter implements HighlightingInputFilter {
    private final HighlightingInputFilter myFilter;
    boolean isBroken;

    public MyHighlightingInputFilter(HighlightingInputFilter filter) {
      myFilter = filter;
    }

    @Nullable
    @Override
    public Result applyFilter(@NotNull String text, @Nullable ConsoleViewContentType contentType) {
      if (!isBroken) {
        try {
          return myFilter.applyFilter(text, contentType);
        }
        catch (Throwable e) {
          isBroken = true;
          LOG.error(e);
        }
      }
      return null;
    }

    @Override
    public String toString() {
      return myFilter.getClass().getSimpleName();
    }
  }

  @Override
  public String toString() {
    return "CompositeHighlightingInputFilter{" +
           "myFilters=" + myFilters +
           '}';
  }
}
