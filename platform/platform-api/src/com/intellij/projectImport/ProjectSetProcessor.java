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
package com.intellij.projectImport;

import com.intellij.openapi.extensions.ExtensionPointName;

/**
 * @author Dmitry Avdeev
 */
public abstract class ProjectSetProcessor<TState> {

  public static final ExtensionPointName<ProjectSetProcessor> EXTENSION_POINT_NAME = ExtensionPointName.create("com.intellij.projectSetProcessor");

  public abstract String getId();

  /**
   * @return null if user canceled the operation.
   */
  public abstract TState interactWithUser();

  public abstract void processEntry(String key, String value, TState state);
}
