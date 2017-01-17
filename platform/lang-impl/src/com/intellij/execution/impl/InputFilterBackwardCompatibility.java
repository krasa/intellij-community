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
package com.intellij.execution.impl;

import com.intellij.execution.filters.CompositeInputFilter;
import com.intellij.execution.filters.ConsoleInputFilterProvider;
import com.intellij.execution.filters.InputFilter;
import com.intellij.execution.filters.InputFilterEx;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;

import java.util.List;

class InputFilterBackwardCompatibility implements InputFilterEx {
  CompositeInputFilter myInputMessageFilter;

  public InputFilterBackwardCompatibility(Project project) {
    ConsoleInputFilterProvider[] inputFilters = Extensions.getExtensions(ConsoleInputFilterProvider.INPUT_FILTER_PROVIDERS);
    if (inputFilters.length > 0) {
      CompositeInputFilter compositeInputFilter = new CompositeInputFilter(project);
      myInputMessageFilter = compositeInputFilter;
      for (ConsoleInputFilterProvider eachProvider : inputFilters) {
        InputFilter[] filters = eachProvider.getDefaultFilters(project);
        for (InputFilter filter : filters) {
          compositeInputFilter.addFilter(filter);
        }
      }
    }
    else {
      myInputMessageFilter = null;
    }
  }

  @Override
  public String applyFilter(String text, ConsoleViewContentType contentType) {
    if (myInputMessageFilter == null) {
      return text;
    }
    List<Pair<String, ConsoleViewContentType>> pairs = myInputMessageFilter.applyFilter(text, contentType);
    if (pairs == null) {
      return text;
    }
    else if (pairs.isEmpty()) {
      return null;
    }
    else if (pairs.size() == 1) {
      return pairs.get(0).first;
    }
    else {
      StringBuilder sb = new StringBuilder();
      for (Pair<String, ConsoleViewContentType> pair : pairs) {
        sb.append(pair.first);
      }
      return sb.toString();
    }
  }
}
