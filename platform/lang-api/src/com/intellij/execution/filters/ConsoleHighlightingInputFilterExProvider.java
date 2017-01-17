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

import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

public abstract class ConsoleHighlightingInputFilterExProvider {
  public static final ExtensionPointName<ConsoleHighlightingInputFilterExProvider> FILTER_PROVIDERS =
    ExtensionPointName.create("com.intellij.consoleHighlightingInputFilterExProvider");

  @NotNull
  public abstract HighlightingInputFilterEx[] getHighlightingFilters(@NotNull ConsoleView consoleView,
                                                                     @NotNull Project project,
                                                                     @NotNull GlobalSearchScope scope);
}
