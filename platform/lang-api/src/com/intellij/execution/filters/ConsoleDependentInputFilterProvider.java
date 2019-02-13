// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.filters;

import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

public abstract class ConsoleDependentInputFilterProvider implements ConsoleInputFilterProvider {
  private static final InputFilter[] EMPTY_ARRAY = new InputFilter[0];

  @NotNull
  public abstract InputFilter[] getDefaultFilters(@NotNull ConsoleView consoleView,
                                                  @NotNull Project project,
                                                  @NotNull GlobalSearchScope scope);

  @NotNull
  @Override
  public final InputFilter[] getDefaultFilters(@NotNull Project project) {
    return EMPTY_ARRAY;
  }
}