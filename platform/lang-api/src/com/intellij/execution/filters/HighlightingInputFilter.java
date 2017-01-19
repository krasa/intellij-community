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
import com.intellij.openapi.util.Pair;
import kotlin.ranges.IntRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface HighlightingInputFilter {
  /**
   * the contentType may be null in case of rehighlighting of the console on the background
   */
  @Nullable
  List<Pair<IntRange, ConsoleViewContentType>> applyFilter(@NotNull final String text, @Nullable final ConsoleViewContentType contentType);
}
