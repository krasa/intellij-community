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
package com.intellij.openapi.vcs;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public abstract class VcsCheckoutProcessor {

  private static final ExtensionPointName<VcsCheckoutProcessor> EXTENSION_POINT_NAME = new ExtensionPointName<VcsCheckoutProcessor>("com.intellij.vcs.checkoutProcessor");

  public static VcsCheckoutProcessor getProcessor(final @NotNull String protocol) {
    return ContainerUtil.find(EXTENSION_POINT_NAME.getExtensions(), new Condition<VcsCheckoutProcessor>() {
      @Override
      public boolean value(VcsCheckoutProcessor processor) {
        return protocol.equals(processor.getProtocol());
      }
    });
  }

  @NotNull
  public abstract String getProtocol();

  public abstract void checkout(@NotNull String url, @NotNull String directoryName, @NotNull VirtualFile parentDirectory);
}
